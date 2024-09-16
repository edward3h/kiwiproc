package org.ethelred.kiwiproc.processor;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.BasicType;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.ethelred.kiwiproc.processor.types.SqlArrayType;
import org.jspecify.annotations.Nullable;

@KiwiRecordBuilder
public record SqlTypeMapping(
        JDBCType jdbcType,
        Class<?> baseType,
        // empty accessorSuffix indicates to use getObject/setObject
        String accessorSuffix,
        boolean specialCase,
        boolean isNullable,
        @Nullable SqlTypeMapping componentType,
        @Nullable String componentDbType)
        implements SqlTypeMappingBuilder.With {
    public SqlTypeMapping(JDBCType jdbcType, Class<?> baseType, String accessorSuffix) {
        this(jdbcType, baseType, accessorSuffix, false, false, null, null);
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
            new SqlTypeMapping(JDBCType.ARRAY, java.sql.Array.class, "Array", true, false, null, null),
            // dates and times
            // Use java.time types - recommended for Postgres https://tada.github.io/pljava/use/datetime.html
            new SqlTypeMapping(JDBCType.DATE, LocalDate.class, ""),
            new SqlTypeMapping(JDBCType.TIME, LocalTime.class, ""),
            new SqlTypeMapping(JDBCType.TIME_WITH_TIMEZONE, OffsetTime.class, ""),
            new SqlTypeMapping(JDBCType.TIMESTAMP, LocalDateTime.class, ""),
            new SqlTypeMapping(JDBCType.TIMESTAMP_WITH_TIMEZONE, OffsetDateTime.class, ""),
            new SqlTypeMapping(JDBCType.NULL, void.class, "", true, true, null, null)

            // TODO fill out types as necessary
            );
    private static final Map<JDBCType, SqlTypeMapping> JDBC_TYPE_SQL_TYPE_MAPPING_MAP =
            types.stream().collect(Collectors.toMap(SqlTypeMapping::jdbcType, t -> t));

    public static SqlTypeMapping get(ColumnMetaData columnMetaData) {
        var r = JDBC_TYPE_SQL_TYPE_MAPPING_MAP.get(columnMetaData.sqlType());
        if (r == null) {
            throw new IllegalArgumentException("Unsupported JDBCType type " + columnMetaData.sqlType());
        }
        if (r.jdbcType == JDBCType.ARRAY) {
            if (columnMetaData.componentType() == null) {
                throw new IllegalArgumentException("No component type provided for SQL Array");
            }
            var component = JDBC_TYPE_SQL_TYPE_MAPPING_MAP.get(
                    columnMetaData.componentType().jdbcType());
            if (component == null) {
                throw new IllegalArgumentException("No component type found for SQL Array");
            }
            r = r.withComponentType(component);
        }
        return r.withIsNullable(columnMetaData.nullable());
    }

    public KiwiType kiwiType() {
        if (jdbcType == JDBCType.ARRAY) {
            assert componentType != null;
            return new SqlArrayType(componentType.kiwiType(), componentType.jdbcType, componentDbType);
        }
        if (CoreTypes.primitiveToBoxed.containsKey(baseType)) {
            return new PrimitiveKiwiType(baseType().getSimpleName(), isNullable);
        }
        return new BasicType(baseType.getPackageName(), baseType.getSimpleName(), isNullable);
    }
}
