/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;
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
            try {
                var result = callback.call(withContext(() -> connection));
                connection.commit();
                connection.setAutoCommit(true);
                return result;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
                throw new UncheckedSQLException(e);
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public void run(DAORunnable<T> callback) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                callback.run(withContext(() -> connection));
                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
                throw new UncheckedSQLException(e);
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * Returns a lazily-evaluated {@link Stream} of query results using a dedicated JDBC connection.
     *
     * <p>Unlike {@link #call}, the connection is not committed when this method returns — it stays
     * open while the stream is consumed. <strong>The caller must close the stream</strong> (e.g. via
     * try-with-resources), which commits the transaction and closes the connection.
     *
     * @param callback produces a {@code Stream<R>} from the DAO instance
     * @param <R>      the element type of the stream
     * @return a lazy stream; the caller is responsible for closing it
     */
    public <R> Stream<R> streamCall(DAOCallable<T, Stream<R>> callback) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
        try {
            var resultStream = callback.call(withContext(() -> connection));
            return resultStream.onClose(() -> {
                try {
                    connection.commit();
                    connection.close();
                } catch (SQLException e) {
                    try {
                        connection.rollback();
                    } catch (SQLException re) {
                        e.addSuppressed(re);
                    }
                    try {
                        connection.close();
                    } catch (SQLException ce) {
                        e.addSuppressed(ce);
                    }
                    throw new UncheckedSQLException(e);
                }
            });
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException ce) {
                e.addSuppressed(ce);
            }
            throw new UncheckedSQLException(e);
        } catch (RuntimeException e) {
            try {
                connection.close();
            } catch (SQLException ce) {
                e.addSuppressed(ce);
            }
            throw e;
        }
    }

    protected abstract T withContext(DAOContext context) throws SQLException;
}
