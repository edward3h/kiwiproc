package org.ethelred.kiwiproc.api;

import java.sql.Connection;

public interface DAOContext {
    Connection getConnection();
}
