/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import org.ethelred.kiwiproc.processorconfig.DatabaseKind;
import org.junit.jupiter.api.Test;

class DataSourceParameterTest {

    @Test
    void getDatabaseKind_byDriverClassName() {
        var param = new DataSourceParameter();
        param.setDriverClassName("com.mysql.cj.jdbc.Driver");
        assertThat(param.getDatabaseKind()).isEqualTo(DatabaseKind.MYSQL);
    }

    @Test
    void getDatabaseKind_mysqlByUrlAlone_previouslyUnsupportedNowWorks() {
        var param = new DataSourceParameter();
        param.setJdbcUrl("jdbc:mysql://localhost:3306/test");
        assertThat(param.getDatabaseKind()).isEqualTo(DatabaseKind.MYSQL);
    }

    @Test
    void getDatabaseKind_h2ByUrlAlone() {
        var param = new DataSourceParameter();
        param.setJdbcUrl("jdbc:h2:mem:test");
        assertThat(param.getDatabaseKind()).isEqualTo(DatabaseKind.H2);
    }

    @Test
    void getDatabaseKind_defaultsToPostgres() {
        var param = new DataSourceParameter();
        assertThat(param.getDatabaseKind()).isEqualTo(DatabaseKind.POSTGRES);
    }
}
