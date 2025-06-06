/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

import static com.google.common.truth.Truth.assertThat;

import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import io.zonky.test.db.postgres.junit5.EmbeddedPostgresExtension;
import io.zonky.test.db.postgres.junit5.PreparedDbExtension;
import java.sql.JDBCType;
import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.Types;
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
        var wrapper = getDatabaseWrapper();
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

    private static DatabaseWrapper getDatabaseWrapper() {
        var ci = pg.getConnectionInfo();
        var config = new DataSourceConfig(
                "test",
                "jdbc:postgresql://localhost:%d/%s?user=%s".formatted(ci.getPort(), ci.getDbName(), ci.getUser()),
                ci.getDbName(),
                ci.getUser(),
                "postgres",
                "org.postgresql.Driver");
        return new DatabaseWrapper("test", config);
    }

    @Test
    public void simpleQueryMetaData() throws SQLException {
        var queryMetaData = getQueryMetaData("SELECT * FROM test_table");
        assertThat(queryMetaData.parameters()).isEmpty();
        assertThat(queryMetaData.resultColumns()).hasSize(4);
        assertThat(queryMetaData.resultColumns().get(0))
                .isEqualTo(new ColumnMetaData(
                        1,
                        false,
                        "test_id",
                        JDBCNullable.NOT_NULL,
                        JDBCType.INTEGER,
                        "int4",
                        "java.lang.Integer",
                        null));
        assertThat(queryMetaData.resultColumns().get(1))
                .isEqualTo(new ColumnMetaData(
                        2, false, "notes", JDBCNullable.NULLABLE, JDBCType.VARCHAR, "text", "java.lang.String", null));
        assertThat(queryMetaData.resultColumns().get(2))
                .isEqualTo(new ColumnMetaData(
                        3,
                        false,
                        "something",
                        JDBCNullable.NULLABLE,
                        JDBCType.OTHER,
                        "jsonb",
                        "java.lang.String",
                        null));
        assertThat(queryMetaData.resultColumns().get(3))
                .isEqualTo(new ColumnMetaData(
                        4, false, "large", JDBCNullable.NULLABLE, JDBCType.BIGINT, "int8", "java.lang.Long", null));
    }

    @Test
    public void simpleQueryWithParameterMetaData() throws SQLException {
        var queryMetaData = getQueryMetaData("SELECT * FROM test_table where test_id = ?");
        assertThat(queryMetaData.parameters()).hasSize(1);
        assertThat(queryMetaData.parameters().get(0))
                .isEqualTo(new ColumnMetaData(
                        1,
                        true,
                        "parameter",
                        JDBCNullable.UNKNOWN,
                        JDBCType.INTEGER,
                        "int4",
                        "java.lang.Integer",
                        null));
        assertThat(queryMetaData.resultColumns()).hasSize(4);
        assertThat(queryMetaData.resultColumns().get(0))
                .isEqualTo(new ColumnMetaData(
                        1,
                        false,
                        "test_id",
                        JDBCNullable.NOT_NULL,
                        JDBCType.INTEGER,
                        "int4",
                        "java.lang.Integer",
                        null));
    }

    @Test
    public void simpleQueryArrayParameterMetaData() throws SQLException {
        var queryMetaData = getQueryMetaData("SELECT * FROM test_table where test_id = ANY(?)");
        assertThat(queryMetaData.parameters()).hasSize(1);
        assertThat(queryMetaData.parameters().get(0))
                .isEqualTo(new ColumnMetaData(
                        1,
                        true,
                        "parameter",
                        JDBCNullable.UNKNOWN,
                        JDBCType.ARRAY,
                        "_int4",
                        "java.sql.Array",
                        new ArrayComponent(JDBCType.INTEGER, "int4")));
        assertThat(queryMetaData.resultColumns()).hasSize(4);
        assertThat(queryMetaData.resultColumns().get(0))
                .isEqualTo(new ColumnMetaData(
                        1,
                        false,
                        "test_id",
                        JDBCNullable.NOT_NULL,
                        JDBCType.INTEGER,
                        "int4",
                        "java.lang.Integer",
                        null));
    }

    @Test
    public void insertQueryWithParameterMetaData() throws SQLException {
        var queryMetaData = getQueryMetaData("INSERT INTO test_table (test_id, notes) VALUES(?, ?) RETURNING test_id");
        assertThat(queryMetaData.parameters()).hasSize(2);
        assertThat(queryMetaData.parameters().get(0))
                .isEqualTo(new ColumnMetaData(
                        1,
                        true,
                        "parameter",
                        JDBCNullable.UNKNOWN,
                        JDBCType.INTEGER,
                        "int4",
                        "java.lang.Integer",
                        null));
        assertThat(queryMetaData.parameters().get(1))
                .isEqualTo(new ColumnMetaData(
                        2,
                        true,
                        "parameter",
                        JDBCNullable.UNKNOWN,
                        JDBCType.VARCHAR,
                        "text",
                        "java.lang.String",
                        null));
        assertThat(queryMetaData.resultColumns()).hasSize(1);
        assertThat(queryMetaData.resultColumns().get(0))
                .isEqualTo(new ColumnMetaData(
                        1,
                        false,
                        "test_id",
                        JDBCNullable.NOT_NULL,
                        JDBCType.INTEGER,
                        "int4",
                        "java.lang.Integer",
                        null));
    }

    @Test
    public void insertUpdateWithParameterMetaData() throws SQLException {
        var queryMetaData = getQueryMetaData("INSERT INTO test_table (test_id, notes) VALUES(?, ?)");
        assertThat(queryMetaData.parameters()).hasSize(2);
        assertThat(queryMetaData.parameters().get(0))
                .isEqualTo(new ColumnMetaData(
                        1,
                        true,
                        "parameter",
                        JDBCNullable.UNKNOWN,
                        JDBCType.INTEGER,
                        "int4",
                        "java.lang.Integer",
                        null));
        assertThat(queryMetaData.parameters().get(1))
                .isEqualTo(new ColumnMetaData(
                        2,
                        true,
                        "parameter",
                        JDBCNullable.UNKNOWN,
                        JDBCType.VARCHAR,
                        "text",
                        "java.lang.String",
                        null));
        assertThat(queryMetaData.resultColumns()).hasSize(0);
    }

    @Test
    public void insertParameterBehaviour() throws SQLException {
        var wrapper = getDatabaseWrapper();
        try (var connection = wrapper.getConnection();
                var preparedStatement =
                        connection.prepareStatement("INSERT INTO test_table (test_id, notes) VALUES(?, ?)")) {
            preparedStatement.setInt(1, 1);
            preparedStatement.setNull(2, Types.VARCHAR);
            var md = preparedStatement.getParameterMetaData();
            // PG driver does not know nullability of parameters
            assertThat(md.isNullable(1)).isEqualTo(ParameterMetaData.parameterNullableUnknown);
            assertThat(md.isNullable(2)).isEqualTo(ParameterMetaData.parameterNullableUnknown);
            var result = preparedStatement.executeUpdate();
            assertThat(result).isEqualTo(1);
        }
    }

    private QueryMetaData getQueryMetaData(String sql) throws SQLException {
        var wrapper = getDatabaseWrapper();

        return wrapper.getQueryMetaData(sql);
    }
}
