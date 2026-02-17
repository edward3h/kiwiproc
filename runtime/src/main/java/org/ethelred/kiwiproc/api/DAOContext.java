/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.api;

import java.sql.Connection;

/**
 * Provides access to the JDBC {@link Connection} used by a DAO instance. This interface is managed by the
 * framework and should not normally be implemented by application code.
 */
public interface DAOContext {
    Connection getConnection();
}
