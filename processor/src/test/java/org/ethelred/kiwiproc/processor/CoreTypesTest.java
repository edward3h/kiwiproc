/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.ethelred.kiwiproc.processor.TestUtils.ofClass;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
    static CoreTypes coreTypes = new CoreTypes();

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
                var formatted = sfc.conversionFormat().replaceAll("\\$\\d*N", "value");
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
                arguments(ofClass(double.class), ofClass(short.class), true, true, "($T) value"),
                arguments(ofClass(double.class), ofClass(Short.class, true), true, true, "($T) value"),
                arguments(ofClass(double.class), ofClass(BigDecimal.class), true, false, "$T.valueOf(value)"),
                arguments(ofClass(String.class), ofClass(int.class), true, true, "$T.parse$L(value)"),
                arguments(ofClass(String.class), ofClass(Integer.class, true), true, true, "$T.valueOf(value)"),
                arguments(
                        new ContainerType(ValidContainerType.LIST, ofClass(Integer.class, true)),
                        new SqlArrayType(
                                ofClass(int.class), new SqlTypeMapping(JDBCType.INTEGER, int.class, "Int"), "ignored"),
                        true,
                        false,
                        "fail"),
                arguments(ofClass(int.class), ofClass(boolean.class), true, false, "value != 0"));
    }

    @ParameterizedTest
    @MethodSource
    public void allSimpleConversions(TypeMapping mapping) {
        var conversion = coreTypes.lookup(mapping);
        assertThat(conversion).isNotNull();
        assertThat(conversion.isValid()).isTrue();
    }

    @ParameterizedTest
    @MethodSource
    public void inverseConversions(TypeMapping mapping) {
        assertThat(expectedMappings().contains(mapping));
    }

    public static Stream<Arguments> inverseConversions() {
        return coreTypes.simpleMappings.keySet().stream().map(Arguments::arguments);
    }

    public static Stream<Arguments> allSimpleConversions() {
        return expectedMappings().stream().map(Arguments::arguments);
    }

    static Set<TypeMapping> expectedMappings() {
        var types = simpleTypes();
        return types.stream()
                .flatMap(t1 -> types.stream().map(t2 -> new TypeMapping(t1, t2)))
                .filter(INCLUDE)
                .collect(Collectors.toSet());
    }

    static Set<KiwiType> simpleTypes() {
        Set<KiwiType> types = new LinkedHashSet<>();
        CoreTypes.primitiveToBoxed.keySet().forEach(c -> types.add(new PrimitiveKiwiType(c.getSimpleName(), false)));
        CoreTypes.BASIC_TYPES.forEach(c -> types.add(new BasicType(c.getPackageName(), c.getSimpleName(), false)));
        return types;
    }

    static boolean exclude(TypeMapping typeMapping) {
        var source = typeMapping.source();
        var target = typeMapping.target();
        if (source.className().matches("char")) {
            return target.className().matches(".*(Big|Date|Time).*");
        }
        if (target.className().matches("char")) {
            return source.className().matches(".*(Big|Date|Time).*");
        }
        if (source.className().matches("boolean")) {
            return target.className().matches(".*(BigDecimal|Date|Time|double|float).*");
        }
        if (target.className().matches("boolean")) {
            return source.className().matches(".*(BigDecimal|Date|Time|double|float).*");
        }
        if ("long".equals(target.className())) {
            return Set.of("OffsetTime", "LocalTime").contains(source.className());
        }
        if (target instanceof PrimitiveKiwiType || target.className().startsWith("Big")) {
            return source.className().matches(".*(Date|Time).*");
        }
        if (source instanceof PrimitiveKiwiType || source.className().startsWith("Big")) {
            return target.className().matches(".*(Date|Time).*");
        }
        var key = typeMapping.source().className() + "|" + typeMapping.target().className();
        return otherExcludeKeys.contains(key);
    }

    static Set<String> otherExcludeKeys = Set.of(
            "LocalDate|OffsetTime",
            "OffsetTime|LocalDate",
            "LocalDateTime|OffsetTime",
            "LocalTime|LocalDate",
            "OffsetDateTime|LocalTime",
            "LocalDateTime|OffsetDateTime", // TODO?
            "LocalTime|OffsetDateTime",
            "LocalDate|LocalTime",
            "OffsetTime|LocalDateTime",
            "OffsetTime|OffsetDateTime",
            "LocalTime|LocalDateTime",
            "LocalTime|OffsetTime");

    static Predicate<TypeMapping> INCLUDE = typeMapping -> !exclude(typeMapping);
}
