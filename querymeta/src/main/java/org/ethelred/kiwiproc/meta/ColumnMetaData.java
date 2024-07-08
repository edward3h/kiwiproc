package org.ethelred.kiwiproc.meta;

import org.jspecify.annotations.Nullable;
import org.postgresql.core.BaseConnection;

import java.sql.*;

public record ColumnMetaData(
        int index,
        String name,
        boolean nullable,
        JDBCType sqlType,
        String dbType,
        String javaType,
        @Nullable JDBCType componentType
        // TODO precision/scale?
) {
    public static ColumnMetaData from(Connection connection, int index, ResultSetMetaData resultSetMetaData) throws SQLException {
        return new ColumnMetaData(
                index,
                resultSetMetaData.getColumnName(index),
                resultSetMetaData.isNullable(index) != ResultSetMetaData.columnNoNulls, // for results, treat 'unknown' as 'nullable' since caller may need to handle null case
                JDBCType.valueOf(resultSetMetaData.getColumnType(index)),
                resultSetMetaData.getColumnTypeName(index),
                resultSetMetaData.getColumnClassName(index),
                componentType(connection, resultSetMetaData.getColumnType(index), resultSetMetaData.getColumnTypeName(index))
        );
    }

    @Nullable
    private static JDBCType componentType(Connection connection, int columnType, String columnTypeName) {
        if (columnType != Types.ARRAY) {
            return null;
        }
        try {
            var pgConnection = connection.unwrap(BaseConnection.class);
            var typeInfo = pgConnection.getTypeInfo();
            var oid = typeInfo.getPGType(columnTypeName);
            var componentOid = typeInfo.getPGArrayElement(oid);
            return JDBCType.valueOf(typeInfo.getSQLType(componentOid));
        } catch (SQLException ignored) {

        }
        return null;
    }

    public static ColumnMetaData from(Connection connection, int index, ParameterMetaData parameterMetaData) throws SQLException {
        return new ColumnMetaData(
                index,
                "parameter", // does not have a name in metadata, will be associated by index outside this scope
                parameterMetaData.isNullable(index) == ParameterMetaData.parameterNullable, // for parameters, treat 'unknown' as 'not null' since DB might not accept a null
                JDBCType.valueOf(parameterMetaData.getParameterType(index)),
                parameterMetaData.getParameterTypeName(index),
                parameterMetaData.getParameterClassName(index),
                componentType(connection,parameterMetaData.getParameterType(index), parameterMetaData.getParameterTypeName(index))
        );
    }

}
