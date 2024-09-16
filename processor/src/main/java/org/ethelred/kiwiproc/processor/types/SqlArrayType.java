package org.ethelred.kiwiproc.processor.types;

import java.sql.JDBCType;

public record SqlArrayType(KiwiType containedType, JDBCType componentType, String dbType) implements KiwiType {
    @Override
    public String packageName() {
        return "";
    }

    @Override
    public String className() {
        return "ARRAY";
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
