/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.meta;

import static com.google.common.truth.Truth.assertThat;

import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.junit.jupiter.api.Test;

class SqliteDialectTest {
    SqliteDialect dialect = new SqliteDialect();

    @Test
    void createDataSource_setsUrl() throws java.sql.SQLException {
        var config = new DataSourceConfig(
                "default", "jdbc:sqlite:/tmp/kiwiproc-test.db", null, null, null, "org.sqlite.JDBC");
        var ds = dialect.createDataSource(config);
        assertThat(ds.getConnection().getMetaData().getURL()).isEqualTo("jdbc:sqlite:/tmp/kiwiproc-test.db");
    }

    @Test
    void normalizeColumnName_isIdentity() {
        assertThat(dialect.normalizeColumnName("MixedCase")).isEqualTo("MixedCase");
    }
}
