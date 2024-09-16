package org.ethelred.kiwiproc.processor.generator;

import static org.ethelred.kiwiproc.processor.generator.RuntimeTypes.*;

import com.karuslabs.utilitary.Logger;
import com.palantir.javapoet.*;
import java.sql.SQLException;
import java.util.*;
import javax.lang.model.element.Modifier;
import org.ethelred.kiwiproc.processor.*;

public class InstanceGenerator {

    private final Logger logger;
    private final KiwiTypeConverter kiwiTypeConverter;
    private final CoreTypes coreTypes;
    private final Set<String> parameterNames = new HashSet<>();
    private final Map<String, String> patchedNames = new HashMap<>();

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
        return CodeBlock.of("//TODO");
    }

    private CodeBlock batchMethodBody(DAOMethodInfo methodInfo) {
        return CodeBlock.of("//TODO");
    }

    private CodeBlock queryMethodBody(DAOMethodInfo methodInfo) {
        var builder = CodeBlock.builder();
        methodInfo.parameterMapping().forEach(parameterInfo -> {
            var name = "param" + parameterInfo.index();
            var conversion = lookupConversion(parameterInfo::element, parameterInfo.mapper());
            buildConversion(builder, conversion, name, parameterInfo.javaAccessor(), true);
            var nullableSource = parameterInfo.mapper().source().isNullable();
            //            if (nullableSource) {
            //                builder.beginControlFlow("if ($L == null)", name)
            //                        .addStatement("statement.setNull($L, $L)", parameterInfo.index(),
            // parameterInfo.sqlType())
            //                        .nextControlFlow("else");
            //            }
            builder.addStatement("statement.$L($L, $L)", parameterInfo.setter(), parameterInfo.index(), name);
            if (nullableSource) {
                builder.endControlFlow();
            }
            parameterNames.add(parameterInfo.javaAccessor());
        });
        builder.addStatement("var rs = statement.executeQuery()")
                .addStatement(
                        "List<$T> l = new $T<>()",
                        kiwiTypeConverter.fromKiwiType(methodInfo.resultComponentType()),
                        ArrayList.class)
                .beginControlFlow("$L (rs.next())", methodInfo.singleResult() ? "if" : "while");
        var singleColumn = methodInfo.singleColumn();
        var multipleColumns = methodInfo.multipleColumns();
        if (singleColumn != null) {
            TypeMapping mapping = singleColumn.asTypeMapping();
            var conversion = lookupConversion(methodInfo::methodElement, mapping);
            builder.addStatement(
                    "var rawValue = rs.get$L($S)", singleColumn.sqlTypeMapping().accessorSuffix(), singleColumn.name());
            buildConversion(builder, conversion, "value", "rawValue", true);
        } else if (!multipleColumns.isEmpty()) {
            multipleColumns.forEach(daoResultColumn -> {
                var conversion = lookupConversion(methodInfo::methodElement, daoResultColumn.asTypeMapping());
                String rawName = daoResultColumn.name() + "Raw";
                builder.addStatement(
                        "$T $L = rs.get$L($S)",
                        ClassName.get(
                                daoResultColumn.targetType().packageName(),
                                daoResultColumn.targetType().className()),
                        rawName,
                        daoResultColumn.sqlTypeMapping().accessorSuffix(),
                        daoResultColumn.name());
                if (daoResultColumn.sqlTypeMapping().isNullable()) {
                    builder.beginControlFlow("if (rs.wasNull())")
                            .addStatement("$L = null", rawName)
                            .endControlFlow();
                }
                var varName = patchName(daoResultColumn.name());
                buildConversion(builder, conversion, varName, rawName, true);
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
                    kiwiTypeConverter.fromKiwiType(methodInfo.resultComponentType()),
                    params);
        } else {
            throw new IllegalStateException("Expected singleColumn or multipleColumns");
        }
        builder.addStatement("l.add(value)")
                .endControlFlow() // end while
                .addStatement("return $L", methodInfo.fromList());
        return builder.build();
    }

    private void buildConversion(
            CodeBlock.Builder builder, Conversion conversion, String assignee, String accessor, boolean withVar) {
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
            Object[] elementObjects = listParam.toArray();
            var param1 = connection.createArrayOf("_int4", elementObjects);
             */
            String elementObjects = patchName("elementObjects");
            builder.addStatement(
                    "Object[] $L = $L",
                    elementObjects,
                    String.format(sac.ct().type().toObjectArrayTemplate(), accessor));
            builder.addStatement(
                    "$L$L = connection.createArrayOf($S, $L)",
                    insertVar,
                    assignee,
                    sac.sat().dbType(),
                    elementObjects);
        } else {
            logger.error(null, "Unsupported Conversion %s".formatted(conversion)); // TODO add Element
        }
    }

    private String patchName(String name) {
        return patchedNames.computeIfAbsent(name, k -> {
            var newName = k;
            while (parameterNames.contains(newName)) {
                newName = "_" + newName;
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
