/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmbeddedH2ManagerTest {
    @Test
    void preparesDatabaseAndAppliesLiquibase() throws IOException {
        var changelog = writeChangelog();

        var info = EmbeddedH2Manager.getInstance().getPreparedDatabase(changelog.toFile());

        assertThat(info.url()).startsWith("jdbc:h2:tcp://localhost:");
        assertThat(info.url()).contains("MODE=PostgreSQL");
    }

    private Path writeChangelog() throws IOException {
        var dir = Files.createTempDirectory("kiwiproc-maven-test-h2");
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
