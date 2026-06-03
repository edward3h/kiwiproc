/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.generator;

import com.karuslabs.utilitary.Logger;
import com.palantir.javapoet.*;
import java.sql.ResultSet;
import java.util.*;
import java.util.function.Supplier;
import javax.lang.model.element.Element;
import org.ethelred.kiwiproc.processor.*;
import org.ethelred.kiwiproc.processor.types.*;

class ConversionCodeGenerator {

    private final Logger logger;
    private final KiwiTypeConverter kiwiTypeConverter;

    ConversionCodeGenerator(Logger logger, KiwiTypeConverter kiwiTypeConverter) {
        this.logger = logger;
        this.kiwiTypeConverter = kiwiTypeConverter;
    }

    void buildConversion(
            CodeBlock.Builder builder,
            MethodContext ctx,
            Supplier<Element> elementSource,
            Conversion conversion,
            KiwiType targetType,
            String assignee,
            String accessor,
            boolean withVar) {
        try {
            var insertVar =
                    withVar ? CodeBlock.of("$T ", kiwiTypeConverter.fromKiwiType(targetType, true)) : CodeBlock.of("");
            if (conversion instanceof AssignmentConversion) {
                builder.addStatement("$L$L = $L", insertVar, assignee, accessor);
            } else if (conversion instanceof StringFormatConversion sfc) {
                builder.add("$L$L =", insertVar, assignee)
                        .addStatement(sfc.conversionFormat(), sfc.withAccessor(accessor));
            } else if (conversion instanceof ToSqlArrayConversion sac) {
                buildToSqlArrayConversion(builder, ctx, elementSource, sac, insertVar, assignee, accessor);
            } else if (conversion instanceof FromSqlArrayConversion sac) {
                buildFromSqlArrayConversion(builder, ctx, elementSource, sac, insertVar, assignee, accessor);
            } else if (conversion instanceof CollectionConversion cc) {
                buildCollectionConversion(builder, ctx, elementSource, cc, insertVar, assignee, accessor);
            } else if (conversion instanceof NullableSourceConversion nsc) {
                builder.addStatement("$T $L = null", kiwiTypeConverter.fromKiwiType(targetType), assignee)
                        .beginControlFlow("if ($L != null)", accessor);
                buildConversion(builder, ctx, elementSource, nsc.conversion(), targetType, assignee, accessor, false);
                builder.endControlFlow();
            } else if (conversion instanceof EnumFromStringConversion efsc) {
                var enumClass = ClassName.get(
                        efsc.enumType().packageName(), efsc.enumType().className());
                builder.addStatement("$L$L = $T.valueOf($L)", insertVar, assignee, enumClass, accessor);
            } else if (conversion instanceof EnumToStringConversion) {
                builder.addStatement("$L$L = $L.name()", insertVar, assignee, accessor);
            } else {
                logger.error(elementSource.get(), "Unsupported Conversion %s".formatted(conversion));
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Error in conversion " + conversion, e);
        }
    }

    private void buildToSqlArrayConversion(
            CodeBlock.Builder builder,
            MethodContext ctx,
            Supplier<Element> elementSource,
            ToSqlArrayConversion sac,
            CodeBlock insertVar,
            String assignee,
            String accessor) {
        Conversion elementConversion = sac.elementConversion();
        String elementObjects = ctx.patchName("elementObjects");
        String lambdaValue = ctx.patchName("value");
        builder.add("Object[] $L = ", elementObjects)
                .addNamed(sac.ct().type().toStreamTemplate(), Map.of("containerVariable", accessor))
                .indent()
                .add("\n.map($L -> {\n", lambdaValue)
                .indent();
        buildConversion(
                builder, ctx, elementSource, elementConversion, sac.sat().containedType(), "tmp", lambdaValue, true);
        builder.addStatement("return tmp").unindent().add("})\n.toArray();\n").unindent();
        builder.addStatement(
                "$L$L = connection.createArrayOf($S, $L)",
                insertVar,
                assignee,
                sac.sat().componentDbType(),
                elementObjects);
    }

    private void buildFromSqlArrayConversion(
            CodeBlock.Builder builder,
            MethodContext ctx,
            Supplier<Element> elementSource,
            FromSqlArrayConversion sac,
            CodeBlock insertVar,
            String assignee,
            String accessor) {
        var arrayRS = ctx.patchName("arrayRS");
        var arrayList = ctx.patchName("arrayList");
        var rawItemValue = ctx.patchName("rawItemValue");
        var itemValue = ctx.patchName("itemValue");
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
        buildConversion(
                builder,
                ctx,
                elementSource,
                sac.elementConversion(),
                sac.ct().containedType(),
                itemValue,
                rawItemValue,
                true);
        builder.addStatement("$L.add($L)", arrayList, itemValue)
                .endControlFlow()
                .add("$L$L = ", insertVar, assignee)
                .addNamed(
                        sac.ct().type().fromListTemplate(),
                        Map.of("componentClass", componentClass, "listVariable", arrayList))
                .addStatement("");
    }

    private void buildCollectionConversion(
            CodeBlock.Builder builder,
            MethodContext ctx,
            Supplier<Element> elementSource,
            CollectionConversion cc,
            CodeBlock insertVar,
            String assignee,
            String accessor) {
        var sourceIterator = ctx.patchName("sourceIterator");
        var arrayList = ctx.patchName("arrayList");
        var rawItemValue = ctx.patchName("rawItemValue");
        var itemValue = ctx.patchName("itemValue");
        TypeName componentClass = kiwiTypeConverter.fromKiwiType(cc.sourceType().containedType(), true);
        builder.addNamed(
                        "var $iteratorName:L = " + cc.sourceType().type().toIteratorTemplate(),
                        Map.of("iteratorName", sourceIterator, "containerVariable", accessor))
                .addStatement("")
                .addStatement("List<$T> $L = new $T<>()", componentClass, arrayList, ArrayList.class)
                .beginControlFlow("while ($L.hasNext())", sourceIterator)
                .addStatement("var $L = $L.next()", rawItemValue, sourceIterator);
        buildConversion(
                builder,
                ctx,
                elementSource,
                cc.containedTypeConversion(),
                cc.targetType().containedType(),
                itemValue,
                rawItemValue,
                true);
        builder.addStatement("$L.add($L)", arrayList, itemValue)
                .endControlFlow()
                .add("$L$L = ", insertVar, assignee)
                .addNamed(
                        cc.targetType().type().fromListTemplate(),
                        Map.of("componentClass", componentClass, "listVariable", arrayList))
                .addStatement("");
    }

    boolean isEnumConversion(Conversion conversion) {
        if (conversion instanceof EnumToStringConversion) {
            return true;
        }
        if (conversion instanceof NullableSourceConversion nsc) {
            return isEnumConversion(nsc.conversion());
        }
        return false;
    }
}
