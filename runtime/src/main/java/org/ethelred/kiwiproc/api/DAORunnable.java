/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.api;

import java.sql.SQLException;

/**
 * A functional interface for transactional DAO operations that do not return a result.
 *
 * <p>Used with {@link TransactionalDAO#run(DAORunnable)}. Any {@link SQLException} thrown by the callback is
 * caught by the caller and wrapped in an
 * {@link org.ethelred.kiwiproc.exception.UncheckedSQLException UncheckedSQLException}.
 *
 * @param <T> the DAO interface type
 */
public interface DAORunnable<T> {
    void run(T dao) throws SQLException;
}
