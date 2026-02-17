/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as a Data Access Object (DAO) for compile-time code generation.
 *
 * <p>The annotated type must be an interface containing at least one method annotated with
 * {@link SqlQuery @SqlQuery}, {@link SqlUpdate @SqlUpdate}, or {@link SqlBatch @SqlBatch}.
 *
 * <p>The annotation processor generates two classes per DAO interface:
 * <ul>
 *   <li>A <b>Provider</b> class that implements {@code TransactionalDAO}, handles dependency injection
 *       registration, and manages transaction lifecycle.</li>
 *   <li>An <b>Impl</b> class that contains the actual JDBC code (PreparedStatement creation, parameter binding,
 *       and ResultSet mapping).</li>
 * </ul>
 *
 * <p>The Provider class is registered as a singleton with the configured dependency injection framework (Jakarta CDI
 * or Spring) and can be injected directly into application code.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DAO {
    /**
     * The name of the data source to use for this DAO. When multiple data sources are configured, this value is used
     * to select the correct one via a DI qualifier. Defaults to {@code "default"}.
     *
     * @return the data source name
     */
    String dataSourceName() default "default";
}
