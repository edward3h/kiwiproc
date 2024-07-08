package org.ethelred.kiwiproc.impl;

import org.ethelred.kiwiproc.api.DAOCallable;
import org.ethelred.kiwiproc.api.DAOProvider;
import org.ethelred.kiwiproc.api.DAORunnable;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;

import javax.sql.DataSource;
import java.sql.SQLException;

public abstract class AbstractDAOProvider<T> implements DAOProvider<T> {
    private final DataSource dataSource;

    protected AbstractDAOProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <R> R call(DAOCallable<T, R> callback) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            return callback.call(withContext(() -> connection));
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }

    @Override
    public void run(DAORunnable<T> callback) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            callback.run(withContext(() -> connection));
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }
}
