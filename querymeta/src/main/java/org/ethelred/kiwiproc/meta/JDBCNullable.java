/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import java.sql.ResultSetMetaData;

public enum JDBCNullable {
    NOT_NULL(ResultSetMetaData.columnNoNulls),
    NULLABLE(ResultSetMetaData.columnNullable),
    UNKNOWN(ResultSetMetaData.columnNullableUnknown);

    private final int constantValue;

    JDBCNullable(int constantValue) {
        this.constantValue = constantValue;
    }

    public static JDBCNullable fromCode(int code) {
        for (var value : values()) {
            if (code == value.constantValue) {
                return value;
            }
        }
        throw new IllegalArgumentException("JDBCNullable: unrecognized code " + code);
    }
}
