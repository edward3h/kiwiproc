/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import io.zonky.test.db.postgres.embedded.ConnectionInfo;
import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import io.zonky.test.db.postgres.embedded.PreparedDbProvider;
import java.io.File;
import java.sql.SQLException;

/**
 * Process-wide singleton wrapping an embedded PostgreSQL instance, lazily started and reused for
 * the lifetime of the Maven JVM (i.e. for the whole `mvn` invocation).
 */
public final class EmbeddedPostgresManager {
    private static final EmbeddedPostgresManager INSTANCE = new EmbeddedPostgresManager();

    private PreparedDbProvider dbProvider;

    private EmbeddedPostgresManager() {}

    public static EmbeddedPostgresManager getInstance() {
        return INSTANCE;
    }

    private synchronized PreparedDbProvider getDbProvider() {
        if (dbProvider == null) {
            dbProvider = PreparedDbProvider.forPreparer(ignore -> {});
        }
        return dbProvider;
    }

    // synchronized because Liquibase has issues running concurrently
    public synchronized ConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        try {
            var connectionInfo = getDbProvider().createNewDatabase();
            var dataSource = getDbProvider().createDataSourceFromConnectionInfo(connectionInfo);
            LiquibasePreparer.forFile(liquibaseChangelog).prepare(dataSource);
            return connectionInfo;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
