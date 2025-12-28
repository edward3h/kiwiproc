/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import static java.util.Map.entry;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethelred.kiwiproc.processor.types.*;
import org.jspecify.annotations.Nullable;

public class CoreTypes {
    public static final ObjectType STRING_TYPE =
            new ObjectType(String.class.getPackageName(), String.class.getSimpleName(), false);
    public static final Set<Class<?>> OBJECT_TYPES = Set.of(
            String.class,
            BigInteger.class,
            BigDecimal.class,
            LocalDate.class,
            LocalTime.class,
            OffsetTime.class,
            LocalDateTime.class,
            OffsetDateTime.class);

    public static final Map<Class<?>, Class<?>> primitiveToBoxed = Map.ofEntries(
            entry(boolean.class, Boolean.class),
            entry(byte.class, Byte.class),
            entry(char.class, Character.class),
            entry(short.class, Short.class),
            entry(int.class, Integer.class),
            entry(long.class, Long.class),
            entry(float.class, Float.class),
            entry(double.class, Double.class));

    public static final Map<String, Class<?>> primitiveNameToType =
            primitiveToBoxed.keySet().stream().collect(Collectors.toMap(Class::getSimpleName, x -> x));

    /*
    The key type can be assigned to any of the value types without casting.
    Primitive type mappings that are NOT in this map require a cast and a "lossy conversion" warning.
     */
    private static final Map<Class<?>, Set<Class<?>>> assignableFrom = Map.of(
            byte.class, Set.of(byte.class, short.class, int.class, long.class, float.class, double.class),
            char.class, Set.of(char.class, int.class, long.class, float.class, double.class),
            short.class, Set.of(short.class, int.class, long.class, float.class, double.class),
            int.class, Set.of(int.class, long.class, float.class, double.class),
            long.class, Set.of(long.class, float.class, double.class),
            float.class, Set.of(float.class, double.class));
    public static final KiwiType UPDATE_RETURN_TYPE = new PrimitiveKiwiType("int", false);
    public static final KiwiType BATCH_RETURN_TYPE = new CollectionType(ValidCollection.ARRAY, UPDATE_RETURN_TYPE);

    private record ClassEntry(Class<?> first, Class<?> second) {}

