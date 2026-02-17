/* (C) Edward Harman 2024 */

/**
 * Annotations for defining Kiwiproc Data Access Objects (DAOs).
 *
 * <p>Kiwiproc uses compile-time annotation processing to generate type-safe JDBC implementations from annotated
 * interfaces. Annotate an interface with {@link org.ethelred.kiwiproc.annotation.DAO @DAO}, then define methods using
 * the SQL method annotations:
 *
 * <ul>
 *   <li>{@link org.ethelred.kiwiproc.annotation.SqlQuery @SqlQuery} &ndash; queries that return results (SELECT, or
 *       statements with RETURNING clauses)</li>
 *   <li>{@link org.ethelred.kiwiproc.annotation.SqlUpdate @SqlUpdate} &ndash; single-row modifications (INSERT, UPDATE,
 *       DELETE)</li>
 *   <li>{@link org.ethelred.kiwiproc.annotation.SqlBatch @SqlBatch} &ndash; batch modifications operating over a
 *       collection of parameter values</li>
 * </ul>
 *
 * <p>At compile time, the annotation processor validates SQL statements against a PostgreSQL database schema and
 * generates two implementation classes per DAO interface: a Provider class that manages transactions and dependency
 * injection, and an Impl class containing the actual JDBC code.
 */
package org.ethelred.kiwiproc.annotation;
