package org.ethelred.kiwiproc.processor;

import io.jstach.jstache.JStacheLambda;

public interface ClassNameMixin {

    @JStacheLambda
    @JStacheLambda.Raw
    default String className(@JStacheLambda.Raw String body, DAOClassInfo classInfo) {
        return className(body, classInfo.daoName());
    }

    default String className(String body, String daoName) {
        return "$" + daoName + "$" +body;
    }
}
