/* (C) Edward Harman 2024 */

/**
 * Exception types used by Kiwiproc.
 *
 * <p>{@link org.ethelred.kiwiproc.exception.KiwiException} is the base checked exception, extending
 * {@link java.sql.SQLException}. It is used within generated DAO implementations for database errors.
 *
 * <p>{@link org.ethelred.kiwiproc.exception.UncheckedSQLException} is an unchecked wrapper for
 * {@link java.sql.SQLException}, thrown by
 * {@link org.ethelred.kiwiproc.api.TransactionalDAO#call TransactionalDAO.call} and
 * {@link org.ethelred.kiwiproc.api.TransactionalDAO#run TransactionalDAO.run} when a database error occurs during
 * a transactional callback.
 */
package org.ethelred.kiwiproc.exception;
