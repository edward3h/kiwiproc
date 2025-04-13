/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.ethelred.kiwiproc.processor.TestUtils.atLeastOne;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import io.zonky.test.db.postgres.junit5.EmbeddedPostgresExtension;
import io.zonky.test.db.postgres.junit5.PreparedDbExtension;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Stream;
import org.ethelred.kiwiproc.meta.ArrayComponent;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.meta.DatabaseWrapper;
import org.ethelred.kiwiproc.meta.JDBCNullable;
import org.ethelred.kiwiproc.processor.types.ObjectType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.ethelred.kiwiproc.processor.types.SqlArrayType;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class SqlTypeMappingTest {
    @RegisterExtension
    public static PreparedDbExtension pg =
            EmbeddedPostgresExtension.preparedDatabase(LiquibasePreparer.forClasspathLocation("changelog.xml"));

    Set<JDBCType> unsupportedTypes = Set.of(
            JDBCType.ARRAY, // special case below
            // Consider supporting the below, but I haven't seen them used in projects I've worked on.
            JDBCType.BINARY,
            JDBCType.VARBINARY,
            JDBCType.LONGVARBINARY,
            JDBCType.OTHER,
            JDBCType.JAVA_OBJECT,
            JDBCType.DISTINCT,
            JDBCType.STRUCT,
            JDBCType.BLOB,
            JDBCType.CLOB,
            JDBCType.NCLOB,
            JDBCType.REF,
            JDBCType.DATALINK,
            JDBCType.ROWID, // TODO?
            JDBCType.SQLXML,
            JDBCType.REF_CURSOR);
    Set<JDBCType> postgresUnsupported = Set.of(
            JDBCType.TINYINT, // no corresponding type
            JDBCType.FLOAT, // treated same as double
            JDBCType.LONGVARCHAR, // no corresponding type
            JDBCType.LONGNVARCHAR, // no corresponding type
            JDBCType.DECIMAL, // synonym for NUMERIC
            JDBCType.NULL, // not a column type
            JDBCType.NVARCHAR, // no corresponding type
            JDBCType.BOOLEAN, // same as BIT
            JDBCType.NCHAR // same as CHAR
            );

    public static Stream<Arguments> sqlMappingIsPresentForArrayType() {
        // arguments - JDBCType, String: db specific type name
        return Stream.of(arguments(JDBCType.INTEGER, "fail"));
    }

    @ParameterizedTest
    @EnumSource(JDBCType.class)
    public void sqlMappingIsPresentForJDBCType(JDBCType jdbcType) {
        if (unsupportedTypes.contains(jdbcType)) {
            return;
        }
        var columnMetaData =
                new ColumnMetaData(1, false, "columnName", JDBCNullable.NOT_NULL, jdbcType, "foo", "bar", null);
        var mapping = SqlTypeMappingRegistry.get(columnMetaData);
        assertThat(mapping).isNotNull();
        atLeastOne(
                assertThat(mapping.kiwiType()),
                s -> s.isInstanceOf(PrimitiveKiwiType.class),
                s -> s.isInstanceOf(ObjectType.class));
    }

    @ParameterizedTest
    @MethodSource
    public void sqlMappingIsPresentForArrayType(JDBCType componentType, String dbType) {
        if (unsupportedTypes.contains(componentType)) {
            return;
        }
        var columnMetaData = new ColumnMetaData(
                1,
                false,
                "columnName",
                JDBCNullable.NOT_NULL,
                JDBCType.ARRAY,
                "foo",
                "bar",
                new ArrayComponent(componentType, dbType));
        var mapping = SqlTypeMappingRegistry.get(columnMetaData);
        assertThat(mapping).isNotNull();
        assertThat(mapping.kiwiType()).isInstanceOf(SqlArrayType.class);
    }

    @ParameterizedTest
    @EnumSource(JDBCType.class)
    public void sqlMappingFromPostgres(JDBCType jdbcType) throws SQLException {
        if (unsupportedTypes.contains(jdbcType) || postgresUnsupported.contains(jdbcType)) {
            return;
        }
        var columnName = "col_" + jdbcType.name().toLowerCase();
        var query = "SELECT %s FROM sql_type_mapping_test".formatted(columnName);
        var ci = pg.getConnectionInfo();
        var databaseWrapper = new DatabaseWrapper(
                "sqlMappingFromPostgres",
                new DataSourceConfig(
                        "test",
                        "jdbc:postgresql://localhost:%d/%s?user=%s"
                                .formatted(ci.getPort(), ci.getDbName(), ci.getUser()),
                        ci.getDbName(),
                        ci.getUser(),
                        "postgres",
                        "org.postgresql.Driver"));
        var queryInfo = databaseWrapper.getQueryMetaData(query);
        assertThat(queryInfo.resultColumns()).hasSize(1);
        var resultColumn = queryInfo.resultColumns().get(0);
        var sqlMapping = SqlTypeMappingRegistry.get(resultColumn);
        assertWithMessage("Column %s expected to match JDBCType %s. CMD %s", columnName, jdbcType, resultColumn)
                .that(sqlMapping.jdbcType())
                .isEqualTo(jdbcType);
    }
}
