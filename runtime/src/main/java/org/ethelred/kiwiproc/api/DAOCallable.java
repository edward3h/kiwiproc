package org.ethelred.kiwiproc.api;

import java.sql.SQLException;

public interface DAOCallable<T, R> {
    R call(T dao) throws SQLException;
}
