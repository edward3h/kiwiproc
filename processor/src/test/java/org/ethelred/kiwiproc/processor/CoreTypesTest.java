package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.ethelred.kiwiproc.processor.TestUtils.ofClass;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.ethelred.kiwiproc.processor.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Don't exhaustively test every type and mapping, just some examples.
 */
public class CoreTypesTest {
    CoreTypes coreTypes = new CoreTypes();

    @Test
    void typeFromClass() {
        KiwiType type;
        type = coreTypes.type(LocalDate.class);
        assertThat(type).isInstanceOf(BasicType.class);
        assertThat(type.className()).isEqualTo("LocalDate");
        assertThat(type.packageName()).isEqualTo("java.time");
        assertThat(type.isNullable()).isFalse();

        type = coreTypes.type(short.class);
        assertThat(type).isInstanceOf(PrimitiveKiwiType.class);
        assertThat(type.className()).isEqualTo("short");
        assertThat(type.packageName()).isEqualTo("");
        assertThat(type.isNullable()).isFalse();

        type = coreTypes.type(Short.class);
        assertThat(type).isInstanceOf(PrimitiveKiwiType.class);
        assertThat(type.className()).isEqualTo("Short");
        assertThat(type.packageName()).isEqualTo("java.lang");
        assertThat(type.isNullable()).isTrue();

        type = coreTypes.type(OutputStream.class);
        assertThat(type).isInstanceOf(UnsupportedType.class);
    }

    @ParameterizedTest
    @MethodSource
    void testConversions(
            KiwiType source, KiwiType target, boolean isValid, boolean isWarning, String conversionFormatContains) {
        var conversion = coreTypes.lookup(source, target);
        //        System.out.println(conversion);
        assertWithMessage("%s to %s is valid", source, target)
                .that(conversion.isValid())
                .isEqualTo(isValid);
        assertWithMessage("%s to %s is warning", source, target)
                .that(conversion.hasWarning())
                .isEqualTo(isWarning);
        if (conversion.isValid()) {
            if (conversion instanceof StringFormatConversion sfc) {
                var formatted = sfc.conversionFormat().formatted("value");
                assertThat(formatted).isEqualTo(conversionFormatContains);
            }
        }
    }

    public static Stream<Arguments> testConversions() {
        return Stream.of(
                arguments(ofClass(int.class), ofClass(int.class), true, false, "value"),
                arguments(ofClass(int.class), ofClass(Integer.class, true), true, false, "value"),
                arguments(ofClass(short.class), ofClass(Integer.class, true), true, false, "value"),
                arguments(
                        ofClass(Short.class, true),
                        ofClass(Integer.class, true),
                        true,
                        false,
                        "value == null ? null : value"),
                arguments(ofClass(double.class), ofClass(short.class), true, true, "(short) value"),
                arguments(ofClass(double.class), ofClass(Short.class, true), true, true, "(short) value"),
                arguments(ofClass(double.class), ofClass(BigDecimal.class), true, false, "BigDecimal.valueOf(value)"),
                arguments(ofClass(String.class), ofClass(int.class), true, true, "Integer.parseInt(value)"),
                arguments(ofClass(String.class), ofClass(Integer.class, true), true, true, "Integer.valueOf(value)"),
                arguments(
                        new ContainerType(ValidContainerType.LIST, ofClass(Integer.class, true)),
                        new SqlArrayType(
                                ofClass(int.class), new SqlTypeMapping(JDBCType.INTEGER, int.class, "Int"), "ignored"),
                        true,
                        false,
                        "fail"));
    }
}
