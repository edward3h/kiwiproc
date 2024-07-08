package org.ethelred.kiwiproc.api;

public interface TransactionManager {
    <A, B, R> R inTransaction(DAOProvider<A> providerA, DAOProvider<B> providerB, TransactionCallable2<A, B, R> callback);
    <A, B> void inTransaction(DAOProvider<A> providerA, DAOProvider<B> providerB, TransactionRunnable2<A, B> callback);
    interface TransactionCallable2<A,B,R> {
        R call(A daoA, B daoB);
    }

    interface TransactionRunnable2<A,B> {
        void run(A daoA, B daoB);
    }

    <A, B, C, R> R inTransaction(DAOProvider<A> providerA, DAOProvider<B> providerB, DAOProvider<C> providerC, TransactionCallable3<A, B, C, R> callback);
    <A, B, C> void inTransaction(DAOProvider<A> providerA, DAOProvider<B> providerB, DAOProvider<C> providerC, TransactionRunnable3<A, B, C> callback);
    interface TransactionCallable3<A,B,C,R> {
        R call(A daoA, B daoB, C daoC);
    }

    interface TransactionRunnable3<A,B,C> {
        void run(A daoA, B daoB, C daoC);
    }
}
