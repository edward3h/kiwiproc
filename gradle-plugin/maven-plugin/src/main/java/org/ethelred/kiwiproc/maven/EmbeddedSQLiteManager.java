/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.sqlite.SQLiteDataSource;

/**
 * Process-wide singleton that creates a fresh temp SQLite database file per requested datasource
 * and applies its Liquibase changelog. SQLite is file-based, so unlike {@link EmbeddedH2Manager}
 * there is no shared server process or keep-alive connection to manage — any later connection to
 * the same file sees the same data.
 */
public final class EmbeddedSQLiteManager {
    private static final EmbeddedSQLiteManager INSTANCE = new EmbeddedSQLiteManager();

    private int dbCounter = 0;

    public record SQLiteConnectionInfo(String url) {}

    private EmbeddedSQLiteManager() {}

    public static EmbeddedSQLiteManager getInstance() {
        return INSTANCE;
    }

    public synchronized SQLiteConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        try {
            Path dbFile = Files.createTempFile("kiwiproc-maven-sqlite-" + (++dbCounter) + "-", ".db");
            Files.deleteIfExists(dbFile);
            dbFile.toFile().deleteOnExit();
            var url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

            var ds = new SQLiteDataSource();
            ds.setUrl(url);

            try (var connection = ds.getConnection()) {
                var liquibaseConnection = new JdbcConnection(connection);
                var liquibase = new Liquibase(
                        liquibaseChangelog.getName(),
                        new DirectoryResourceAccessor(liquibaseChangelog.getParentFile()),
                        liquibaseConnection);
                liquibase.update();
            }

            return new SQLiteConnectionInfo(url);
        } catch (IOException | SQLException | LiquibaseException e) {
            throw new RuntimeException(e);
        }
    }
}
