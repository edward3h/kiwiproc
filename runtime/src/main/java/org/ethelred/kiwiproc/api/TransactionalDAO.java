/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.api;

/**
 * Entry point for executing DAO operations within a managed transaction.
 *
 * <p>Generated Provider classes implement this interface. Inject the Provider into your application code and use
 * {@link #call(DAOCallable)} or {@link #run(DAORunnable)} to execute queries and updates within a transaction.
 *
 * <p>Transaction lifecycle:
 * <ol>
 *   <li>A JDBC connection is obtained and auto-commit is disabled.</li>
 *   <li>A DAO instance is created and passed to the callback.</li>
 *   <li>If the callback completes normally, the transaction is committed.</li>
 *   <li>If the callback throws, the transaction is rolled back.</li>
 *   <li>The connection is closed in all cases.</li>
 * </ol>
 *
 * @param <T> the DAO interface type
 */
public interface TransactionalDAO<T> {

    /**
     * Executes the given callback within a transaction and returns its result.
     *
     * @param callback the operation to perform with the DAO instance
     * @param <R>      the return type
     * @return the value returned by the callback
     * @throws org.ethelred.kiwiproc.exception.UncheckedSQLException if a database error occurs
     */
    <R> R call(DAOCallable<T, R> callback);

    /**
     * Executes the given callback within a transaction.
     *
     * @param callback the operation to perform with the DAO instance
     * @throws org.ethelred.kiwiproc.exception.UncheckedSQLException if a database error occurs
     */
    void run(DAORunnable<T> callback);
}
