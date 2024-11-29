/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.api;

import java.sql.SQLException;

public interface DAORunnable<T> {
    void run(T dao) throws SQLException;
}
