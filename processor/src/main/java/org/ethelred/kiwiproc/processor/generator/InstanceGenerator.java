/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.generator;

import static org.ethelred.kiwiproc.processor.ResultPart.KEY;
import static org.ethelred.kiwiproc.processor.ResultPart.VALUE;
import static org.ethelred.kiwiproc.processor.generator.RuntimeTypes.*;

import com.karuslabs.utilitary.Logger;
import com.palantir.javapoet.*;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import org.ethelred.kiwiproc.processor.*;
import org.ethelred.kiwiproc.processor.types.*;
import org.jspecify.annotations.Nullable;

public class InstanceGenerator {

    private final Logger logger;
    private final KiwiTypeConverter kiwiTypeConverter;
    private final CoreTypes coreTypes;
    private final ConversionCodeGenerator conversionGenerator;

    public InstanceGenerator(Logger logger, KiwiTypeConverter kiwiTypeConverter, CoreTypes coreTypes) {
        this.logger = logger;
        this.kiwiTypeConverter = kiwiTypeConverter;
        this.coreTypes = coreTypes;
        this.conversionGenerator = new ConversionCodeGenerator(logger, kiwiTypeConverter);
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
        var ctx = new MethodContext();
        var methodSpecBuilder = MethodSpec.overriding(methodInfo.methodElement());
        boolean isStreamReturn = methodInfo.signature().returnType() instanceof StreamType;
        methodSpecBuilder.addStatement("var connection = context.getConnection()");
        if (isStreamReturn) {
            methodSpecBuilder.beginControlFlow("try");
            methodSpecBuilder.addStatement(
                    "var statement = connection.prepareStatement($S)",
                    methodInfo.parsedSql().parsedSql());
        } else {
            methodSpecBuilder.beginControlFlow(
                    "try (var statement = connection.prepareStatement($S))",
                    methodInfo.parsedSql().parsedSql());
        }
        if (methodInfo.fetchSize() != Integer.MIN_VALUE) {
            methodSpecBuilder.addStatement("statement.setFetchSize($L)", methodInfo.fetchSize());
        }
        methodSpecBuilder.addCode(
                switch (methodInfo.kind()) {
                    case QUERY ->
                        isStreamReturn
                                ? methodBodyForStreamQuery(methodInfo, ctx)
                                : methodBodyForQuery(methodInfo, ctx);
                    case UPDATE -> methodBodyForUpdate(methodInfo, ctx);
                    case BATCH -> methodBodyForBatch(methodInfo, ctx);
                    case DEFAULT -> throw new IllegalArgumentException();
                });
        methodSpecBuilder
                .nextControlFlow("catch ($T e)", SQLException.class) // end try
                .addStatement("throw new $T(e)", UNCHECKED_SQL_EXCEPTION)
                .endControlFlow(); // end catch

        return methodSpecBuilder.build();
    }

    private CodeBlock methodBodyForUpdate(DAOMethodInfo methodInfo, MethodContext ctx) {
        var builder = builderWithParameters(methodInfo.parameterMapping(), ctx);
        builder.addStatement("var rawResult = statement.executeUpdate()");
        KiwiType returnType = methodInfo.signature().returnType();
        if (!(returnType instanceof VoidType)) {
            var conversion = lookupConversion(
                    methodInfo::methodElement, new TypeMapping(CoreTypes.UPDATE_RETURN_TYPE, returnType));
            conversionGenerator.buildConversion(
                    builder, ctx, methodInfo::methodElement, conversion, returnType, "result", "rawResult", true);
            builder.addStatement("return result");
        }
        return builder.build();
    }

