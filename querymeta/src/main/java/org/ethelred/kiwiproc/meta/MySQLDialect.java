/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.jspecify.annotations.Nullable;

public class MySQLDialect implements DatabaseDialect {
    @Override
    public DataSource createDataSource(DataSourceConfig config) {
        var ds = new MysqlDataSource();
        ds.setURL(config.url());
        if (config.username() != null) {
            ds.setUser(config.username());
        }
        if (config.password() != null) {
            ds.setPassword(config.password());
        }
        return ds;
    }

    @Override
    public @Nullable ArrayComponent componentType(Connection connection, int columnType, String columnTypeName) {
        return null;
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
            // Parameter metadata unavailable; create synthetic entries by counting ? in SQL
            long paramCount = sql.chars().filter(c -> c == '?').count();
            var result = new ArrayList<ColumnMetaData>((int) paramCount);
            for (int index = 1; index <= (int) paramCount; index++) {
                result.add(DatabaseDialect.syntheticParameter(index));
            }
            return result;
        }
    }
}
