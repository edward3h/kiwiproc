package org.ethelred.kiwiproc.processor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ethelred.kiwiproc.processor.types.BasicType;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.jspecify.annotations.Nullable;

import static java.util.Map.entry;

public class CoreTypes {
    public static final BasicType STRING_TYPE = new BasicType(String.class.getPackageName(), String.class.getSimpleName(), false);
    public static final Set<Class<?>> BASIC_TYPES = Set.of(
            String.class,
            BigInteger.class,
            BigDecimal.class,
            LocalDate.class,
            LocalTime.class,
            OffsetTime.class,
            LocalDateTime.class,
            OffsetDateTime.class
    );

    public record Conversion(boolean isValid, @Nullable String warning, String conversionFormat) {
        public boolean hasWarning() {
            return warning != null;
        }
    }

    public static final Map<Class<?>, Class<?>> primitiveToBoxed = Map.ofEntries(
            entry(boolean.class, Boolean.class),
            entry(byte.class, Byte.class),
            entry(char.class, Character.class),
            entry(short.class, Short.class),
            entry(int.class, Integer.class),
            entry(long.class, Long.class),
            entry(float.class, Float.class),
            entry(double.class, Double.class));

    public static final Map<String, String> primitiveToBoxedStrings = primitiveToBoxed.entrySet().stream().map(e -> entry(e.getKey().getSimpleName(), e.getValue().getSimpleName())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    /*
    The key type can be assigned to any of the value types without casting.
    Primitive type mappings that are NOT in this map require a cast and a "lossy converson" warning.
     */
    private static final Map<Class<?>, Set<Class<?>>> assignableFrom = Map.of(
            byte.class, Set.of(short.class, int.class, long.class, float.class, double.class),
            char.class, Set.of(int.class, long.class, float.class, double.class),
            short.class, Set.of(int.class, long.class, float.class, double.class),
            int.class, Set.of(long.class, float.class, double.class),
            long.class, Set.of(float.class, double.class),
            float.class, Set.of(double.class));

    // boxing a primitive type is also assignable
    // unboxing is invalid in Kiwiproc, since it would convert a nullable to non-null
    private static boolean isAssignable(Class<?> source, Class<?> target) {
        return assignableFrom.getOrDefault(source, Set.of()).contains(target);
    }

    private final Conversion invalid = new Conversion(false, null, "invalid");
    Map<Class<?>, KiwiType> coreTypes;
    Map<TypeMapping, Conversion> coreMappings;

    public CoreTypes() {
        coreTypes = defineTypes();
        coreMappings = defineMappings();
        //        System.out.println(
        //                coreMappings.entrySet().stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    private Map<TypeMapping, Conversion> defineMappings() {
        List<Map.Entry<TypeMapping, Conversion>> entries = new ArrayList<>(200);

        addPrimitiveMappings(entries);
        addPrimitiveParseMappings(entries);
        addBigNumberMappings(entries);
        addDateTimeMappings(entries);

        return entries.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private void addPrimitiveParseMappings(Collection<Map.Entry<TypeMapping, Conversion>> entries) {
        primitiveToBoxed.keySet().forEach(target -> {
            String warning = "possible NumberFormatException parsing String to %s".formatted(target.getName());
            Class<?> boxed = primitiveToBoxed.get(target);
            // String -> primitive
            TypeMapping t = new TypeMapping(STRING_TYPE, coreTypes.get(target));
            Conversion c = new Conversion(
                    true,
                    warning,
                    "%s.parse%s(%%s)".formatted(boxed.getSimpleName(), Util.capitalizeFirst(target.getSimpleName())));
            entries.add(entry(t, c));
            // String -> boxed
            t = new TypeMapping(STRING_TYPE, coreTypes.get(boxed));
            c = new Conversion(true, warning, "%s.valueOf(%%s)".formatted(boxed.getSimpleName()));
            entries.add(entry(t, c));
        });
    }

    private void addDateTimeMappings(Collection<Map.Entry<TypeMapping, Conversion>> entries) {}

    private void addBigNumberMappings(Collection<Map.Entry<TypeMapping, Conversion>> entries) {
        List.of(BigInteger.class, BigDecimal.class).forEach(big -> {
            // primitive -> Big
            Stream.of(byte.class, short.class, int.class, long.class, float.class, double.class)
                    .forEach(source -> {
                        entries.add(mappingEntry(source, big, null, "%s.valueOf(%%s)".formatted(big.getSimpleName())));
                    });

            // String -> Big
            String warning = "possible NumberFormatException parsing String to %s".formatted(big.getSimpleName());
            entries.add(mappingEntry(String.class, big, warning, "new %s(%%s)".formatted(big.getSimpleName())));

            // Big -> primitive
            Stream.of(byte.class, short.class, int.class, long.class, float.class, double.class)
                    .forEach(target -> {
                        String w = "possible lossy conversion from %s to %s".formatted(big.getName(), target.getName());
                        entries.add(mappingEntry(big, target, w, "%%s.%sValue()".formatted(target.getName())));
                    });
        });
    }

    private void addPrimitiveMappings(Collection<Map.Entry<TypeMapping, Conversion>> entries) {
        // primitive safe assignments
        assignableFrom.forEach((source, targets) -> {
            targets.forEach(target -> {
                // primitive
                entries.add(mappingEntry(source, target, null, "%s"));
            });
        });

        // primitive lossy assignments
        primitiveToBoxed.keySet().forEach(source -> {
            primitiveToBoxed.keySet().forEach(target -> {
                if (!source.equals(target) && !isAssignable(source, target)) {
                    String warning =
                            "possible lossy conversion from %s to %s".formatted(source.getName(), target.getName());
                    String conversionFormat = "(%s) %%s".formatted(target.getName());
                    entries.add(mappingEntry(source, target, warning, conversionFormat));
                }
            });
        });
    }

    private Map.Entry<TypeMapping, Conversion> mappingEntry(
            Class<?> source, Class<?> target, @Nullable String warning, String conversionFormat) {
        var fromType = Objects.requireNonNull(coreTypes.get(source));
        var toType = Objects.requireNonNull(coreTypes.get(target));
        var mapping = new TypeMapping(fromType, toType);
        var lookup = new Conversion(true, warning, conversionFormat);
        return entry(mapping, lookup);
    }

    private Map<Class<?>, KiwiType> defineTypes() {
        Map<Class<?>, KiwiType> builder = new LinkedHashMap<>(32);
        primitiveToBoxed.forEach((key, value) -> {
            builder.put(key, new PrimitiveKiwiType(key.getSimpleName(), false));
            builder.put(value, new PrimitiveKiwiType(key.getSimpleName(), true));
        });
        BASIC_TYPES.forEach(c -> builder.put(c, new BasicType(c.getPackageName(), c.getSimpleName(), false)));
        return Map.copyOf(builder);
    }

    public KiwiType type(Class<?> aClass) {
        if (coreTypes.containsKey(aClass)) {
            return coreTypes.get(aClass);
        }
        return KiwiType.unsupported();
    }

    public Conversion lookup(TypeMapping mapper) {
            return lookup(mapper.source(), mapper.target());
    }

    public Conversion lookup(KiwiType source, KiwiType target) {
        if (source.equals(target) || source.withIsNullable(true).equals(target)) {
            return new Conversion(true, null, "%s");
        }
        // special case String
        Conversion stringConversion = null;
        if (STRING_TYPE.equals(target) || STRING_TYPE.withIsNullable(true).equals(target)) {
            stringConversion = new Conversion(true, null, "String.valueOf(%s)");
        }
        var result = firstNonNull(
                stringConversion,
                coreMappings.get(new TypeMapping(source, target)),
                coreMappings.get(new TypeMapping(source, target.withIsNullable(false))),
                coreMappings.get(new TypeMapping(source.withIsNullable(false), target.withIsNullable(false))),
                invalid);
        if (result.isValid() && source.isNullable()) {
            result = new Conversion(true, result.warning(), nullWrap(result.conversionFormat()));
        }
        return result;
    }

    private Conversion firstNonNull(@Nullable Conversion... conversions) {
        for (var c : conversions) {
            if (c != null) {
                return c;
            }
        }
        throw new NullPointerException();
    }

    private String nullWrap(String conversionFormat) {
        conversionFormat = conversionFormat.replace("%s", "%<s");
        return "%s == null ? null : " + conversionFormat;
    }
}
