package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilderFull;
import java.util.List;
import javax.lang.model.element.TypeElement;

@RecordBuilderFull
public record DAOClassInfo(
        TypeElement element, DAOPrism annotation, String packageName, String daoName, List<DAOMethodInfo> methods) {
    public String dataSourceName() {
        return annotation().dataSourceName();
    }

    public String className(String suffix) {
        return "$" + daoName + "$" + suffix;
    }
}
