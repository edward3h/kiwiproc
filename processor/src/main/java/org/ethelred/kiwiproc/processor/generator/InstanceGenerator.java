package org.ethelred.kiwiproc.processor.generator;

import static org.ethelred.kiwiproc.processor.generator.RuntimeTypes.*;

import com.karuslabs.utilitary.Logger;
import com.palantir.javapoet.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import javax.lang.model.element.Modifier;
import org.ethelred.kiwiproc.processor.*;
import org.ethelred.kiwiproc.processor.types.ContainerType;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;

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
                    case QUERY -> queryMethodBody(methodInfo);
                    case UPDATE -> updateMethodBody(methodInfo);
                    case BATCH -> batchMethodBody(methodInfo);
                    case DEFAULT -> throw new IllegalArgumentException();
                });
        methodSpecBuilder
                .nextControlFlow("catch ($T e)", SQLException.class) // end try
                .addStatement("throw new $T(e)", UNCHECKED_SQL_EXCEPTION)
                .endControlFlow(); // end catch

        return methodSpecBuilder.build();
    }

    private CodeBlock updateMethodBody(DAOMethodInfo methodInfo) {
        var builder = builderWithParameters(methodInfo);
        builder.addStatement("var rawResult = statement.executeUpdate()");
        KiwiType returnType = methodInfo.signature().returnType();
        var conversion = lookupConversion(
                methodInfo::methodElement, new TypeMapping(new PrimitiveKiwiType("int", false), returnType));
        buildConversion(builder, conversion, returnType, "result", "rawResult", true);
        builder.addStatement("return result");
        return builder.build();
    }

    private CodeBlock batchMethodBody(DAOMethodInfo methodInfo) {
        return CodeBlock.of("//TODO\n");
    }

    private CodeBlock queryMethodBody(DAOMethodInfo methodInfo) {
        var builder = builderWithParameters(methodInfo);
        var listVariable = patchName("l");
        TypeName componentClass = kiwiTypeConverter.fromKiwiType(methodInfo.resultComponentType());
        builder.addStatement("var rs = statement.executeQuery()")
                .addStatement("List<$T> $L = new $T<>()", componentClass, listVariable, ArrayList.class)
                .beginControlFlow("$L (rs.next())", methodInfo.singleResult() ? "if" : "while");
        var singleColumn = methodInfo.singleColumn();
        var multipleColumns = methodInfo.multipleColumns();
        if (singleColumn != null) {
            TypeMapping mapping = singleColumn.asTypeMapping();
            var conversion = lookupConversion(methodInfo::methodElement, mapping);
            builder.addStatement(
                    "var rawValue = rs.get$L($S)", singleColumn.sqlTypeMapping().accessorSuffix(), singleColumn.name());
            buildConversion(builder, conversion, mapping.target(), "value", "rawValue", true);
        } else if (!multipleColumns.isEmpty()) {
            multipleColumns.forEach(daoResultColumn -> {
                var conversion = lookupConversion(methodInfo::methodElement, daoResultColumn.asTypeMapping());
                String rawName = daoResultColumn.name() + "Raw";
                String accessorSuffix = daoResultColumn.sqlTypeMapping().accessorSuffix();
                TypeName typeName = kiwiTypeConverter.fromKiwiType(
                        daoResultColumn.sqlTypeMapping().kiwiType());
                if ("Object".equals(accessorSuffix)) { // hacky

                    builder.addStatement(
                            "$1T $2L = rs.getObject($3S, $1T.class)", typeName, rawName, daoResultColumn.name());
                } else {
                    builder.addStatement(
                            "$T $L = rs.get$L($S)", typeName, rawName, accessorSuffix, daoResultColumn.name());
                }
                if (daoResultColumn.sqlTypeMapping().isNullable()) {
                    builder.beginControlFlow("if (rs.wasNull())")
                            .addStatement("$L = null", rawName)
                            .endControlFlow();
                }
                var varName = patchName(daoResultColumn.name());
                buildConversion(
                        builder, conversion, daoResultColumn.asTypeMapping().target(), varName, rawName, true);
            });
            var params = multipleColumns.stream()
                    .map(p -> CodeBlock.of("$L", patchedNames.get(p.name())))
                    .collect(CodeBlock.joining(",\n"));
            params = CodeBlock.builder().indent().add(params).unindent().build();
            builder.add(
                    """
                    var value = new $T(
                    $L
                    );
                    """,
                    componentClass,
                    params);
        } else {
            throw new IllegalStateException("Expected singleColumn or multipleColumns");
        }
        builder.addStatement("$L.add(value)", listVariable).endControlFlow(); // end while
        if (methodInfo.signature().returnType() instanceof ContainerType containerType) {
            builder.add("return ")
                    .addNamed(
                            containerType.type().fromListTemplate(),
                            Map.of("componentClass", componentClass, "listVariable", listVariable))
                    .addStatement("");
        } else {
            builder.addStatement("return $1L.isEmpty() ? null : $1L.get(0)", listVariable);
        }
        return builder.build();
    }

    private CodeBlock.Builder builderWithParameters(DAOMethodInfo methodInfo) {
        var builder = CodeBlock.builder();
        methodInfo.parameterMapping().forEach(parameterInfo -> {
            var name = "param" + parameterInfo.index();
            var conversion = lookupConversion(parameterInfo::element, parameterInfo.mapper());
            buildConversion(
                    builder, conversion, parameterInfo.mapper().target(), name, parameterInfo.javaAccessor(), true);
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
            parameterNames.add(parameterInfo.javaAccessor());
        });
        return builder;
    }

    private void buildConversion(
            CodeBlock.Builder builder,
            Conversion conversion,
            KiwiType targetType,
            String assignee,
            String accessor,
            boolean withVar) {
        var insertVar = withVar ? "var " : "";
        if (conversion instanceof AssignmentConversion) {
            /* e.g.
            var param1 = id;
             */
            builder.addStatement("$L$L = $L", insertVar, assignee, accessor);
        } else if (conversion instanceof StringFormatConversion sfc) {
            /* e.g.
            var param1 = (int) id;
             */
            builder.addStatement(
                    "$L$L = $L", insertVar, assignee, sfc.conversionFormat().formatted(accessor));
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
            buildConversion(builder, elementConversion, sac.sat().containedType(), "tmp", lambdaValue, true);
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
            TypeName componentClass = kiwiTypeConverter.fromKiwiType(sac.ct().containedType());
            builder.addStatement("$T $L = $L.getResultSet()", ResultSet.class, arrayRS, accessor)
                    .addStatement("List<$T> $L = new $T<>()", componentClass, arrayList, ArrayList.class)
                    .beginControlFlow("while ($L.next())", arrayRS)
                    // Array.getResultSet() returns 2 columns: 1 is the index, 2 is the value
                    .addStatement(
                            "var $L = $L.get$L(2)",
                            rawItemValue,
                            arrayRS,
                            sac.sat().componentType().accessorSuffix());
            buildConversion(builder, sac.elementConversion(), sac.ct().containedType(), itemValue, rawItemValue, true);
            builder.addStatement("$L.add($L)", arrayList, itemValue)
                    .endControlFlow()
                    .add("$L$L = ", insertVar, assignee)
                    .addNamed(
                            sac.ct().type().fromListTemplate(),
                            Map.of("componentClass", componentClass, "listVariable", arrayList))
                    .addStatement("");
        } else if (conversion instanceof NullableSourceConversion nsc) {
            builder.addStatement("$T $L = null", kiwiTypeConverter.fromKiwiType(targetType), assignee)
                    .beginControlFlow("if ($L != null)", accessor);
            buildConversion(builder, nsc.conversion(), targetType, assignee, accessor, false);
            builder.endControlFlow();
        } else {
            logger.error(null, "Unsupported Conversion %s".formatted(conversion)); // TODO add Element
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
}
