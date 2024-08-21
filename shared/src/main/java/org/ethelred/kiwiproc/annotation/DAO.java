package org.ethelred.kiwiproc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DAO must annotate an interface. A DAOProvider and DAO implementation will be generated. A DAO interface must contain
 * at least one method annotated with one of the Sql annotations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DAO {
    String dataSourceName() default "default";
}
