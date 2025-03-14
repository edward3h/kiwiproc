package org.ethelred.buildsupport;

import io.zonky.test.db.postgres.embedded.ConnectionInfo;
import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import io.zonky.test.db.postgres.embedded.PreparedDbProvider;
import org.gradle.api.services.BuildService;

import java.io.File;
import java.sql.SQLException;

public abstract class EmbeddedPostgresService implements BuildService<EmbeddedPostgresParams> {
    PreparedDbProvider dbProvider;

    private synchronized PreparedDbProvider getDbProvider() {
        if (dbProvider == null) {
            dbProvider = PreparedDbProvider.forPreparer(ignore -> {});
        }
        return dbProvider;
    }

    // synchronized because Liquibase has issues in multi-threaded
    public synchronized ConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        try {
            var connectionInfo = getDbProvider().createNewDatabase();
            var dataSource = getDbProvider().createDataSourceFromConnectionInfo(connectionInfo);
            var liquibasePreparer = LiquibasePreparer.forFile(liquibaseChangelog);
            liquibasePreparer.prepare(dataSource);
            return connectionInfo;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
