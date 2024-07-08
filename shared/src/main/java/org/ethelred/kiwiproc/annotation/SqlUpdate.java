package org.ethelred.kiwiproc.annotation;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlUpdate
{
    /**
     * Alias for "sql"
     */
    @Language("SQL")
    String value() default "";
    @Language("SQL")
    String sql() default "";
}
