/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.meta;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.junit.jupiter.api.Test;

class SqliteDatabaseWrapperTest {

    // Exercises the same code path @SqlBatch-annotated methods hit: DatabaseWrapper.getQueryMetaData
    // calls prepareStatement(sql) + getMetaData() once per SQL text regardless of whether the
    // generated code later calls executeUpdate() or addBatch()/executeBatch() — batching only
    // affects execution, not statement preparation/metadata retrieval.
    @Test
    void updateStatementHasNoResultColumns() throws SQLException, IOException {
        var wrapper = getDatabaseWrapper();
        try (var connection = wrapper.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE widget (id INTEGER PRIMARY KEY, name TEXT)");
        }

        var queryMetaData = wrapper.getQueryMetaData("INSERT INTO widget (name) VALUES (?)");

        assertThat(queryMetaData.resultColumns()).isEmpty();
        assertThat(queryMetaData.parameters()).hasSize(1);
    }

    @Test
    void selectStatementHasResultColumns() throws SQLException, IOException {
        var wrapper = getDatabaseWrapper();
        try (var connection = wrapper.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE widget (id INTEGER PRIMARY KEY, name TEXT)");
        }

        var queryMetaData = wrapper.getQueryMetaData("SELECT id, name FROM widget");

        assertThat(queryMetaData.resultColumns()).hasSize(2);
    }

    private DatabaseWrapper getDatabaseWrapper() throws IOException {
        var dbFile = Files.createTempFile("kiwiproc-sqlite-wrapper-test-", ".db");
        Files.deleteIfExists(dbFile);
        var url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        var config = new DataSourceConfig("test", url, null, null, null, "org.sqlite.JDBC");
        return new DatabaseWrapper("test", config);
    }
}
