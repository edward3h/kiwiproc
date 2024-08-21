package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilderFull;
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.meta.ColumnMetaData;

@RecordBuilderFull
public record SqlTypeMapping(
        JDBCType jdbcType,
        Class<?> baseType,
        // empty accessorSuffix indicates to use getObject/setObject
        String accessorSuffix,
        boolean specialCase,
        boolean isNullable)
        implements SqlTypeMappingBuilder.With {
    public SqlTypeMapping(JDBCType jdbcType, Class<?> baseType, String accessorSuffix) {
        this(jdbcType, baseType, accessorSuffix, false, false);
    }

    private static final List<SqlTypeMapping> types = List.of(
            new SqlTypeMapping(JDBCType.BIT, boolean.class, "Boolean"),
            new SqlTypeMapping(JDBCType.BOOLEAN, boolean.class, "Boolean"),
            new SqlTypeMapping(JDBCType.BIGINT, long.class, "Long"),
            new SqlTypeMapping(JDBCType.TINYINT, byte.class, "Byte"),
            new SqlTypeMapping(JDBCType.SMALLINT, short.class, "Short"),
            new SqlTypeMapping(JDBCType.INTEGER, int.class, "Int"),
            new SqlTypeMapping(JDBCType.FLOAT, double.class, "Double"), // postgres treats "FLOAT" and "DOUBLE" the same
            new SqlTypeMapping(JDBCType.REAL, float.class, "Float"),
            new SqlTypeMapping(JDBCType.DOUBLE, double.class, "Double"),
            new SqlTypeMapping(JDBCType.NUMERIC, BigDecimal.class, "BigDecimal"),
            new SqlTypeMapping(JDBCType.DECIMAL, BigDecimal.class, "BigDecimal"),
            new SqlTypeMapping(JDBCType.CHAR, String.class, "String"),
            new SqlTypeMapping(JDBCType.VARCHAR, String.class, "String"),
            new SqlTypeMapping(JDBCType.LONGVARCHAR, String.class, "String"),
            new SqlTypeMapping(JDBCType.NCHAR, String.class, "String"),
            new SqlTypeMapping(JDBCType.NVARCHAR, String.class, "String"),
            new SqlTypeMapping(JDBCType.LONGNVARCHAR, String.class, "String"),
            new SqlTypeMapping(JDBCType.ARRAY, java.sql.Array.class, "Array", true, false),
            // dates and times
            // Use java.time types - recommended for Postgres https://tada.github.io/pljava/use/datetime.html
            new SqlTypeMapping(JDBCType.DATE, LocalDate.class, ""),
            new SqlTypeMapping(JDBCType.TIME, LocalTime.class, ""),
            new SqlTypeMapping(JDBCType.TIME_WITH_TIMEZONE, OffsetTime.class, ""),
            new SqlTypeMapping(JDBCType.TIMESTAMP, LocalDateTime.class, ""),
            new SqlTypeMapping(JDBCType.TIMESTAMP_WITH_TIMEZONE, OffsetDateTime.class, ""),
            new SqlTypeMapping(JDBCType.NULL, void.class, "", true, true)

            // TODO fill out types as necessary
            );
    private static final Map<JDBCType, SqlTypeMapping> lookup =
            types.stream().collect(Collectors.toMap(SqlTypeMapping::jdbcType, t -> t));

    public static SqlTypeMapping get(ColumnMetaData columnMetaData) {
        var r = lookup.get(columnMetaData.sqlType());
        if (r == null) {
            throw new IllegalArgumentException("Unsupported JDBCType type " + columnMetaData.sqlType());
        }
        return r.withIsNullable(columnMetaData.nullable());
    }

    public KiwiType kiwiType() {
        // TODO fix array type handling
        //        if (jdbcType == JDBCType.ARRAY && columnMetaData.componentType() != null) {
        //            var componentSql = get(columnMetaData.componentType());
        //            var containedClass = componentSql.baseType;
        //            return ContainerTypeBuilder.builder()
        //                    .type(ValidContainerType.ARRAY)
        //                    .containedType(SimpleTypeBuilder.builder()
        //                            .className(containedClass.getName())
        //                            .isNullable(columnMetaData.nullable())
        //                            .build())
        //                    .build();
        //        }
        var resolvedType = baseType;
        if (isNullable) {
            var maybeBoxed = CoreTypes.primitiveToBoxed.getByA(baseType);
            if (maybeBoxed.isPresent()) {
                resolvedType = maybeBoxed.get();
            }
        }
        return SimpleType.ofClass(resolvedType, isNullable);
    }
}
