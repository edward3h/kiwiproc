/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testany;

import static com.google.common.truth.Truth.assertThat;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

public class AutoCommitTest {
    private static DataSource realDataSource() throws Exception {
        var properties = new Properties();
        try (var in = AutoCommitTest.class.getResourceAsStream("/application-test.properties");
                var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        var dataSource = new PGSimpleDataSource();
        dataSource.setURL(properties.getProperty("datasources.periodic-table.url"));
        return dataSource;
    }

    /** Wraps a DataSource so that every Connection.setAutoCommit(boolean) argument is recorded. */
    private static DataSource recording(DataSource delegate, List<Boolean> autoCommitCalls) {
        return (DataSource) Proxy.newProxyInstance(
                AutoCommitTest.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    var result = method.invoke(delegate, args);
                    if (result instanceof Connection connection) {
                        return Proxy.newProxyInstance(
                                AutoCommitTest.class.getClassLoader(), new Class<?>[] {Connection.class}, (p, m, a) -> {
                                    if (m.getName().equals("setAutoCommit")) {
                                        autoCommitCalls.add((Boolean) a[0]);
                                    }
                                    return m.invoke(connection, a);
                                });
                    }
                    return result;
                });
    }

    @Test
    void autoCommitDaoDoesNotStartTransactionForSingleQuery() throws Exception {
        var calls = new ArrayList<Boolean>();
        var dao = new $AutoCommitDAO$Provider(recording(realDataSource(), calls));

        assertThat(dao.atomicNumberOf("He")).isEqualTo(2);
        assertThat(calls).doesNotContain(false);
    }

    @Test
    void defaultDaoStillUsesTransactionForSingleQuery() throws Exception {
        var calls = new ArrayList<Boolean>();
        var dao = new $PeriodicTableDAO$Provider(recording(realDataSource(), calls));

        assertThat(dao.atomicNumberQuery(2)).isEqualTo(1);
        assertThat(calls).contains(false);
    }

    @Test
    void methodCanDisableAutoCommitOnAutoCommitDao() throws Exception {
        var calls = new ArrayList<Boolean>();
        var dao = new $AutoCommitDAO$Provider(recording(realDataSource(), calls));

        assertThat(dao.atomicNumberForUpdate("He")).isEqualTo(2);
        assertThat(calls).contains(false);
    }

    @Test
    void methodCanEnableAutoCommitOnDefaultDao() throws Exception {
        var calls = new ArrayList<Boolean>();
        var dao = new $PeriodicTableDAO$Provider(recording(realDataSource(), calls));

        assertThat(dao.atomicNumberAutoCommit("He")).isEqualTo(2);
        assertThat(calls).doesNotContain(false);
    }
}
