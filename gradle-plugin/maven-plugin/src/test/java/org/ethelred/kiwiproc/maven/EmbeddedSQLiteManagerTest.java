/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class EmbeddedSQLiteManagerTest {
    @Test
    void preparesDatabaseAndAppliesLiquibase() throws IOException, SQLException {
        var changelog = writeChangelog();

        var info = EmbeddedSQLiteManager.getInstance().getPreparedDatabase(changelog.toFile());

        assertThat(info.url()).startsWith("jdbc:sqlite:");
        try (var connection = DriverManager.getConnection(info.url())) {
            var tables = connection.getMetaData().getTables(null, null, "widget", null);
            assertThat(tables.next()).isTrue();
        }
    }

    private Path writeChangelog() throws IOException {
        var dir = Files.createTempDirectory("kiwiproc-maven-test-sqlite");
        var changelog = dir.resolve("changelog.xml");
        Files.writeString(changelog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">
                    <changeSet id="1" author="test">
                        <createTable tableName="widget">
                            <column name="id" type="int"/>
                        </createTable>
                    </changeSet>
                </databaseChangeLog>
                """);
        return changelog;
    }
}
