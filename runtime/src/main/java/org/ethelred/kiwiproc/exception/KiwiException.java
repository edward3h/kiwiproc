/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.exception;

import java.sql.SQLException;

/**
 * Base checked exception for Kiwiproc errors. Extends {@link SQLException} so that it integrates naturally with
 * JDBC error handling in generated DAO implementations.
 */
public class KiwiException extends SQLException {
    public KiwiException(String reason) {
        super(reason);
    }

    public KiwiException(Throwable cause) {
        super(cause);
    }

    public KiwiException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
