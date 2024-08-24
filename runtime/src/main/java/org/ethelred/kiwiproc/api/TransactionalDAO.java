package org.ethelred.kiwiproc.api;

public interface TransactionalDAO<T> {

    <R> R call(DAOCallable<T, R> callback);

    void run(DAORunnable<T> callback);
}
