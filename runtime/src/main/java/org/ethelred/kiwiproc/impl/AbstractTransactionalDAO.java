/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.impl;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.api.DAOCallable;
import org.ethelred.kiwiproc.api.DAOContext;
import org.ethelred.kiwiproc.api.DAORunnable;
import org.ethelred.kiwiproc.api.TransactionalDAO;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;

public abstract class AbstractTransactionalDAO<T> implements TransactionalDAO<T> {
    /* package */ final DataSource dataSource;

    protected AbstractTransactionalDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <R> R call(DAOCallable<T, R> callback) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            var result = callback.call(withContext(() -> connection));
            connection.commit();
            connection.setAutoCommit(true);
            return result;
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public void run(DAORunnable<T> callback) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            callback.run(withContext(() -> connection));
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    protected abstract T withContext(DAOContext context) throws SQLException;
}
