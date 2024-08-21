package org.ethelred.kiwiproc.processor.generator;

import com.palantir.javapoet.ClassName;

public final class RuntimeTypes {
    public static final ClassName ABSTRACT_DAO = ClassName.get("org.ethelred.kiwiproc.impl", "AbstractDAO");
    public static final ClassName ABSTRACT_PROVIDER =
            ClassName.get("org.ethelred.kiwiproc.impl", "AbstractDAOProvider");
    public static final ClassName ABSTRACT_TRANSACTION_MANAGER =
            ClassName.get("org.ethelred.kiwiproc.impl", "AbstractTransactionManager");
    public static final ClassName DAO_CONTEXT = ClassName.get("org.ethelred.kiwiproc.api", "DAOContext");
    public static final ClassName UNCHECKED_SQL_EXCEPTION =
            ClassName.get("org.ethelred.kiwiproc.exception", "UncheckedSQLException");

    private RuntimeTypes() {}
}
