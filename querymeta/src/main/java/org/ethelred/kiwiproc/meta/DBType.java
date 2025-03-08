/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import java.sql.JDBCType;

public interface DBType {
    JDBCType jdbcType();

    String dbType();
}
