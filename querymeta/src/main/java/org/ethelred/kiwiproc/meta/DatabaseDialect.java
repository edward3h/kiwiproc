/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.jspecify.annotations.Nullable;

public interface DatabaseDialect {
    DataSource createDataSource(DataSourceConfig config);

    @Nullable ArrayComponent componentType(Connection connection, int columnType, String columnTypeName);

    default List<String> queryEnumConstants(Connection connection, String typeName) throws SQLException {
        return List.of();
    }

    default String normalizeColumnName(String name) {
        return name;
    }

    default List<ColumnMetaData> getParameters(Connection connection, PreparedStatement statement, String sql)
            throws SQLException {
        var pmd = statement.getParameterMetaData();
        var result = new ArrayList<ColumnMetaData>(pmd.getParameterCount());
        for (int index = 1; index <= pmd.getParameterCount(); index++) {
            result.add(ColumnMetaData.from(this, connection, index, pmd));
        }
        return result;
    }

    default List<ColumnMetaData> getResultColumns(Connection connection, ResultSetMetaData resultSetMetaData)
            throws SQLException {
        var result = new ArrayList<ColumnMetaData>(resultSetMetaData.getColumnCount());
        for (var index = 1; index <= resultSetMetaData.getColumnCount(); index++) {
            result.add(ColumnMetaData.from(this, connection, index, resultSetMetaData));
        }
        return result;
    }

    static ColumnMetaData syntheticParameter(int index) {
        return new ColumnMetaData(
                index, true, SqlName.PARAMETER, JDBCNullable.UNKNOWN, JDBCType.OTHER, "UNKNOWN", null, null);
    }
}
