/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.gradle.api.services.BuildService;
import org.sqlite.SQLiteDataSource;

public abstract class EmbeddedSQLiteService implements BuildService<EmbeddedSQLiteParams>, AutoCloseable {
    public static final String DEFAULT_NAME = "embeddedSQLite";

    private int dbCounter = 0;
    private final List<Path> createdDatabaseFiles = new ArrayList<>();

    public record SQLiteConnectionInfo(String url) {}

    public synchronized SQLiteConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        try {
            var dbFile = Files.createTempFile("kiwiproc-sqlite-" + (++dbCounter) + "-", ".db");
            // SQLite creates the file itself on first connection; an empty placeholder confuses it.
            Files.deleteIfExists(dbFile);
            createdDatabaseFiles.add(dbFile);
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

    @Override
    public void close() {
        for (var file : createdDatabaseFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }
}
