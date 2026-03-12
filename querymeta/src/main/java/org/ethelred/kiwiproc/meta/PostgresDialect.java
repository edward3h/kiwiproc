/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.jspecify.annotations.Nullable;
import org.postgresql.core.BaseConnection;
import org.postgresql.ds.PGSimpleDataSource;

public class PostgresDialect implements DatabaseDialect {
    @Override
    public DataSource createDataSource(DataSourceConfig config) {
        var ds = new PGSimpleDataSource();
        ds.setURL(config.url());
        if (config.database() != null) {
            ds.setDatabaseName(config.database());
        }
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
}
