package org.ethelred.kiwiproc.impl;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.api.DAOProvider;
import org.ethelred.kiwiproc.api.TransactionManager;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;

/**
 * Implements {@link TransactionManager}. Abstract because we need to generate an implementation with the correct dependency injection annotations.
 */
public abstract class AbstractTransactionManager implements TransactionManager {
    private final DataSource dataSource;

    protected AbstractTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <A, B, R> R inTransaction(
            DAOProvider<A> providerA, DAOProvider<B> providerB, TransactionCallable2<A, B, R> callback)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            A daoA = ((AbstractDAOProvider<A>) providerA).withContext(() -> connection);
            B daoB = ((AbstractDAOProvider<B>) providerB).withContext(() -> connection);
            var result = callback.call(daoA, daoB);
            connection.commit();
            connection.setAutoCommit(true);
            return result;
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }

    @Override
    public <A, B> void inTransaction(
            DAOProvider<A> providerA, DAOProvider<B> providerB, TransactionRunnable2<A, B> callback)
            throws SQLException {

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            A daoA = ((AbstractDAOProvider<A>) providerA).withContext(() -> connection);
            B daoB = ((AbstractDAOProvider<B>) providerB).withContext(() -> connection);
            callback.run(daoA, daoB);
            connection.commit();
            connection.setAutoCommit(true);
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }

    @Override
    public <A, B, C, R> R inTransaction(
            DAOProvider<A> providerA,
            DAOProvider<B> providerB,
            DAOProvider<C> providerC,
            TransactionCallable3<A, B, C, R> callback)
            throws SQLException {

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            A daoA = ((AbstractDAOProvider<A>) providerA).withContext(() -> connection);
            B daoB = ((AbstractDAOProvider<B>) providerB).withContext(() -> connection);
            C daoC = ((AbstractDAOProvider<C>) providerC).withContext(() -> connection);
            var result = callback.call(daoA, daoB, daoC);
            connection.commit();
            connection.setAutoCommit(true);
            return result;
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }

    @Override
    public <A, B, C> void inTransaction(
            DAOProvider<A> providerA,
            DAOProvider<B> providerB,
            DAOProvider<C> providerC,
            TransactionRunnable3<A, B, C> callback)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            A daoA = ((AbstractDAOProvider<A>) providerA).withContext(() -> connection);
            B daoB = ((AbstractDAOProvider<B>) providerB).withContext(() -> connection);
            C daoC = ((AbstractDAOProvider<C>) providerC).withContext(() -> connection);
            callback.run(daoA, daoB, daoC);
            connection.commit();
            connection.setAutoCommit(true);
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }
}
