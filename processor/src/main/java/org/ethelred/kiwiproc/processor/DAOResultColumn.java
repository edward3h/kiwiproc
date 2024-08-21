package org.ethelred.kiwiproc.processor;

public record DAOResultColumn(String name, SqlTypeMapping sqlTypeMapping, KiwiType targetType) {
    public TypeMapping asTypeMapping() {
        return TypeMapping.of(sqlTypeMapping.kiwiType(), targetType);
    }
}
