/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

import java.sql.*;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.postgresql.core.BaseConnection;

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

    public static ColumnMetaData from(Connection connection, int index, ResultSetMetaData resultSetMetaData)
            throws SQLException {
        return new ColumnMetaData(
                index,
                false,
                new SqlName(resultSetMetaData.getColumnName(index)),
                JDBCNullable.fromCode(resultSetMetaData.isNullable(index)),
                JDBCType.valueOf(resultSetMetaData.getColumnType(index)),
                resultSetMetaData.getColumnTypeName(index),
                resultSetMetaData.getColumnClassName(index),
                componentType(
                        connection,
                        resultSetMetaData.getColumnType(index),
                        resultSetMetaData.getColumnTypeName(index)));
    }

    @Nullable private static ArrayComponent componentType(Connection connection, int columnType, String columnTypeName) {
        if (columnType != Types.ARRAY) {
            return null;
        }
        try {
            var pgConnection = connection.unwrap(BaseConnection.class);
            var typeInfo = pgConnection.getTypeInfo();
            var oid = typeInfo.getPGType(columnTypeName);
            var componentOid = typeInfo.getPGArrayElement(oid);
            return new ArrayComponent(
                    JDBCType.valueOf(typeInfo.getSQLType(componentOid)),
                    Objects.requireNonNull(typeInfo.getPGType(componentOid)));
        } catch (SQLException ignored) {

        }
        return null;
    }

    public static ColumnMetaData from(Connection connection, int index, ParameterMetaData parameterMetaData)
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
                componentType(
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
