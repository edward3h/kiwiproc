/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

import java.sql.*;
import org.jspecify.annotations.Nullable;

public record ColumnMetaData(
        int index,
        boolean isParameter,
        SqlName name,
        JDBCNullable nullable,
        JDBCType jdbcType,
        String dbType,
        @Nullable String dbClassName,
        @Nullable ArrayComponent componentType
        // TODO precision/scale?
        ) implements DBType {

    public ColumnMetaData(
            int index,
            boolean isParameter,
            String name,
            JDBCNullable nullable,
            JDBCType sqlType,
            String dbTypeName,
            @Nullable String dbClassName,
            @Nullable ArrayComponent componentType) {
        this(index, isParameter, new SqlName(name), nullable, sqlType, dbTypeName, dbClassName, componentType);
    }

    public static ColumnMetaData from(
            DatabaseDialect dialect, Connection connection, int index, ResultSetMetaData resultSetMetaData)
            throws SQLException {
        return new ColumnMetaData(
                index,
                false,
                new SqlName(resultSetMetaData.getColumnName(index)),
                JDBCNullable.fromCode(resultSetMetaData.isNullable(index)),
                JDBCType.valueOf(resultSetMetaData.getColumnType(index)),
                resultSetMetaData.getColumnTypeName(index),
                resultSetMetaData.getColumnClassName(index),
                dialect.componentType(
                        connection,
                        resultSetMetaData.getColumnType(index),
                        resultSetMetaData.getColumnTypeName(index)));
    }

    public static ColumnMetaData from(
            DatabaseDialect dialect, Connection connection, int index, ParameterMetaData parameterMetaData)
            throws SQLException {
        return new ColumnMetaData(
                index,
                true,
                SqlName.PARAMETER, // does not have a name in metadata, will be associated by index outside this scope
                JDBCNullable.fromCode(
                        parameterMetaData.isNullable(index)), // For Postgres, this is always 'unknown' for parameters
                JDBCType.valueOf(parameterMetaData.getParameterType(index)),
                parameterMetaData.getParameterTypeName(index),
                parameterMetaData.getParameterClassName(index),
                dialect.componentType(
                        connection,
                        parameterMetaData.getParameterType(index),
                        parameterMetaData.getParameterTypeName(index)));
    }

    public boolean treatAsNullable() {
        return switch (nullable) {
            case NOT_NULL -> false;
            case NULLABLE -> true;
            case UNKNOWN -> isParameter;
        };
    }
}
