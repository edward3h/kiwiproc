package org.ethelred.kiwiproc.impl;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.api.DAOCallable;
import org.ethelred.kiwiproc.api.DAOContext;
import org.ethelred.kiwiproc.api.DAOProvider;
import org.ethelred.kiwiproc.api.DAORunnable;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;

public abstract class AbstractDAOProvider<T> implements DAOProvider<T> {
    /* package */ final DataSource dataSource;

    protected AbstractDAOProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <R> R call(DAOCallable<T, R> callback) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            var result = callback.call(withContext(() -> connection));
            connection.commit();
            connection.setAutoCommit(true);
            return result;
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }

    @Override
    public void run(DAORunnable<T> callback) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            callback.run(withContext(() -> connection));
            connection.commit();
            connection.setAutoCommit(true);
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }

    protected abstract T withContext(DAOContext context) throws SQLException;
}
