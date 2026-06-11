/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;

/**
 * Process-wide singleton wrapping an embedded H2 instance (in PostgreSQL compatibility mode),
 * lazily started and reused for the lifetime of the Maven JVM (i.e. for the whole `mvn`
 * invocation).
 */
public final class EmbeddedH2Manager {
    private static final EmbeddedH2Manager INSTANCE = new EmbeddedH2Manager();

    private Server tcpServer;
    private int dbCounter = 0;
    // Held open to prevent in-memory databases from being dropped between connections
    private final List<Connection> keepAliveConnections = new ArrayList<>();

    public record H2ConnectionInfo(String url) {}

    private EmbeddedH2Manager() {}

    public static EmbeddedH2Manager getInstance() {
        return INSTANCE;
    }

    private synchronized Server getServer() {
        if (tcpServer == null) {
            try {
                tcpServer =
                        Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return tcpServer;
    }

    public synchronized H2ConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        var server = getServer();
        var dbName = "kiwi_" + (++dbCounter);
        var url = "jdbc:h2:tcp://localhost:" + server.getPort() + "/mem:" + dbName + ";MODE=PostgreSQL";

        var ds = new JdbcDataSource();
        ds.setURL(url);

        try {
            keepAliveConnections.add(ds.getConnection());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        try (var connection = ds.getConnection()) {
            var liquibaseConnection = new JdbcConnection(connection);
            var liquibase = new Liquibase(
                    liquibaseChangelog.getName(),
                    new DirectoryResourceAccessor(liquibaseChangelog.getParentFile()),
                    liquibaseConnection);
            liquibase.update();
        } catch (SQLException | FileNotFoundException | LiquibaseException e) {
            throw new RuntimeException(e);
        }

        return new H2ConnectionInfo(url);
    }
}
