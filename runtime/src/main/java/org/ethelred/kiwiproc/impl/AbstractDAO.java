package org.ethelred.kiwiproc.impl;

import org.ethelred.kiwiproc.api.DAOContext;

public abstract class AbstractDAO<T> {
    protected final DAOContext context;

    protected AbstractDAO(DAOContext context) {
        this.context = context;
    }
}