    private CodeBlock methodBodyForBatch(DAOMethodInfo methodInfo, MethodContext ctx) {
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
                                ctx.patchName(i.baseName() + "Iterator"),
                                ctx.patchName(i.baseName() + "IteratorValue"))));
        for (var iteratorAndNames : iteratorsAndNames.values()) {
            builder.addNamed(
                            "var $iteratorVariable:L = "
                                    + iteratorAndNames
                                            .iterator
                                            .validCollection()
                                            .toIteratorTemplate(),
                            Map.of(
                                    "iteratorVariable",
                                    iteratorAndNames.iteratorName,
                                    "containerVariable",
                                    iteratorAndNames.iterator.baseName()))
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
        addParameters(builder, parameterMapping, ctx, methodParameterInfo -> {
            var key = methodParameterInfo;
            if (key.recordParent() != null) {
                key = key.recordParent();
            }
            if (iteratorsAndNames.containsKey(key)) {
                return iteratorsAndNames.get(key).iteratorValueName;
            }
            return methodParameterInfo.name().name();
        });
        builder.addStatement("statement.addBatch()");
        builder.addStatement("anyValues = true");
        builder.endControlFlow(); // while hasNext...
        builder.addStatement("var rawResult = anyValues ? statement.executeBatch() : new int[0]");
        KiwiType returnType = methodInfo.signature().returnType();
        if (!(returnType instanceof VoidType)) {
            var conversion = lookupConversion(
                    methodInfo::methodElement, new TypeMapping(CoreTypes.BATCH_RETURN_TYPE, returnType));
            conversionGenerator.buildConversion(
                    builder, ctx, methodInfo::methodElement, conversion, returnType, "result", "rawResult", true);
            builder.addStatement("return result");
        }
        return builder.build();
    }

    private CodeBlock methodBodyForQuery(DAOMethodInfo methodInfo, MethodContext ctx) {
        var builder = builderWithParameters(methodInfo.parameterMapping(), ctx);
        var containerBuilder = containerBuilderFor(methodInfo, ctx);
        builder.addStatement("var rs = statement.executeQuery()");
        builder.addStatement(containerBuilder.declaration());
        builder.beginControlFlow("$L (rs.next())", methodInfo.expectedRows() == RowCount.MANY ? "while" : "if");
        var columns = methodInfo.columns();
        if (!columns.isEmpty()) {
            buildColumnReadingStatements(builder, ctx, columns, methodInfo);
            for (ResultPart resultPart : ResultPart.values()) {
                var componentClass = containerBuilder.componentTypeFor(resultPart);
                if (componentClass != null) {
                    var resultVar = ctx.patchName(prefixName(resultPart, "Value"));
                    var params = columns.stream()
                            .filter(column -> resultPart.equals(column.resultPart()))
                            .map(p -> CodeBlock.of("$L", ctx.patchedNameFor(columnName(p))))
                            .collect(CodeBlock.joining(",\n"));
                    params = CodeBlock.builder().indent().add(params).unindent().build();
                    builder.add("""
                                    var $L = new $T(
                                    $L
                                    );
                                    """, resultVar, kiwiTypeConverter.fromKiwiType(componentClass), params);
                }
            }
            if (methodInfo.expectedRows() == RowCount.EXACTLY_ONE) {
                builder.beginControlFlow("if (rs.next())")
                        .addStatement(
                                "throw new $T($S)",
                                IllegalStateException.class,
                                "Expected exactly one row in result, but more were selected.")
                        .endControlFlow();
            }
            builder.add(containerBuilder.add());
        } else {
            throw new IllegalStateException("Expected columns");
        }
        if (methodInfo.expectedRows() == RowCount.EXACTLY_ONE) {
            builder.nextControlFlow("else")
                    .addStatement(
                            "throw new $T($S)",
                            IllegalStateException.class,
                            "Expected exactly one row in result, but none were selected.");
        }
        builder.endControlFlow(); // end while or if
        builder.add(containerBuilder.returnValue());
        return builder.build();
    }

    private CodeBlock methodBodyForStreamQuery(DAOMethodInfo methodInfo, MethodContext ctx) {
        var builder = builderWithParameters(methodInfo.parameterMapping(), ctx);
        if (!(methodInfo.signature().returnType() instanceof StreamType streamType)) {
            throw new IllegalArgumentException("Expected StreamType");
        }
        var elementType = streamType.containedType();

        // "rs" is the lambda parameter name — register it so patchName() avoids it for column variables
        ctx.registerParameterName("rs");

        // Inner try so that if executeQuery() throws, the statement is closed before propagating
        builder.beginControlFlow("try");
        builder.addStatement("var resultSet = statement.executeQuery()");

        // Open lambda — outer RS is "resultSet", lambda param is "rs"
        builder.add("return $T.of(statement, resultSet, rs -> {\n", RESULT_SET_STREAM);
        builder.indent();

        var columns = methodInfo.columns();
        if (columns.isEmpty()) {
            throw new IllegalStateException("Expected columns for stream query");
        }

        // Column reading — uses "rs" (the lambda parameter)
        buildColumnReadingStatements(builder, ctx, columns, methodInfo);

        // Construct record if element type is not simple; otherwise return raw column value
        if (!elementType.isSimple()) {
            var resultVar = ctx.patchName(prefixName(ResultPart.SIMPLE, "Value"));
            var params = columns.stream()
                    .filter(column -> ResultPart.SIMPLE.equals(column.resultPart()))
                    .map(p -> CodeBlock.of("$L", ctx.patchedNameFor(columnName(p))))
                    .collect(CodeBlock.joining(",\n"));
            params = CodeBlock.builder().indent().add(params).unindent().build();
            builder.add("""
                    var $L = new $T(
                    $L
                    );
                    """, resultVar, kiwiTypeConverter.fromKiwiType(elementType), params);
            builder.addStatement("return $L", resultVar);
        } else {
            var firstSimple = columns.stream()
                    .filter(c -> c.resultPart() == ResultPart.SIMPLE)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No SIMPLE column for stream query"));
            String patchedValueVariable = ctx.patchedNameFor(columnName(firstSimple));
            builder.addStatement("return $L", patchedValueVariable);
        }

        builder.unindent();
        builder.add("});\n");

        // Close inner try — if executeQuery() threw, close statement (suppressing close exception) and rethrow
        builder.nextControlFlow("catch ($T e)", SQLException.class)
                .beginControlFlow("try")
                .addStatement("statement.close()")
                .nextControlFlow("catch ($T ce)", SQLException.class)
                .addStatement("e.addSuppressed(ce)")
                .endControlFlow()
                .addStatement("throw e")
                .endControlFlow();

        return builder.build();
    }

    private void buildColumnReadingStatements(
            CodeBlock.Builder builder, MethodContext ctx, List<DAOResultColumn> columns, DAOMethodInfo methodInfo) {
        for (DAOResultColumn column : columns) {
            String rawName = ctx.patchName(columnName(column) + "Raw");
            String accessorSuffix = column.sqlTypeMapping().accessorSuffix();
            TypeName typeName =
                    kiwiTypeConverter.fromKiwiType(column.sqlTypeMapping().kiwiType());
            if ("Object".equals(accessorSuffix)) {
                builder.addStatement("$1T $2L = rs.getObject($3S, $1T.class)", typeName, rawName, column.name());
            } else {
                builder.addStatement("$T $L = rs.get$L($S)", typeName, rawName, accessorSuffix, column.name());
            }
            if (column.sqlTypeMapping().isNullable()) {
                builder.beginControlFlow("if (rs.wasNull())")
                        .addStatement("$L = null", rawName)
                        .endControlFlow();
            }
            var varName = ctx.patchName(columnName(column));
            conversionGenerator.buildConversion(
                    builder,
                    ctx,
                    methodInfo::methodElement,
                    column.conversion(),
                    column.asTypeMapping().target(),
                    varName,
                    rawName,
                    true);
        }
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

    private CodeBlock.Builder builderWithParameters(List<DAOParameterInfo> parameterMapping, MethodContext ctx) {
        var builder = CodeBlock.builder();
        addParameters(builder, parameterMapping, ctx, methodParameterInfo -> {
            if (methodParameterInfo.recordParent() != null) {
                return methodParameterInfo.recordParent().name().name();
            }
            return methodParameterInfo.name().name();
        });
        return builder;
    }

    private void addParameters(
            CodeBlock.Builder builder,
            List<DAOParameterInfo> parameterMapping,
            MethodContext ctx,
            Function<MethodParameterInfo, String> sourceLookup) {
        parameterMapping.forEach(parameterInfo -> {
            var name = "param" + parameterInfo.index();
            String accessor = sourceLookup.apply(parameterInfo.source()) + parameterInfo.javaAccessorSuffix();
            conversionGenerator.buildConversion(
                    builder,
                    ctx,
                    parameterInfo,
                    parameterInfo.conversion(),
                    parameterInfo.mapper().target(),
                    name,
                    accessor,
                    true);
            var nullableSource = parameterInfo.mapper().source().isNullable();
            if (nullableSource) {
                builder.beginControlFlow("if ($L == null)", name)
                        .addStatement("statement.setNull($L, $L)", parameterInfo.index(), parameterInfo.sqlType())
                        .nextControlFlow("else");
            }
            if ("setObject".equals(parameterInfo.setter())
                    && conversionGenerator.isEnumConversion(parameterInfo.conversion())) {
                builder.addStatement(
                        "statement.setObject($L, $L, $L)", parameterInfo.index(), name, parameterInfo.sqlType());
            } else {
                builder.addStatement("statement.$L($L, $L)", parameterInfo.setter(), parameterInfo.index(), name);
            }
            if (nullableSource) {
                builder.endControlFlow();
            }
            ctx.registerParameterName(accessor);
        });
    }

    private ContainerBuilder containerBuilderFor(DAOMethodInfo methodInfo, MethodContext ctx) {
        var returnType = methodInfo.signature().returnType();
        if (returnType instanceof MapType mapType) {
            return new MapContainerBuilder(mapType, methodInfo, ctx);
        }
        if (returnType instanceof CollectionType collectionType) {
            return new CollectionContainerBuilder(collectionType, methodInfo, ctx);
        }
        return new SingleRowContainerBuilder(returnType, methodInfo, ctx);
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
        protected final MethodContext ctx;

        private BaseContainerBuilder(T returnType, DAOMethodInfo methodInfo, MethodContext ctx) {
            this.methodInfo = methodInfo;
            this.returnType = returnType;
            this.ctx = ctx;
        }

        protected DAOResultColumn firstColumnOfPart(ResultPart part) {
            return methodInfo.columns().stream()
                    .filter(c -> c.resultPart() == part)
                    .findFirst()
                    .orElseThrow(IllegalArgumentException::new);
        }
    }

    private class SingleRowContainerBuilder extends BaseContainerBuilder<KiwiType> implements ContainerBuilder {

        public SingleRowContainerBuilder(KiwiType returnType, DAOMethodInfo methodInfo, MethodContext ctx) {
            super(returnType, methodInfo, ctx);
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
            var builder = CodeBlock.builder();
            var valueVariable = returnType.valueComponentType().isSimple()
                    ? columnName(firstColumnOfPart(ResultPart.SIMPLE))
                    : prefixName(ResultPart.SIMPLE, "Value");
            var patchedValueVariable = ctx.patchedNameFor(valueVariable);
            if (returnType instanceof OptionalType optionalType) {
                if (!returnType.valueComponentType().isNullable()) {
                    return builder.addStatement("return $T.of($L)", optionalType.optionalClass(), patchedValueVariable)
                            .build();
                }
                return builder.addStatement(
                                "return $1L == null ? $2T.empty() : $2T.of($1L)",
                                patchedValueVariable,
                                optionalType.optionalClass())
                        .build();
            }
            return builder.addStatement("return $L", patchedValueVariable).build();
        }

        @Override
        public CodeBlock returnValue() {
            if (returnType instanceof OptionalType optionalType) {
                return CodeBlock.builder()
                        .addStatement("return $T.empty()", optionalType.optionalClass())
                        .build();
            }
            if (returnType.isNullable()) {
                return CodeBlock.builder().addStatement("return null").build();
            }
            // unreachable case due to "exactly one" checks.
            return CodeBlock.builder().build();
        }
    }

    private class CollectionContainerBuilder extends BaseContainerBuilder<CollectionType> implements ContainerBuilder {
        private final String containerVariable;

        public CollectionContainerBuilder(CollectionType collectionType, DAOMethodInfo methodInfo, MethodContext ctx) {
            super(collectionType, methodInfo, ctx);
            containerVariable = ctx.patchName("l");
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
            String patchedValueVariable = ctx.patchedNameFor(valueVariable);
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
                    .addStatement("")
                    .build();
        }
    }

    private class MapContainerBuilder extends BaseContainerBuilder<MapType> implements ContainerBuilder {
        private final String containerVariable;
        private final boolean valueIsCollection;

        public MapContainerBuilder(MapType mapType, DAOMethodInfo methodInfo, MethodContext ctx) {
            super(mapType, methodInfo, ctx);
            /* Cases:
            Map<KeyType, Simple>
            Map<KeyType, Collection<Simple>>
             */
            containerVariable = ctx.patchName("m");
            valueIsCollection = mapType.valueType() instanceof CollectionType
                    // if the value is a SQL array, it must be the only Value column
                    && methodInfo.columns().stream()
                            .filter(c -> c.resultPart() == ResultPart.VALUE)
                            .noneMatch(c -> c.sqlTypeMapping().kiwiType() instanceof SqlArrayType);
        }

        @Override
        public CodeBlock declaration() {
            var implementationType = returnType.isSortedMap() ? TreeMap.class : LinkedHashMap.class;
            var baseType = returnType.isSortedMap() ? SortedMap.class : Map.class;
            var valueDeclarationType = valueIsCollection
                    ? ParameterizedTypeName.get(
                            ClassName.get(List.class),
                            kiwiTypeConverter.fromKiwiType(returnType.valueComponentType(), true))
                    : kiwiTypeConverter.fromKiwiType(returnType.valueType(), true);
            var declarationType = ParameterizedTypeName.get(
                    ClassName.get(baseType),
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
            var patchedKeyVariable = ctx.patchedNameFor(keyVariable);
            var valueVariable = returnType.valueComponentType().isSimple()
                    ? columnName(firstColumnOfPart(VALUE))
                    : prefixName(VALUE, "Value");
            var patchedValueVariable = ctx.patchedNameFor(valueVariable);
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
            if (returnType.isSortedMap()) {
                return CodeBlock.builder()
                        .addStatement("return $T.unmodifiableSortedMap($L)", Collections.class, containerVariable)
                        .build();
            }
            return CodeBlock.builder()
                    .addStatement("return $T.copyOf($L)", Map.class, containerVariable)
                    .build();
        }
    }
}
