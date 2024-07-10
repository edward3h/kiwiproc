package org.ethelred.kiwiproc.meta;

import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.jspecify.annotations.Nullable;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseWrapper {
    @Nullable private Boolean valid;
    private DatabaseWrapperException error;
    private DataSource dataSource;

    public DatabaseWrapper(String name, @Nullable DataSourceConfig dataSourceConfig) {
        if (dataSourceConfig == null) {
            valid = false;
            error = new DatabaseWrapperException("No config found for data source name %s".formatted(name));
        } else if(invalidDriver(dataSourceConfig.driverClassName())) {
            valid = false;
            error = new DatabaseWrapperException("Sorry, I only support Postgres at the moment.");
        } else {
            var pgSimpleDataSource = new PGSimpleDataSource();
            pgSimpleDataSource.setDatabaseName(dataSourceConfig.database());
            pgSimpleDataSource.setURL(dataSourceConfig.url());
            pgSimpleDataSource.setUser(dataSourceConfig.username());
            pgSimpleDataSource.setPassword(dataSourceConfig.password());
            dataSource = pgSimpleDataSource;
        }
    }

    private boolean invalidDriver(@Nullable String driverClassName) {
        return driverClassName != null && !driverClassName.isBlank() && !"org.postgresql.Driver".equals(driverClassName);
    }

    public boolean isValid() {
        if (valid == null) {
            testConnection();
        }
        return valid;
    }

    public DatabaseWrapperException getError()
    {
        return error;
    }

    /* package visible for testing */ Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public QueryMetaData getQueryMetaData(String sql) throws SQLException {
        System.err.printf("getQueryMetaData(%s)%n", sql);
        try (var connection = getConnection(); var statement = connection.prepareStatement(sql)) {
            var builder = QueryMetaDataBuilder.builder();
            var rsmd = statement.getMetaData();
            for (var index = 1; index <= rsmd.getColumnCount(); index++) {
                builder.addResultColumns(ColumnMetaData.from(connection, index, rsmd));
            }
            var pmd = statement.getParameterMetaData();
            for (var index = 1; index <= pmd.getParameterCount(); index++) {
                builder.addParameters(ColumnMetaData.from(connection, index, pmd));
            }
            return builder.build();
        }
    }

    private void testConnection() {
        try (var connection = getConnection(); var st = connection.prepareStatement("SELECT 1 = 1"); var rs = st.executeQuery()) {
            valid = rs.next();
        } catch (SQLException e) {
            valid = false;
            error = new DatabaseWrapperException("Test database connection failed", e);
        }
    }

    public static class DatabaseWrapperException extends Exception {
        public DatabaseWrapperException(String message) {
            super(message);
        }

        public DatabaseWrapperException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
