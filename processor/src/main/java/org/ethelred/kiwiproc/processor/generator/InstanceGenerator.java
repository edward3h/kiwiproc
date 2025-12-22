/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.generator;

import static org.ethelred.kiwiproc.processor.ResultPart.KEY;
import static org.ethelred.kiwiproc.processor.ResultPart.VALUE;
import static org.ethelred.kiwiproc.processor.generator.RuntimeTypes.*;

import com.karuslabs.utilitary.Logger;
import com.palantir.javapoet.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import org.ethelred.kiwiproc.processor.*;
import org.ethelred.kiwiproc.processor.types.*;
import org.jspecify.annotations.Nullable;

public class InstanceGenerator {

    private final Logger logger;
    private final KiwiTypeConverter kiwiTypeConverter;
    private final CoreTypes coreTypes;
    private final Set<String> parameterNames = new HashSet<>();
    private final Map<String, String> patchedNames = new HashMap<>();
    private int patchedNameCount = 0;

    public InstanceGenerator(Logger logger, KiwiTypeConverter kiwiTypeConverter, CoreTypes coreTypes) {
        this.logger = logger;
        this.kiwiTypeConverter = kiwiTypeConverter;
        this.coreTypes = coreTypes;
    }

    public JavaFile generate(DAOClassInfo classInfo) {
        var daoName = ClassName.get(classInfo.packageName(), classInfo.daoName());
        var superClass = ParameterizedTypeName.get(ABSTRACT_DAO, daoName);
        var constructorSpec = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(DAO_CONTEXT, "daoContext").build())
                .addStatement("super(daoContext)")
                .build();
        var typeSpecBuilder = TypeSpec.classBuilder(ClassName.get(classInfo.packageName(), classInfo.className("Impl")))
                .addOriginatingElement(classInfo.element())
                .addAnnotation(AnnotationSpec.builder(ClassName.bestGuess("javax.annotation.processing.Generated"))
                        .addMember("value", "$S", "org.ethelred.kiwiproc.processor.KiwiProcessor")
                        .build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superClass)
                .addSuperinterface(daoName)
                .addMethod(constructorSpec);
        for (var methodThing : classInfo.methods()) {
            typeSpecBuilder.addMethod(buildMethod(methodThing));
        }
        return JavaFile.builder(classInfo.packageName(), typeSpecBuilder.build())
                .build();
    }

    private MethodSpec buildMethod(DAOMethodInfo methodInfo) {
        parameterNames.clear();
        patchedNames.clear();
        patchedNameCount = 0;
        var methodSpecBuilder = MethodSpec.overriding(methodInfo.methodElement());
        methodSpecBuilder.addStatement("var connection = context.getConnection()");
        methodSpecBuilder.beginControlFlow(
                "try (var statement = connection.prepareStatement($S))",
                methodInfo.parsedSql().parsedSql());
        methodSpecBuilder.addCode(
                switch (methodInfo.kind()) {
                    case QUERY -> methodBodyForQuery(methodInfo);
                    case UPDATE -> methodBodyForUpdate(methodInfo);
                    case BATCH -> methodBodyForBatch(methodInfo);
                    case DEFAULT -> throw new IllegalArgumentException();
                });
        methodSpecBuilder
                .nextControlFlow("catch ($T e)", SQLException.class) // end try
                .addStatement("throw new $T(e)", UNCHECKED_SQL_EXCEPTION)
                .endControlFlow(); // end catch

        return methodSpecBuilder.build();
    }

    private CodeBlock methodBodyForUpdate(DAOMethodInfo methodInfo) {
        var builder = builderWithParameters(methodInfo.parameterMapping());
        builder.addStatement("var rawResult = statement.executeUpdate()");
        KiwiType returnType = methodInfo.signature().returnType();
        if (!(returnType instanceof VoidType)) {
            var conversion = lookupConversion(
                    methodInfo::methodElement, new TypeMapping(CoreTypes.UPDATE_RETURN_TYPE, returnType));
            buildConversion(builder, methodInfo, conversion, returnType, "result", "rawResult", true);
            builder.addStatement("return result");
        }
        return builder.build();
    }

    private CodeBlock methodBodyForBatch(DAOMethodInfo methodInfo) {
        /*
           boolean anyValues = false;
           var someIterator = some.iterator();
           var otherIterator = Arrays.asList(other).iterator();
           while (someIterator.hasNext() && otherIterator.hasNext()) {
               var someIteratorValue = someIterator.next();
               var otherIteratorValue = otherIterator.next();
               // set parameters
               statement.addBatch();
               anyValues = true;
           }
           if (someIterator.hasNext() || otherIterator.hasNext()) {
               throw new UncheckedSqlException("Iterators in method x have different lengths");
           }
           if (anyValues) {
               int[] rawResult = statement.executeBatch();
               var result = doConversion(rawResult);
               return result;
           }
           return someDefaultResult;
        */
        var builder = CodeBlock.builder();
        builder.addStatement("var anyValues = false");
        var iterators = methodInfo.batchIterators();
        record IteratorVariableNames(DAOBatchIterator iterator, String iteratorName, String iteratorValueName) {}
        var iteratorsAndNames = iterators.stream()
                .collect(Collectors.toMap(
                        DAOBatchIterator::source,
                        i -> new IteratorVariableNames(
                                i,
                                patchName(i.source().methodParameterName() + "Iterator"),
                                patchName(i.source().methodParameterName() + "IteratorValue"))));
        for (var iteratorAndNames : iteratorsAndNames.values()) {
            builder.addNamed(
                    "var $iteratorVariable:L = "
                            + iteratorAndNames.iterator.validCollection().toIteratorTemplate(),
                    Map.of(
                            "iteratorVariable",
                            iteratorAndNames.iteratorName,
                            "containerVariable",
                            iteratorAndNames.iterator.source().methodParameterName()))
                    .addStatement("");
        }
        var hasNextClause = iteratorsAndNames.values().stream()
                .map(IteratorVariableNames::iteratorName)
                .map("%s.hasNext()"::formatted)
                .collect(Collectors.joining(" && "));
        builder.beginControlFlow("while ($L)", hasNextClause);
        for (var iteratorAndNames : iteratorsAndNames.values()) {
            builder.addStatement(
                    "var $L = $L.next()", iteratorAndNames.iteratorValueName, iteratorAndNames.iteratorName);
        }
        var parameterMapping = methodInfo.parameterMapping();
        addParameters(builder, parameterMapping, methodParameterInfo -> {
            if (iteratorsAndNames.containsKey(methodParameterInfo)) {
                return iteratorsAndNames.get(methodParameterInfo).iteratorValueName;
            }
            return methodParameterInfo.methodParameterName();
        });
        builder.addStatement("statement.addBatch()");
        builder.addStatement("anyValues = true");
        builder.endControlFlow(); // while hasNext...
        builder.addStatement("var rawResult = anyValues ? statement.executeBatch() : new int[0]");
        KiwiType returnType = methodInfo.signature().returnType();
        if (!(returnType instanceof VoidType)) {
            var conversion = lookupConversion(
                    methodInfo::methodElement, new TypeMapping(CoreTypes.BATCH_RETURN_TYPE, returnType));
            buildConversion(builder, methodInfo, conversion, returnType, "result", "rawResult", true);
            builder.addStatement("return result");
        }
        return builder.build();
    }

    private CodeBlock methodBodyForQuery(DAOMethodInfo methodInfo) {
        var builder = builderWithParameters(methodInfo.parameterMapping());
        var containerBuilder = containerBuilderFor(methodInfo);
        builder.addStatement("var rs = statement.executeQuery()");
        builder.addStatement(containerBuilder.declaration());
        builder.beginControlFlow("$L (rs.next())", methodInfo.expectedRows() == RowCount.MANY ? "while" : "if");
        var columns = methodInfo.columns();
        if (!columns.isEmpty()) {
            for (DAOResultColumn column : columns) {
                String rawName = patchName(columnName(column) + "Raw");
                String accessorSuffix = column.sqlTypeMapping().accessorSuffix();
                TypeName typeName =
                        kiwiTypeConverter.fromKiwiType(column.sqlTypeMapping().kiwiType());
                if ("Object".equals(accessorSuffix)) { // hacky
                    builder.addStatement("$1T $2L = rs.getObject($3S, $1T.class)", typeName, rawName, column.name());
                } else {
                    builder.addStatement("$T $L = rs.get$L($S)", typeName, rawName, accessorSuffix, column.name());
                }
                if (column.sqlTypeMapping().isNullable()) {
                    builder.beginControlFlow("if (rs.wasNull())")
                            .addStatement("$L = null", rawName)
                            .endControlFlow();
                }
                var varName = patchName(columnName(column));
                buildConversion(
                        builder, methodInfo, column.conversion(), column.asTypeMapping().target(), varName, rawName, true);
            }
            for (ResultPart resultPart : ResultPart.values()) {
                var componentClass = containerBuilder.componentTypeFor(resultPart);
                if (componentClass != null) {
                    var resultVar = patchName(prefixName(resultPart, "Value"));
                    var params = columns.stream()
                            .filter(column -> resultPart.equals(column.resultPart()))
                            .map(p -> CodeBlock.of("$L", patchedNames.get(columnName(p))))
                            .collect(CodeBlock.joining(",\n"));
                    params = CodeBlock.builder().indent().add(params).unindent().build();
                    builder.add(
                            """
                                    var $L = new $T(
                                    $L
                                    );
                                    """,
                            resultVar,
                            kiwiTypeConverter.fromKiwiType(componentClass),
                            params);
                }
            }
            //            builder.addStatement(containerBuilder.add());
            builder.add(containerBuilder.add());
        } else {
            throw new IllegalStateException("Expected columns");
        }
        if (methodInfo.expectedRows() == RowCount.EXACTLY_ONE) {
            // TODO test for exactly one row case
        }
        builder.endControlFlow(); // end while or if
        builder.addStatement(containerBuilder.returnValue());
        //        if (methodInfo.signature().returnType() instanceof CollectionType collectionType) {
        //            builder.add("return ")
        //                    .addNamed(
        //                            collectionType.type().fromListTemplate(),
        //                            Map.of("componentClass", componentClass, "containerVariable", containerVariable))
        //                    .addStatement("");
        //        } else {
        //            builder.addStatement("return $1L.isEmpty() ? null : $1L.get(0)", containerVariable);
        //        }
        return builder.build();
    }

    private String columnName(DAOResultColumn column) {
        return prefixName(column.resultPart(), column.name().name());
    }

    private String prefixName(ResultPart part, String name) {
        var prefix = part.prefix();
        var cName = Util.toTitleCase(name);
        if (cName.isBlank()) {
            throw new IllegalArgumentException("Column name can't be blank");
        }
        if (prefix.isBlank()) {
            return cName.substring(0, 1).toLowerCase() + cName.substring(1);
        }
        return prefix + cName;
    }

    private CodeBlock.Builder builderWithParameters(List<DAOParameterInfo> parameterMapping) {
        var builder = CodeBlock.builder();
        addParameters(builder, parameterMapping, MethodParameterInfo::methodParameterName);
        return builder;
    }

    private void addParameters(
            CodeBlock.Builder builder,
            List<DAOParameterInfo> parameterMapping,
            Function<MethodParameterInfo, String> sourceLookup) {
        parameterMapping.forEach(parameterInfo -> {
            var name = "param" + parameterInfo.index();
            String accessor = sourceLookup.apply(parameterInfo.source()) + parameterInfo.javaAccessorSuffix();
            buildConversion(
                    builder, parameterInfo, parameterInfo.conversion(), parameterInfo.mapper().target(), name, accessor, true);
            var nullableSource = parameterInfo.mapper().source().isNullable();
            if (nullableSource) {
                builder.beginControlFlow("if ($L == null)", name)
                        .addStatement("statement.setNull($L, $L)", parameterInfo.index(), parameterInfo.sqlType())
                        .nextControlFlow("else");
            }
            builder.addStatement("statement.$L($L, $L)", parameterInfo.setter(), parameterInfo.index(), name);
            if (nullableSource) {
                builder.endControlFlow();
            }
            parameterNames.add(accessor);
        });
    }

    private void buildConversion(
            CodeBlock.Builder builder,
            Supplier<Element> methodInfo,
            Conversion conversion,
            KiwiType targetType,
            String assignee,
            String accessor,
            boolean withVar) {
        try {
            var insertVar =
                    withVar ? CodeBlock.of("$T ", kiwiTypeConverter.fromKiwiType(targetType, true)) : CodeBlock.of("");
            if (conversion instanceof AssignmentConversion) {
                /* e.g.
                var param1 = id;
                 */
                builder.addStatement("$L$L = $L", insertVar, assignee, accessor);
            } else if (conversion instanceof StringFormatConversion sfc) {
                /* e.g.
                var param1 = (int) id;
                 */
                builder.add("$L$L =", insertVar, assignee)
                        .addStatement(sfc.conversionFormat(), sfc.withAccessor(accessor));
            } else if (conversion instanceof ToSqlArrayConversion sac) {
                /* e.g.
                Object[] elementObjects = listParam.stream()
                    .map(x -> (int) x)
                    .toArray();
                var param1 = connection.createArrayOf("int4", elementObjects);
                 */
                Conversion elementConversion = sac.elementConversion();
                String elementObjects = patchName("elementObjects");
                String lambdaValue = patchName("value");
                builder.add("Object[] $L = ", elementObjects)
                        .addNamed(sac.ct().type().toStreamTemplate(), Map.of("containerVariable", accessor))
                        .indent()
                        .add("\n.map($L -> {\n", lambdaValue)
                        .indent();
                buildConversion(builder, methodInfo, elementConversion, sac.sat().containedType(), "tmp", lambdaValue, true);
                builder.addStatement("return tmp")
                        .unindent()
                        .add("})\n.toArray();\n")
                        .unindent();
                builder.addStatement(
                        "$L$L = connection.createArrayOf($S, $L)",
                        insertVar,
                        assignee,
                        sac.sat().componentDbType(),
                        elementObjects);
            } else if (conversion instanceof FromSqlArrayConversion sac) {
                /* e.g.
                ResultSet arrayRS = rawValue.getResultSet();
                List<String> arrayList = new ArrayList<>();
                while (arrayRs.next()) {
                    var rawItemValue = arrayRs.getString(2);
                    var itemValue = rawItemValue;
                    arrayList.add(itemValue);
                 }
                 var value = List.copyOf(arrayList);
                 */
                var arrayRS = patchName("arrayRS");
                var arrayList = patchName("arrayList");
                var rawItemValue = patchName("rawItemValue");
                var itemValue = patchName("itemValue");
                TypeName componentClass =
                        kiwiTypeConverter.fromKiwiType(sac.ct().containedType());
                builder.addStatement("$T $L = $L.getResultSet()", ResultSet.class, arrayRS, accessor)
                        .addStatement("List<$T> $L = new $T<>()", componentClass, arrayList, ArrayList.class)
                        .beginControlFlow("while ($L.next())", arrayRS)
                        // Array.getResultSet() returns 2 columns: 1 is the index, 2 is the value
                        .addStatement(
                                "var $L = $L.get$L(2)",
                                rawItemValue,
                                arrayRS,
                                sac.sat().componentType().accessorSuffix());
                buildConversion(
                        builder, methodInfo, sac.elementConversion(), sac.ct().containedType(), itemValue, rawItemValue, true);
                builder.addStatement("$L.add($L)", arrayList, itemValue)
                        .endControlFlow()
                        .add("$L$L = ", insertVar, assignee)
                        .addNamed(
                                sac.ct().type().fromListTemplate(),
                                Map.of("componentClass", componentClass, "listVariable", arrayList))
                        .addStatement("");
            } else if (conversion instanceof CollectionConversion cc) {
                /*
                var sourceIterator = Arrays.asList(source).iterator();
                List<Integer> arrayList = new ArrayList<>();
                while (sourceIterator.hasNext()) {
                    var rawItemValue = sourceIterator.next();
                    var itemValue = rawItemValue;
                    arrayList.add(itemValue);
                }
                var value = List.copyOf(arrayList);
                 */

                var sourceIterator = patchName("sourceIterator");
                var arrayList = patchName("arrayList");
                var rawItemValue = patchName("rawItemValue");
                var itemValue = patchName("itemValue");
                TypeName componentClass =
                        kiwiTypeConverter.fromKiwiType(cc.sourceType().containedType(), true);
                builder.addNamed("var $iteratorName:L = " + cc.sourceType().type().toIteratorTemplate(), Map.of("iteratorName", sourceIterator, "containerVariable", accessor))
                        .addStatement("")
                        .addStatement("List<$T> $L = new $T<>()", componentClass, arrayList, ArrayList.class)
                        .beginControlFlow("while ($L.hasNext())", sourceIterator)
                        .addStatement(
                                "var $L = $L.next()",
                                rawItemValue,
                                sourceIterator);
                buildConversion(
                        builder, methodInfo, cc.containedTypeConversion(), cc.targetType().containedType(), itemValue, rawItemValue, true);
                builder.addStatement("$L.add($L)", arrayList, itemValue)
                        .endControlFlow()
                        .add("$L$L = ", insertVar, assignee)
                        .addNamed(
                                cc.targetType().type().fromListTemplate(),
                                Map.of("componentClass", componentClass, "listVariable", arrayList))
                        .addStatement("");
            } else if (conversion instanceof NullableSourceConversion nsc) {
                builder.addStatement("$T $L = null", kiwiTypeConverter.fromKiwiType(targetType), assignee)
                        .beginControlFlow("if ($L != null)", accessor);
                buildConversion(builder, methodInfo, nsc.conversion(), targetType, assignee, accessor, false);
                builder.endControlFlow();
            } else {
                logger.error(methodInfo.get(), "Unsupported Conversion %s".formatted(conversion));
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Error in conversion " + conversion, e);
        }
    }

    private String patchName(String name) {
        return patchedNames.computeIfAbsent(name, k -> {
            var newName = k;
            while (parameterNames.contains(newName)) {
                newName = k + (++patchedNameCount);
            }
            return newName;
        });
    }

    private ContainerBuilder containerBuilderFor(DAOMethodInfo methodInfo) {
        var returnType = methodInfo.signature().returnType();
        if (returnType instanceof MapType mapType) {
            return new MapContainerBuilder(mapType, methodInfo);
        }
        if (returnType instanceof CollectionType collectionType) {
            return new CollectionContainerBuilder(collectionType, methodInfo);
        }
        return new SingleRowContainerBuilder(returnType, methodInfo);
    }

    interface ContainerBuilder {

        CodeBlock declaration();

        @Nullable KiwiType componentTypeFor(ResultPart resultPart);

        CodeBlock add();

        CodeBlock returnValue();
    }

    Conversion lookupConversion(ElementSupplier elementSupplier, TypeMapping t) {
        var element = elementSupplier.getElement();
        var conversion = coreTypes.lookup(t);
        if (!conversion.isValid()) {
            logger.error(element, "No valid conversion for %s".formatted(t));
        }
        if (conversion.hasWarning()) {
            logger.warn(element, conversion.warning());
        }
        return conversion;
    }

    private class BaseContainerBuilder<T extends KiwiType> {
        protected final DAOMethodInfo methodInfo;
        protected final T returnType;

        private BaseContainerBuilder(T returnType, DAOMethodInfo methodInfo) {
            this.methodInfo = methodInfo;
            this.returnType = returnType;
        }

        protected DAOResultColumn firstColumnOfPart(ResultPart part) {
            return methodInfo.columns().stream()
                    .filter(c -> c.resultPart() == part)
                    .findFirst()
                    .orElseThrow(IllegalArgumentException::new);
        }
    }

    private class SingleRowContainerBuilder extends BaseContainerBuilder<KiwiType> implements ContainerBuilder {

        public SingleRowContainerBuilder(KiwiType returnType, DAOMethodInfo methodInfo) {
            super(returnType, methodInfo);
        }

        @Override
        public CodeBlock declaration() {
            // no container needed
            return CodeBlock.of("");
        }

        @Override
        public @Nullable KiwiType componentTypeFor(ResultPart resultPart) {
            if (resultPart == ResultPart.SIMPLE
                    && !returnType.valueComponentType().isSimple()) {
                return returnType.valueComponentType();
            }
            return null;
        }

        @Override
        public CodeBlock add() {
            var valueVariable = returnType.valueComponentType().isSimple()
                    ? columnName(firstColumnOfPart(ResultPart.SIMPLE))
                    : prefixName(ResultPart.SIMPLE, "Value");
            var patchedValueVariable = patchedNames.get(valueVariable);
            if (returnType instanceof OptionalType optionalType) {
                if (!returnType.valueComponentType().isNullable()) {
                    return CodeBlock.of("return $T.of($L);", optionalType.optionalClass(), patchedValueVariable);
                }
                return CodeBlock.of(
                        "return $1L == null ? $2T.empty() : $2T.of($1L);",
                        patchedValueVariable,
                        optionalType.optionalClass());
            }
            return CodeBlock.of("return $L;", patchedValueVariable);
        }

        @Override
        public CodeBlock returnValue() {
            if (returnType instanceof OptionalType optionalType) {
                return CodeBlock.of("return $T.empty()", optionalType.optionalClass());
            }
            return CodeBlock.of("return null"); // TODO
        }
    }

    private class CollectionContainerBuilder extends BaseContainerBuilder<CollectionType> implements ContainerBuilder {
        private final String containerVariable;

        public CollectionContainerBuilder(CollectionType collectionType, DAOMethodInfo methodInfo) {
            super(collectionType, methodInfo);
            containerVariable = patchName("l");
        }

        @Override
        public CodeBlock declaration() {
            var componentClass = kiwiTypeConverter.fromKiwiType(returnType.valueComponentType());
            return CodeBlock.of(
                    "$T<$T> $L = new $T<>()", List.class, componentClass, containerVariable, ArrayList.class);
        }

        @Override
        public @Nullable KiwiType componentTypeFor(ResultPart resultPart) {
            if (resultPart == ResultPart.SIMPLE
                    && !returnType.valueComponentType().isSimple()) {
                return returnType.valueComponentType();
            }
            return null;
        }

        @Override
        public CodeBlock add() {
            var valueVariable = returnType.valueComponentType().isSimple()
                    ? columnName(firstColumnOfPart(ResultPart.SIMPLE))
                    : prefixName(ResultPart.SIMPLE, "Value");
            String patchedValueVariable = patchedNames.get(valueVariable);
            return CodeBlock.builder()
                    .beginControlFlow("if ($L != null)", patchedValueVariable)
                    .addStatement("$L.add($L)", containerVariable, patchedValueVariable)
                    .endControlFlow()
                    .build();
        }

        @Override
        public CodeBlock returnValue() {
            return CodeBlock.builder()
                    .add("return ")
                    .addNamed(
                            returnType.type().fromListTemplate(),
                            Map.of(
                                    "componentClass",
                                    kiwiTypeConverter.fromKiwiType(returnType.valueComponentType()),
                                    "listVariable",
                                    containerVariable))
                    .build();
        }
    }

    private class MapContainerBuilder extends BaseContainerBuilder<MapType> implements ContainerBuilder {
        private final String containerVariable;
        private final boolean valueIsCollection;

        public MapContainerBuilder(MapType mapType, DAOMethodInfo methodInfo) {
            super(mapType, methodInfo);
            /* Cases:
            Map<KeyType, Simple>
            Map<KeyType, Collection<Simple>>
             */
            containerVariable = patchName("m");
            valueIsCollection = mapType.valueType() instanceof CollectionType
                    // if the value is a SQL array, it must be the only Value column
                    && methodInfo.columns().stream()
                            .filter(c -> c.resultPart() == ResultPart.VALUE)
                            .noneMatch(c -> c.sqlTypeMapping().kiwiType() instanceof SqlArrayType);
        }

        @Override
        public CodeBlock declaration() {
            // TODO imposing comparable ordering probably isn't right. Maybe if declared as SortedMap
            //            var implementationType = returnType.comparableKey() ? TreeMap.class : HashMap.class;
            var implementationType = LinkedHashMap.class;
            var valueDeclarationType = valueIsCollection
                    ? ParameterizedTypeName.get(
                            ClassName.get(List.class),
                            kiwiTypeConverter.fromKiwiType(returnType.valueComponentType(), true))
                    : kiwiTypeConverter.fromKiwiType(returnType.valueType(), true);
            var declarationType = ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    kiwiTypeConverter.fromKiwiType(returnType.keyType(), true),
                    valueDeclarationType);
            return CodeBlock.of("$T $L = new $T<>()", declarationType, containerVariable, implementationType);
        }

        @Override
        public @Nullable KiwiType componentTypeFor(ResultPart resultPart) {
            if (resultPart == KEY && !returnType.keyType().isSimple()) {
                return returnType.keyType();
            }
            if (resultPart == ResultPart.VALUE
                    && !returnType.valueComponentType().isSimple()) {
                return returnType.valueComponentType();
            }
            return null;
        }

        @Override
        public CodeBlock add() {
            var keyVariable =
                    returnType.keyType().isSimple() ? columnName(firstColumnOfPart(KEY)) : prefixName(KEY, "Value");
            var patchedKeyVariable = patchedNames.get(keyVariable);
            var valueVariable = returnType.valueComponentType().isSimple()
                    ? columnName(firstColumnOfPart(VALUE))
                    : prefixName(VALUE, "Value");
            var patchedValueVariable = patchedNames.get(valueVariable);
            var builder = CodeBlock.builder();
            if (returnType.keyType().isNullable()) {
                builder.beginControlFlow("if ($L != null)", patchedKeyVariable);
            }
            if (returnType.valueComponentType().isNullable()) {
                builder.beginControlFlow("if ($L != null)", patchedValueVariable);
            }
            if (valueIsCollection) {
                builder.addStatement(
                        "$L.computeIfAbsent($L, k -> new ArrayList<>()).add($L)",
                        containerVariable,
                        patchedKeyVariable,
                        patchedValueVariable);
            } else {
                builder.addStatement("$L.put($L, $L)", containerVariable, patchedKeyVariable, patchedValueVariable);
            }
            if (returnType.valueComponentType().isNullable()) {
                builder.endControlFlow();
            }
            if (returnType.keyType().isNullable()) {
                builder.endControlFlow();
            }
            return builder.build();
        }

        @Override
        public CodeBlock returnValue() {
            return CodeBlock.of("return $T.copyOf($L)", Map.class, containerVariable);
        }
    }
}
