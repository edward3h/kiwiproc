/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.intellij.lang.annotations.Language;

/**
 * <p>
 *     Annotates a DAO method to mark it as a query that returns results. Typically, a SELECT, but could be an update with a
 * RETURNING clause.
 * </p>
 * <p>
 * Exactly one of the 'value' or 'sql' parameters must be set, and will contain the SQL statement.
 * </p>
 * <p>
 *     The method parameters and return types must be "supported types" TODO link
 *     The method parameter names must match placeholder names in the SQL statement, unless a method parameter is a
 *     Record, in which case record component names may match placeholder names.
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlQuery {
    /**
     * Alias for "sql"
     */
    @Language("SQL")
    String value() default "";

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

    int fetchSize() default Integer.MIN_VALUE;
}
