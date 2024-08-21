package org.ethelred.kiwiproc.processor;

public interface DAOGenerator {
    void generateProvider(DAOClassInfo classInfo);

    void generateImpl(DAOClassInfo classInfo);

    void generateTransactionManager(DAODataSourceInfo dataSourceInfo);
}
