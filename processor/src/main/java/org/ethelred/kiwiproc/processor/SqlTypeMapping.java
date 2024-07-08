package org.ethelred.kiwiproc.processor;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record SqlTypeMapping(JDBCType jdbcType, Class<?> baseType, String accessorSuffix, boolean specialCase) {
    private static final List<SqlTypeMapping> types = List.of(
            new SqlTypeMapping(JDBCType.BIT, boolean.class, "Boolean", false),
            new SqlTypeMapping(JDBCType.BIGINT, long.class, "Long", false),
            new SqlTypeMapping(JDBCType.TINYINT, byte.class, "Byte", false),
            new SqlTypeMapping(JDBCType.SMALLINT, short.class, "Short", false),
            new SqlTypeMapping(JDBCType.INTEGER, int.class, "Int", false),
            new SqlTypeMapping(JDBCType.FLOAT, double.class, "Double", false), // postgres treats "FLOAT" and "DOUBLE" the same
            new SqlTypeMapping(JDBCType.REAL, float.class, "Float", false),
            new SqlTypeMapping(JDBCType.DOUBLE, double.class, "Double", false),
            new SqlTypeMapping(JDBCType.NUMERIC, BigDecimal.class, "BigDecimal", false),
            new SqlTypeMapping(JDBCType.DECIMAL, BigDecimal.class, "BigDecimal", false),
            new SqlTypeMapping(JDBCType.CHAR, String.class, "String", false),
            new SqlTypeMapping(JDBCType.VARCHAR, String.class, "String", false),
            new SqlTypeMapping(JDBCType.LONGVARCHAR, String.class, "String", false),
            new SqlTypeMapping(JDBCType.ARRAY, java.sql.Array.class, "Array", true)
            // TODO fill out types as necessary
    );
    private static final Map<JDBCType, SqlTypeMapping> lookup = types.stream().collect(Collectors.toMap(
            SqlTypeMapping::jdbcType,
            t -> t
    ));

    public static SqlTypeMapping get(JDBCType jdbcType) {
        var r = lookup.get(jdbcType);
        if (r == null) {
            throw new IllegalArgumentException("Unsupported type " + jdbcType);
        }
        return r;
    }
}
