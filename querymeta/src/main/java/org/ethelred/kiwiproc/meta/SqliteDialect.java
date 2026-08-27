/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.meta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.jspecify.annotations.Nullable;
import org.sqlite.SQLiteDataSource;

public class SqliteDialect implements DatabaseDialect {
    @Override
    public DataSource createDataSource(DataSourceConfig config) {
        var ds = new SQLiteDataSource();
        ds.setUrl(config.url());
        return ds;
    }

    @Override
    public @Nullable ArrayComponent componentType(Connection connection, int columnType, String columnTypeName) {
        return null;
    }

    @Override
    public List<ColumnMetaData> getResultColumns(Connection connection, ResultSetMetaData resultSetMetaData)
            throws SQLException {
        try {
            return DatabaseDialect.super.getResultColumns(connection, resultSetMetaData);
        } catch (SQLException ignored) {
            // SQLite returns non-null, zero-column metadata for non-SELECT statements instead of
            // null (unlike Postgres/H2/MySQL), and getColumnCount() throws rather than returning 0
            // in that case — treat this the same as no result columns. Mirrors the same
            // try/catch-with-fallback pattern used in getParameters() above for the same driver's
            // unreliable metadata reporting.
            return List.of();
        }
    }

    @Override
    public List<ColumnMetaData> getParameters(Connection connection, PreparedStatement statement, String sql) {
        try {
            var pmd = statement.getParameterMetaData();
            var result = new ArrayList<ColumnMetaData>(pmd.getParameterCount());
            for (int index = 1; index <= pmd.getParameterCount(); index++) {
                try {
                    result.add(ColumnMetaData.from(this, connection, index, pmd));
                } catch (SQLException ignored) {
                    result.add(DatabaseDialect.syntheticParameter(index));
                }
            }
            return result;
        } catch (SQLException ignored) {
            long paramCount = sql.chars().filter(c -> c == '?').count();
            var result = new ArrayList<ColumnMetaData>((int) paramCount);
            for (int index = 1; index <= (int) paramCount; index++) {
                result.add(DatabaseDialect.syntheticParameter(index));
            }
            return result;
        }
    }
}
