/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.impl;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultSetMapper<T> {
    T map(ResultSet rs) throws SQLException;
}
