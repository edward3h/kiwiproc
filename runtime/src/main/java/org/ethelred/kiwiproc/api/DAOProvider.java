package org.ethelred.kiwiproc.api;

import java.sql.SQLException;

public interface DAOProvider<T> {
    String getDataSourceName();
    <R> R call(DAOCallable<T, R> callback) throws SQLException;
    void run(DAORunnable<T> callback) throws SQLException;

    T withContext(DAOContext context) throws SQLException;
}