    private static final Map<Class<?>, Set<Class<?>>> assignableTo = assignableFrom.entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(v -> new ClassEntry(v, e.getKey())))
            .collect(Collectors.groupingBy(ce -> ce.first, Collectors.mapping(ce -> ce.second, Collectors.toSet())));

    // boxing a primitive type is also assignable
    // unboxing is invalid in Kiwiproc, since it would convert a nullable to non-null
    private static boolean isAssignable(Class<?> source, Class<?> target) {
        return assignableFrom.getOrDefault(source, Set.of()).contains(target);
    }

    Map<Class<?>, KiwiType> coreTypes;
    Map<TypeMapping, Conversion> simpleMappings;

    public CoreTypes() {
        coreTypes = defineTypes();
        simpleMappings = defineMappings();
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
            if (target.equals(boolean.class)) {
                return; // special case below
            }
            String warning = "possible NumberFormatException parsing String to %s".formatted(target.getName());
            Class<?> boxed = primitiveToBoxed.get(target);
            // String -> primitive
            TypeMapping t = new TypeMapping(STRING_TYPE, coreTypes.get(target));
            StringFormatConversion c = new StringFormatConversion(
                    warning, "$T.parse$L($N)", boxed, Util.capitalizeFirst(target.getSimpleName()));
            entries.add(entry(t, c));
            // String -> boxed
            t = new TypeMapping(STRING_TYPE, coreTypes.get(boxed));
            c = new StringFormatConversion(warning, "$T.valueOf($N)", boxed);
            entries.add(entry(t, c));
        });

        TypeMapping t = new TypeMapping(STRING_TYPE, coreTypes.get(boolean.class));
        String format =
                """
                ($1N.matches("\\d+") && !"0".equals($1N)) || Boolean.parseBoolean($1N)
                """;
        entries.add(entry(t, new StringFormatConversion(null, format)));
    }

    private void addDateTimeMappings(Collection<Map.Entry<TypeMapping, Conversion>> entries) {
        String usesSystemDefaultZoneId = "uses system default ZoneId";
        List.of(LocalDate.class, LocalTime.class, OffsetTime.class, LocalDateTime.class, OffsetDateTime.class)
                .forEach(dtClass -> {
                    entries.add(mappingEntry(
                            String.class,
                            dtClass,
                            "possible DateTimeParseException parsing String to %s".formatted(dtClass.getSimpleName()),
                            "$T.parse($N)",
                            dtClass));

                    entries.add(mappingEntry(
                            long.class,
                            dtClass,
                            usesSystemDefaultZoneId,
                            "$1T.ofInstant($2T.ofEpochMilli($4N), $3T.systemDefault())",
                            dtClass,
                            Instant.class,
                            ZoneId.class));
                });
        entries.add(mappingEntry(OffsetDateTime.class, long.class, null, "$N.toInstant().toEpochMilli()"));
        entries.add(mappingEntry(OffsetDateTime.class, LocalDateTime.class, null, "$N.toLocalDateTime()"));
        entries.add(mappingEntry(OffsetDateTime.class, OffsetTime.class, null, "$N.toOffsetTime()"));
        entries.add(mappingEntry(OffsetDateTime.class, LocalDate.class, null, "$N.toLocalDate()"));
        entries.add(mappingEntry(
                LocalDateTime.class,
                long.class,
                usesSystemDefaultZoneId,
                "$2N.atZone($1T.systemDefault()).toOffsetDateTime().toInstant().toEpochMilli()",
                ZoneId.class));
        entries.add(mappingEntry(LocalDateTime.class, LocalDate.class, null, "$N.toLocalDate()"));
        entries.add(mappingEntry(LocalDateTime.class, LocalTime.class, null, "$N.toLocalTime()"));
        entries.add(mappingEntry(
                LocalDate.class,
                long.class,
                usesSystemDefaultZoneId,
                "$2N.atStartOfDay().atZone($1T.systemDefault()).toOffsetDateTime().toInstant().toEpochMilli()",
                ZoneId.class));
        entries.add(mappingEntry(LocalDate.class, LocalDateTime.class, null, "$N.atStartOfDay()"));
        entries.add(mappingEntry(
                LocalDate.class,
                OffsetDateTime.class,
                null,
                "$2N.atStartOfDay().atZone($1T.systemDefault()).toOffsetDateTime()",
                ZoneId.class));
        entries.add(mappingEntry(OffsetTime.class, LocalTime.class, null, "$N.toLocalTime()"));
    }

    private void addBigNumberMappings(Collection<Map.Entry<TypeMapping, Conversion>> entries) {
        List.of(BigInteger.class, BigDecimal.class).forEach(big -> {
            // primitive -> Big
            Stream.of(byte.class, short.class, int.class, long.class, float.class, double.class)
                    .forEach(source -> {
                        entries.add(mappingEntry(source, big, null, "$T.valueOf($N)", big));
                    });

            // String -> Big
            String warning = "possible NumberFormatException parsing String to %s".formatted(big.getSimpleName());
            entries.add(mappingEntry(String.class, big, warning, "new $T($N)", big));

            // Big -> primitive
            Stream.of(byte.class, short.class, int.class, long.class, float.class, double.class)
                    .forEach(target -> {
                        String w = "possible lossy conversion from %s to %s"
                                .formatted(big.getSimpleName(), target.getName());
                        entries.add(mappingEntry(big, target, w, "$N.%sValue()".formatted(target.getName())));
                    });
        });
        entries.add(mappingEntry(BigInteger.class, boolean.class, null, "!$T.ZERO.equals($N)", BigInteger.class));
        entries.add(mappingEntry(boolean.class, BigInteger.class, null, "$2N ? $1T.ONE : $1T.ZERO", BigInteger.class));
        entries.add(mappingEntry(
                BigDecimal.class,
                BigInteger.class,
                "possible lossy conversion from BigDecimal to BigInteger",
                "$N.toBigInteger()"));
        entries.add(mappingEntry(BigInteger.class, BigDecimal.class, null, "new $T($N)", BigDecimal.class));
    }

    private void addPrimitiveMappings(Collection<Map.Entry<TypeMapping, Conversion>> entries) {
        // primitive safe assignments
        assignableFrom.forEach((source, targets) -> {
            targets.forEach(target -> {
                // primitive
                entries.add(mappingEntry(source, target, new AssignmentConversion()));
            });
        });

        // primitive lossy assignments
        primitiveToBoxed.keySet().forEach(source -> {
            primitiveToBoxed.keySet().forEach(target -> {
                if (!source.equals(boolean.class)
                        && !target.equals(boolean.class)
                        && !source.equals(target)
                        && !isAssignable(source, target)) {
                    String warning =
                            "possible lossy conversion from %s to %s".formatted(source.getName(), target.getName());
                    entries.add(mappingEntry(source, target, warning, "($T) $N", target));
                }
            });
        });

        Stream.of(byte.class, short.class, int.class, long.class).forEach(source -> {
            entries.add(mappingEntry(source, boolean.class, null, "$N != 0"));
            entries.add(mappingEntry(boolean.class, source, null, "$2N ? 1 : 0"));
        });
        entries.add(mappingEntry(char.class, boolean.class, null, "Character.isDigit($1N) && $1N != '0'"));
        entries.add(mappingEntry(boolean.class, char.class, null, "$N ? '1' : '0'"));
    }

    private Map.Entry<TypeMapping, Conversion> mappingEntry(Class<?> source, Class<?> target, Conversion lookup) {
        var fromType = Objects.requireNonNull(coreTypes.get(source));
        var toType = Objects.requireNonNull(coreTypes.get(target));
        var mapping = new TypeMapping(fromType, toType);
        return entry(mapping, lookup);
    }

    private Map.Entry<TypeMapping, Conversion> mappingEntry(
            Class<?> source,
            Class<?> target,
            @Nullable String warning,
            String conversionFormat,
            Object... defaultArgs) {
        return mappingEntry(source, target, new StringFormatConversion(warning, conversionFormat, defaultArgs));
    }

    private Map<Class<?>, KiwiType> defineTypes() {
        Map<Class<?>, KiwiType> builder = new LinkedHashMap<>(32);
        primitiveToBoxed.forEach((key, value) -> {
            builder.put(key, new PrimitiveKiwiType(key.getSimpleName(), false));
            builder.put(value, new PrimitiveKiwiType(key.getSimpleName(), true));
        });
        OBJECT_TYPES.forEach(c -> builder.put(c, new ObjectType(c.getPackageName(), c.getSimpleName(), false)));
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
        if (source.isNullable() && !target.isNullable()) {
            return invalid(source, target);
        }
        if (source.equals(target) || source.withIsNullable(true).equals(target)) {
            return new AssignmentConversion();
        }
        if (source instanceof CollectionType ct && target instanceof SqlArrayType sat) {
            return toSqlArray(ct, sat);
        }
        if (source instanceof SqlArrayType sat && target instanceof CollectionType ct) {
            return fromSqlArray(sat, ct);
        }
        if (source instanceof CollectionType sct && target instanceof CollectionType tct) {
            return new CollectionConversion(sct, tct, lookup(sct.containedType(), tct.containedType()));
        }
        // special case String
        StringFormatConversion stringConversion = null;
        if (STRING_TYPE.equals(target) || STRING_TYPE.withIsNullable(true).equals(target)) {
            stringConversion = new StringFormatConversion(null, "String.valueOf($N)");
        }
        var result = firstNonNull(
                stringConversion,
                simpleMappings.get(new TypeMapping(source, target)),
                simpleMappings.get(new TypeMapping(source, target.withIsNullable(false))),
                simpleMappings.get(new TypeMapping(source.withIsNullable(false), target.withIsNullable(false))),
                invalid(source, target));
        if (result.isValid() && source.isNullable()) {
            result = new NullableSourceConversion(result);
        }
        return result;
    }

    private Conversion invalid(KiwiType source, KiwiType target) {
        return new InvalidConversion(source, target);
    }

    private Conversion fromSqlArray(SqlArrayType sat, CollectionType ct) {
        var elementConversion = lookup(sat.containedType(), ct.containedType());
        if (!elementConversion.isValid()) {
            return elementConversion;
        }
        return new FromSqlArrayConversion(sat, ct, elementConversion);
    }

    private Conversion toSqlArray(CollectionType ct, SqlArrayType sat) {
        var elementConversion = lookup(ct.containedType().withIsNullable(false), sat.containedType());
        if (!elementConversion.isValid()) {
            return elementConversion;
        }
        return new ToSqlArrayConversion(ct, sat, elementConversion);
    }

    private Conversion firstNonNull(@Nullable Conversion... conversions) {
        for (var c : conversions) {
            if (c != null) {
                return c;
            }
        }
        throw new NullPointerException();
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            var filePath = Path.of(args[0]);
            try {
                Files.createDirectories(filePath.getParent());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try (var bw = Files.newBufferedWriter(
                            Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    var w = new PrintWriter(bw)) {
                w.println(
                        """
                        .Conversions
                        [%autowidth]
                        |===
                        |Source |Target |Warning
                        """);
                var ct = new CoreTypes();
                ct.simpleMappings.entrySet().stream()
                        .map(e -> "|%s |%s |%s"
                                .formatted(
                                        e.getKey().source().className(),
                                        e.getKey().target().className(),
                                        e.getValue().hasWarning() ? e.getValue().warning() : ""))
                        .sorted()
                        .forEach(w::println);
                w.println("|===");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
