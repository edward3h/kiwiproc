package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.ethelred.kiwiproc.processor.SimpleType.ofClass;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;
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
        assertThat(type).isInstanceOf(SimpleType.class);
        assertThat(((SimpleType) type).className()).isEqualTo("LocalDate");
        assertThat(((SimpleType) type).packageName()).isEqualTo("java.time");
        assertThat(((SimpleType) type).isNullable()).isFalse();

        type = coreTypes.type(short.class);
        assertThat(type).isInstanceOf(SimpleType.class);
        assertThat(((SimpleType) type).className()).isEqualTo("short");
        assertThat(((SimpleType) type).packageName()).isEqualTo("");
        assertThat(((SimpleType) type).isNullable()).isFalse();

        type = coreTypes.type(Short.class);
        assertThat(type).isInstanceOf(SimpleType.class);
        assertThat(((SimpleType) type).className()).isEqualTo("Short");
        assertThat(((SimpleType) type).packageName()).isEqualTo("java.lang");
        assertThat(((SimpleType) type).isNullable()).isTrue();

        type = coreTypes.type(OutputStream.class);
        assertThat(type).isInstanceOf(UnsupportedType.class);
    }

    @ParameterizedTest
    @MethodSource
    void testConversions(
            SimpleType source, SimpleType target, boolean isValid, boolean isWarning, String conversionFormatContains) {
        var conversion = coreTypes.lookup(source, target);
        //        System.out.println(conversion);
        assertWithMessage("is valid").that(conversion.isValid()).isEqualTo(isValid);
        assertWithMessage("is warning").that(conversion.hasWarning()).isEqualTo(isWarning);
        if (conversion.isValid()) {
            var formatted = conversion.conversionFormat().formatted("value");
            assertThat(formatted).isEqualTo(conversionFormatContains);
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
                arguments(ofClass(String.class), ofClass(Integer.class, true), true, true, "Integer.valueOf(value)"));
    }
}
