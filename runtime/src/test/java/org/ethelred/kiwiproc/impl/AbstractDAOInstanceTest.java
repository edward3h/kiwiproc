/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import org.ethelred.kiwiproc.api.DAOContext;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;
import org.junit.jupiter.api.Test;

public class AbstractDAOInstanceTest {

    private static class TestDAOInstance extends AbstractDAOInstance<TestDAOInstance> {
        TestDAOInstance(DAOContext context) {
            super(context);
        }
    }

    @Test
    void callPassesSelfToCallbackAndReturnsResult() {
        var dao = new TestDAOInstance(() -> null);

        var result = dao.call(self -> self == dao ? "matched" : "mismatched");

        assertThat(result).isEqualTo("matched");
    }

    @Test
    void callWrapsSqlExceptionFromCallback() {
        var dao = new TestDAOInstance(() -> null);

        var thrown = assertThrows(
                UncheckedSQLException.class,
                () -> dao.call(self -> {
                    throw new SQLException("simulated failure");
                }));

        assertThat(thrown.getCause().getMessage()).isEqualTo("simulated failure");
    }

    @Test
    void runPassesSelfToCallback() {
        var dao = new TestDAOInstance(() -> null);
        var seen = new TestDAOInstance[1];

        dao.run(self -> seen[0] = self);

        assertThat(seen[0]).isSameInstanceAs(dao);
    }

    @Test
    void runWrapsSqlExceptionFromCallback() {
        var dao = new TestDAOInstance(() -> null);

        var thrown = assertThrows(
                UncheckedSQLException.class,
                () -> dao.run(self -> {
                    throw new SQLException("simulated failure");
                }));

        assertThat(thrown.getCause().getMessage()).isEqualTo("simulated failure");
    }
}
