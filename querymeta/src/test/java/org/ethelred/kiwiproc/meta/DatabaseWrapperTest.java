package org.ethelred.kiwiproc.meta;

import static com.google.common.truth.Truth.assertThat;

import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import io.zonky.test.db.postgres.junit5.EmbeddedPostgresExtension;
import io.zonky.test.db.postgres.junit5.PreparedDbExtension;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.ArrayList;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DatabaseWrapperTest {
    @RegisterExtension
    public static PreparedDbExtension pg =
            EmbeddedPostgresExtension.preparedDatabase(LiquibasePreparer.forClasspathLocation("changelog.xml"));

    @Test
    public void validConnection() throws SQLException {
        var ci = pg.getConnectionInfo();
        var config = new DataSourceConfig(
                "test",
                "jdbc:postgresql://localhost:%d/%s?user=%s".formatted(ci.getPort(), ci.getDbName(), ci.getUser()),
                ci.getDbName(),
                ci.getUser(),
                "postgres",
                "org.postgresql.Driver");
        var wrapper = new DatabaseWrapper("test", config);
        wrapper.isValid(); // triggers connection test
        assertThat(wrapper.getError()).isNull();
        assertThat(wrapper.isValid()).isTrue();
        try (var connection = Assertions.assertDoesNotThrow(wrapper::getConnection)) {
            var md = connection.getMetaData();
            var tablesRS = md.getTables(null, null, null, null);
            var tables = new ArrayList<String>();
            while (tablesRS.next()) {
                tables.add(tablesRS.getString(3)); // TABLE_NAME
            }
            assertThat(tables).contains("test_table");
        }
    }

    @Test
    public void simpleQueryMetaData() throws SQLException {
        var queryMetaData = getQueryMetaData("SELECT * FROM test_table");
        assertThat(queryMetaData.parameters()).isEmpty();
        assertThat(queryMetaData.resultColumns()).hasSize(4);
        assertThat(queryMetaData.resultColumns().get(0))
                .isEqualTo(new ColumnMetaData(1, "test_id", false, JDBCType.INTEGER, null));
        assertThat(queryMetaData.resultColumns().get(1))
                .isEqualTo(new ColumnMetaData(2, "notes", true, JDBCType.VARCHAR, null));
        assertThat(queryMetaData.resultColumns().get(2))
                .isEqualTo(new ColumnMetaData(3, "something", true, JDBCType.OTHER, null));
        assertThat(queryMetaData.resultColumns().get(3))
                .isEqualTo(new ColumnMetaData(4, "large", true, JDBCType.BIGINT, null));
    }

    @Test
    public void simpleQueryWithParameterMetaData() throws SQLException {
        var queryMetaData = getQueryMetaData("SELECT * FROM test_table where test_id = ?");
        assertThat(queryMetaData.parameters()).hasSize(1);
        assertThat(queryMetaData.parameters().get(0))
                .isEqualTo(new ColumnMetaData(1, "parameter", false, JDBCType.INTEGER, null));
        assertThat(queryMetaData.resultColumns()).hasSize(4);
        assertThat(queryMetaData.resultColumns().get(0))
                .isEqualTo(new ColumnMetaData(1, "test_id", false, JDBCType.INTEGER, null));
    }

    @Test
    public void simpleQueryArrayParameterMetaData() throws SQLException {
        var queryMetaData = getQueryMetaData("SELECT * FROM test_table where test_id = ANY(?)");
        assertThat(queryMetaData.parameters()).hasSize(1);
        assertThat(queryMetaData.parameters().get(0))
                .isEqualTo(new ColumnMetaData(
                        1, "parameter", false, JDBCType.ARRAY, new ArrayComponent(JDBCType.INTEGER, "_int4")));
        assertThat(queryMetaData.resultColumns()).hasSize(4);
        assertThat(queryMetaData.resultColumns().get(0))
                .isEqualTo(new ColumnMetaData(1, "test_id", false, JDBCType.INTEGER, null));
    }

    private QueryMetaData getQueryMetaData(String sql) throws SQLException {
        var ci = pg.getConnectionInfo();
        var config = new DataSourceConfig(
                "test",
                "jdbc:postgresql://localhost:%d/%s?user=%s".formatted(ci.getPort(), ci.getDbName(), ci.getUser()),
                ci.getDbName(),
                ci.getUser(),
                "postgres",
                "org.postgresql.Driver");
        var wrapper = new DatabaseWrapper("test", config);

        return wrapper.getQueryMetaData(sql);
    }
}
