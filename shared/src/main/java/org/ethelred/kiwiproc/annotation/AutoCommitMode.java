/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.annotation;

/**
 * Per-method setting for whether a single-statement call runs in auto-commit mode rather than in its own
 * transaction. See {@link DAO#autoCommit()}.
 */
public enum AutoCommitMode {
    /** Use the {@link DAO#autoCommit()} setting of the enclosing DAO. */
    INHERIT,
    /** Run this method in auto-commit mode, regardless of the DAO setting. */
    ENABLED,
    /**
     * Run this method in its own transaction, regardless of the DAO setting. Use this for statements that need
     * a transaction, for example {@code SELECT ... FOR UPDATE}.
     */
    DISABLED
}
