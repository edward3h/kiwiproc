/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.gradle;

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
import org.gradle.api.services.BuildService;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;

public abstract class EmbeddedH2Service implements BuildService<EmbeddedH2Params>, AutoCloseable {
    public static final String DEFAULT_NAME = "embeddedH2";

    private Server tcpServer;
    private int dbCounter = 0;
    // Held open to prevent in-memory databases from being dropped between connections
    private final List<Connection> keepAliveConnections = new ArrayList<>();

    public record H2ConnectionInfo(String url) {}

    private synchronized Server getServer() {
        if (tcpServer == null) {
            try {
                tcpServer = Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
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

    @Override
    public void close() {
        for (var conn : keepAliveConnections) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
        if (tcpServer != null) {
            tcpServer.stop();
        }
    }
}
