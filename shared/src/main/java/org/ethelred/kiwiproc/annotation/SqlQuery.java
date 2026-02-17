/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.intellij.lang.annotations.Language;

/**
 * Annotates a DAO method as a query that returns results. Typically a SELECT, but may also be a statement with a
 * RETURNING clause.
 *
 * <p>Exactly one of {@link #value()} or {@link #sql()} must be set, containing the SQL statement. Named parameters
 * use the {@code :paramName} syntax and are bound from method parameter names. If a method parameter is a Record,
 * its component names may match placeholder names instead.
 *
 * <p><b>Supported return types:</b>
 * <ul>
 *   <li>A single value or Record &ndash; expects exactly one row; throws if zero or more than one row returned</li>
 *   <li>{@link java.util.Optional Optional&lt;T&gt;} &ndash; returns empty if no rows, throws if more than one</li>
 *   <li>A {@link org.jspecify.annotations.Nullable @Nullable} type &ndash; returns null if no rows</li>
 *   <li>{@link java.util.List List&lt;T&gt;} or other Collection types &ndash; returns all rows</li>
 *   <li>{@link java.util.Map Map&lt;K, V&gt;} &ndash; returns rows as key-value pairs (see {@link #keyColumn()} and
 *       {@link #valueColumn()})</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlQuery {
    /**
     * The SQL statement to execute. Alias for {@link #sql()}.
     *
     * @return the SQL query string
     */
    @Language("SQL")
    String value() default "";

    /**
     * The SQL statement to execute. Alias for {@link #value()}.
     *
     * @return the SQL query string
     */
    @Language("SQL")
    String sql() default "";

    /**
     * <p>When the return type of the method is Map, this is the column name to use as the Map key. It is an error to set
     * this if the return type is not Map.</p>
     * <p>If the Map key type is a Record, the columns will be matched by the record component names.</p>
     * <p>This may also be omitted if there is a column with literal name "key".</p>
     */
    String keyColumn() default "";

    /**
     * <p>When the return type of the method is Map, this is the column name to use as the Map value. It is an error to set
     * this if the return type is not Map.</p>
     * <p>If the Map value type is a Record, the columns will be matched by the record component names.</p>
     * <p>This may also be omitted if there is a column with literal name "value".</p>
     */
    String valueColumn() default "";

    /**
     * JDBC fetch size hint for the query. When set to a positive value, the JDBC driver may use this as a hint for
     * the number of rows to fetch from the database at a time. Defaults to the driver's own fetch size.
     *
     * @return the fetch size, or {@link Integer#MIN_VALUE} to use the driver default
     */
    int fetchSize() default Integer.MIN_VALUE;
}
