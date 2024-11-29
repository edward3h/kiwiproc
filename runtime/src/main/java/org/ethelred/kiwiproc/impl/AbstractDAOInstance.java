/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.impl;

import java.sql.SQLException;
import org.ethelred.kiwiproc.api.DAOCallable;
import org.ethelred.kiwiproc.api.DAOContext;
import org.ethelred.kiwiproc.api.DAORunnable;
import org.ethelred.kiwiproc.api.TransactionalDAO;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;

public abstract class AbstractDAOInstance<T> implements TransactionalDAO<T> {
    protected final DAOContext context;

    protected AbstractDAOInstance(DAOContext context) {
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> R call(DAOCallable<T, R> callback) {
        try {
            return callback.call((T) this);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run(DAORunnable<T> callback) {
        try {
            callback.run((T) this);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }
}
