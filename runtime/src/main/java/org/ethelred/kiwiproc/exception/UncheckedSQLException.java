/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.exception;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Unchecked wrapper for {@link SQLException}. Thrown by
 * {@link org.ethelred.kiwiproc.api.TransactionalDAO#call TransactionalDAO.call} and
 * {@link org.ethelred.kiwiproc.api.TransactionalDAO#run TransactionalDAO.run} when a database error occurs
 * during a transactional callback, allowing callers to handle database errors without checked exceptions.
 */
public class UncheckedSQLException extends RuntimeException {
    public UncheckedSQLException(String message, SQLException cause) {
        super(message, Objects.requireNonNull(cause));
    }

    public UncheckedSQLException(SQLException cause) {
        super(Objects.requireNonNull(cause));
    }

    /**
     * Returns the underlying {@link SQLException} that caused this exception.
     *
     * @return the cause, guaranteed to be non-null and of type {@link SQLException}
     */
    @Override
    public SQLException getCause() {
        return (SQLException) super.getCause();
    }
}
