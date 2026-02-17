/* (C) Edward Harman 2024 */

/**
 * Runtime API for interacting with generated Kiwiproc DAOs.
 *
 * <p>The central type is {@link org.ethelred.kiwiproc.api.TransactionalDAO}, which generated Provider classes
 * implement. It provides transaction management through the {@code call} and {@code run} methods, which accept
 * {@link org.ethelred.kiwiproc.api.DAOCallable} and {@link org.ethelred.kiwiproc.api.DAORunnable} callbacks
 * respectively.
 *
 * <p>{@link org.ethelred.kiwiproc.api.DAOContext} provides access to the underlying JDBC connection and is managed
 * by the framework.
 */
package org.ethelred.kiwiproc.api;
