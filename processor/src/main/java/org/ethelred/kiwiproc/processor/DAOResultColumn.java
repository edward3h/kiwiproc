package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.processor.types.KiwiType;

public record DAOResultColumn(String name, SqlTypeMapping sqlTypeMapping, KiwiType targetType) {
    public TypeMapping asTypeMapping() {
        return new TypeMapping(sqlTypeMapping.kiwiType(), targetType);
    }
}
