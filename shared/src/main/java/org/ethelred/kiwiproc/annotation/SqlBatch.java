/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.intellij.lang.annotations.Language;

/**
 * Annotates a DAO method as a batch modification statement. The method must have at least one parameter that is a
 * Collection or array; the statement is executed once per element, using JDBC batch execution.
 *
 * <p>Exactly one of {@link #value()} or {@link #sql()} must be set, containing the SQL statement. Named parameters
 * use the {@code :paramName} syntax. Collection or array parameters are iterated, binding one element per batch
 * entry. Scalar parameters are bound to the same value for every entry in the batch.
 *
 * <p><b>Supported return types:</b>
 * <ul>
 *   <li>{@code void} &ndash; ignores the update counts</li>
 *   <li>{@code int[]} &ndash; returns the per-statement update counts</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlBatch {
    /**
     * The SQL statement to execute. Alias for {@link #sql()}.
     *
     * @return the SQL batch string
     */
    @Language("SQL")
    String value() default "";

    /**
     * The SQL statement to execute. Alias for {@link #value()}.
     *
     * @return the SQL batch string
     */
    @Language("SQL")
    String sql() default "";

    /**
     * The number of statements to accumulate before flushing the batch to the database. Defaults to 50.
     *
     * @return the batch size
     */
    int batchSize() default 50;
}
