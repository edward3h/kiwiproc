package org.ethelred.kiwiproc.exception;

import java.sql.SQLException;

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
