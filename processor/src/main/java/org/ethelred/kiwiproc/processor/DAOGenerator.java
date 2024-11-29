/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

public interface DAOGenerator {
    void generateProvider(DAOClassInfo classInfo);

    void generateImpl(DAOClassInfo classInfo);
}
