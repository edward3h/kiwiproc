package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilderFull;

import javax.lang.model.element.TypeElement;
import java.util.List;

@RecordBuilderFull
public record DAOClassInfo(
        TypeElement element,
        DAOPrism annotation,
        String packageName,
        String daoName,
        List<DAOMethodInfo> methods
) {
    public String dataSourceName() {
        return annotation().dataSourceName();
    }
}
