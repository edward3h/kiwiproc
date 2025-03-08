/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.meta.DBType;
import org.jspecify.annotations.Nullable;

public class SqlTypeMappingRegistry {

    private static final List<SqlTypeMapping> types = List.of(
            jdbcType(JDBCType.BIT).baseType(boolean.class).build(),
            jdbcType(JDBCType.BOOLEAN).baseType(boolean.class).build(),
            jdbcType(JDBCType.BIGINT).baseType(long.class).build(),
            jdbcType(JDBCType.TINYINT).baseType(byte.class).build(),
            jdbcType(JDBCType.SMALLINT).baseType(short.class).build(),
            jdbcType(JDBCType.INTEGER).baseType(int.class).build(),
            jdbcType(JDBCType.FLOAT).baseType(double.class).build(), // postgres treats "FLOAT" and "DOUBLE" the same
            jdbcType(JDBCType.REAL).baseType(float.class).build(),
            jdbcType(JDBCType.DOUBLE).baseType(double.class).build(),
            jdbcType(JDBCType.NUMERIC)
                    .baseType(BigDecimal.class)
                    .accessorSuffix("BigDecimal")
                    .build(),
            jdbcType(JDBCType.DECIMAL)
                    .baseType(BigDecimal.class)
                    .accessorSuffix("BigDecimal")
                    .build(),
            jdbcType(JDBCType.CHAR)
                    .baseType(String.class)
                    .accessorSuffix("String")
                    .build(),
            jdbcType(JDBCType.VARCHAR)
                    .baseType(String.class)
                    .accessorSuffix("String")
                    .build(),
            jdbcType(JDBCType.LONGVARCHAR)
                    .baseType(String.class)
                    .accessorSuffix("String")
                    .build(),
            jdbcType(JDBCType.NCHAR)
                    .baseType(String.class)
                    .accessorSuffix("String")
                    .build(),
            jdbcType(JDBCType.NVARCHAR)
                    .baseType(String.class)
                    .accessorSuffix("String")
                    .build(),
            jdbcType(JDBCType.LONGNVARCHAR)
                    .baseType(String.class)
                    .accessorSuffix("String")
                    .build(),
            new SqlTypeMapping(JDBCType.ARRAY, java.sql.Array.class, "Array", true, false, null, null, null),
            // dates and times
            // Use java.time types - recommended for Postgres https://tada.github.io/pljava/use/datetime.html
            // https://jdbc.postgresql.org/documentation/query/#using-java-8-date-and-time-classes
            jdbcType(JDBCType.DATE).baseType(LocalDate.class).build(),
            jdbcType(JDBCType.TIME).baseType(LocalTime.class).build(),
            jdbcType(JDBCType.TIME_WITH_TIMEZONE)
                    .baseType(OffsetTime.class)
                    .dbType("timetz")
                    .build(),
            jdbcType(JDBCType.TIMESTAMP).baseType(LocalDateTime.class).build(),
            jdbcType(JDBCType.TIMESTAMP_WITH_TIMEZONE)
                    .baseType(OffsetDateTime.class)
                    .dbType("timestamptz")
                    .build(),
            new SqlTypeMapping(JDBCType.NULL, void.class, "", true, true, null, null, null)

            // TODO fill out types as necessary
            );

    /**
     * Shortcut to reduce the length of builder chain.
     * @param jdbcType
     * @return
     */
    private static SqlTypeMappingBuilder jdbcType(JDBCType jdbcType) {
        return SqlTypeMappingBuilder.builder().jdbcType(jdbcType);
    }

    private static final Map<JDBCType, SqlTypeMapping> JDBC_TYPE_SQL_TYPE_MAPPING_MAP =
            types.stream().collect(Collectors.toMap(SqlTypeMapping::jdbcType, t -> t));
    private static final Map<String, SqlTypeMapping> DB_TYPE_SQL_TYPE_MAPPING =
            types.stream().filter(t -> t.dbType() != null).collect(Collectors.toMap(SqlTypeMapping::dbType, t -> t));

    private static @Nullable SqlTypeMapping lookup(DBType type) {
        // prefer dbType mapping if present
        var r = DB_TYPE_SQL_TYPE_MAPPING.get(type.dbType());
        if (r == null) {
            r = JDBC_TYPE_SQL_TYPE_MAPPING_MAP.get(type.jdbcType());
        }
        return r;
    }

    public static SqlTypeMapping get(ColumnMetaData columnMetaData) {
        var r = lookup(columnMetaData);
        if (r == null) {
            throw new IllegalArgumentException("Unsupported JDBCType type " + columnMetaData.jdbcType());
        }
        if (r.jdbcType() == JDBCType.ARRAY) {
            if (columnMetaData.componentType() == null) {
                throw new IllegalArgumentException("No component type provided for SQL Array");
            }
            var component = lookup(columnMetaData.componentType());
            if (component == null) {
                throw new IllegalArgumentException("No component type found for SQL Array");
            }
            r = r.withComponentType(component)
                    .withComponentDbType(columnMetaData.componentType().dbType());
        }
        return r.withIsNullable(columnMetaData.nullable());
    }
}
