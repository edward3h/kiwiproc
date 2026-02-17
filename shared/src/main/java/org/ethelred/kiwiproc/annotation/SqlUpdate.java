/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.intellij.lang.annotations.Language;

/**
 * Annotates a DAO method as a single-row modification statement (INSERT, UPDATE, or DELETE).
 *
 * <p>Exactly one of {@link #value()} or {@link #sql()} must be set, containing the SQL statement. Named parameters
 * use the {@code :paramName} syntax and are bound from method parameter names. If a method parameter is a Record,
 * its component names may match placeholder names instead.
 *
 * <p><b>Supported return types:</b>
 * <ul>
 *   <li>{@code void} &ndash; ignores the update count</li>
 *   <li>{@code boolean} &ndash; returns {@code true} if at least one row was affected</li>
 *   <li>{@code int} &ndash; returns the number of affected rows</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlUpdate {
    /**
     * The SQL statement to execute. Alias for {@link #sql()}.
     *
     * @return the SQL update string
     */
    @Language("SQL")
    String value() default "";

    /**
     * The SQL statement to execute. Alias for {@link #value()}.
     *
     * @return the SQL update string
     */
    @Language("SQL")
    String sql() default "";
}
