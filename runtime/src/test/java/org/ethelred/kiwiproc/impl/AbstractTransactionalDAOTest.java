/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.api.DAOContext;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

public class AbstractTransactionalDAOTest {

    private static class TestDAO extends AbstractTransactionalDAO<DAOContext> {
        TestDAO(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        protected DAOContext withContext(DAOContext context) {
            return context;
        }
    }

    private static DataSource dataSourceFor(String name) throws SQLException {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (var conn = ds.getConnection()) {
            conn.createStatement().execute("CREATE TABLE nums (n INT)");
        }
        return ds;
    }

    @Test
    void callCommitsOnSuccess() throws Exception {
        var dataSource = dataSourceFor("AbstractTransactionalDAOTest_commit");
        var dao = new TestDAO(dataSource);

        var inserted = dao.call(ctx -> {
            var conn = ctx.getConnection();
            return conn.createStatement().executeUpdate("INSERT INTO nums VALUES (1)");
        });

        assertThat(inserted).isEqualTo(1);
        try (var conn = dataSource.getConnection();
                var rs = conn.createStatement().executeQuery("SELECT count(*) FROM nums")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void callRollsBackOnSqlException() throws Exception {
        var dataSource = dataSourceFor("AbstractTransactionalDAOTest_rollback");
        var dao = new TestDAO(dataSource);

        assertThrows(
                UncheckedSQLException.class,
                () -> dao.call(ctx -> {
                    var conn = ctx.getConnection();
                    conn.createStatement().executeUpdate("INSERT INTO nums VALUES (1)");
                    throw new SQLException("simulated failure");
                }));

        try (var conn = dataSource.getConnection();
                var rs = conn.createStatement().executeQuery("SELECT count(*) FROM nums")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(0);
        }
    }

    @Test
    void runCommitsOnSuccess() throws Exception {
        var dataSource = dataSourceFor("AbstractTransactionalDAOTest_run");
        var dao = new TestDAO(dataSource);

        dao.run(ctx -> ctx.getConnection().createStatement().executeUpdate("INSERT INTO nums VALUES (1)"));

        try (var conn = dataSource.getConnection();
                var rs = conn.createStatement().executeQuery("SELECT count(*) FROM nums")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void streamCallCommitsOnlyWhenStreamIsClosed() throws Exception {
        var dataSource = dataSourceFor("AbstractTransactionalDAOTest_stream");
        var dao = new TestDAO(dataSource);
        try (var conn = dataSource.getConnection()) {
            conn.createStatement().executeUpdate("INSERT INTO nums VALUES (1),(2),(3)");
        }

        try (var stream = dao.streamCall(ctx -> {
            var rs = ctx.getConnection().createStatement().executeQuery("SELECT n FROM nums ORDER BY n");
            var results = new java.util.ArrayList<Integer>();
            while (rs.next()) {
                results.add(rs.getInt(1));
            }
            return results.stream();
        })) {
            assertThat(stream.toList()).containsExactly(1, 2, 3).inOrder();
        }
    }

    @Test
    void streamCallClosesConnectionOnSetupFailure() throws Exception {
        var dataSource = dataSourceFor("AbstractTransactionalDAOTest_stream_fail");
        var dao = new TestDAO(dataSource);

        assertThrows(
                RuntimeException.class,
                () -> dao.streamCall(ctx -> {
                    throw new RuntimeException("boom");
                }));
    }
}
