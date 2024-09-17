package org.ethelred.kiwiproc.processor.types;

import org.ethelred.kiwiproc.processor.SqlTypeMapping;

public record SqlArrayType(KiwiType containedType, SqlTypeMapping componentType, String componentDbType)
        implements KiwiType {
    @Override
    public String packageName() {
        return "java.sql";
    }

    @Override
    public String className() {
        return "Array";
    }

    @Override
    public boolean isSimple() {
        return true; // kinda
    }

    @Override
    public String toString() {
        return "SqlArray[" + containedType + ']';
    }
}
