/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.gradle;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.sql.DriverManager;
import java.sql.SQLException;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.gradle.api.services.BuildService;
import org.testcontainers.containers.MySQLContainer;

public abstract class EmbeddedMySQLService implements BuildService<EmbeddedMySQLParams> {
    public static final String DEFAULT_NAME = "embeddedMySQL";

    private MySQLContainer<?> container;
    private int dbCounter = 0;

    private synchronized MySQLContainer<?> getContainer() {
        if (container == null) {
            container = new MySQLContainer<>("mysql:8.4").withEnv("MYSQL_ROOT_HOST", "%");
            container.start();
        }
        return container;
    }

    public record MySQLConnectionInfo(String url, String username, String password) {}

    // synchronized because Liquibase has issues in multi-threaded
    public synchronized MySQLConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        var c = getContainer();
        var dbName = "kiwi_" + (++dbCounter);
        var adminUrl = "jdbc:mysql://" + c.getHost() + ":" + c.getMappedPort(3306) + "/"
                + "?allowPublicKeyRetrieval=true&useSSL=false";
        try (var conn = DriverManager.getConnection(adminUrl, "root", c.getPassword());
                var stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE `" + dbName + "`");
            stmt.executeUpdate("GRANT ALL PRIVILEGES ON `" + dbName + "`.* TO '" + c.getUsername() + "'@'%'");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        var url = "jdbc:mysql://" + c.getHost() + ":" + c.getMappedPort(3306) + "/" + dbName
                + "?allowPublicKeyRetrieval=true&useSSL=false&user=" + c.getUsername() + "&password="
                + c.getPassword();

        var ds = new MysqlDataSource();
        ds.setURL(url);
        ds.setUser(c.getUsername());
        ds.setPassword(c.getPassword());

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

        return new MySQLConnectionInfo(url, c.getUsername(), c.getPassword());
    }
}
