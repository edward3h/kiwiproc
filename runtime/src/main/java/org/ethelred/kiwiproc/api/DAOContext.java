package org.ethelred.kiwiproc.api;

import java.sql.Connection;
import java.sql.SQLException;

public interface DAOContext {
    Connection getConnection() throws SQLException;
}
