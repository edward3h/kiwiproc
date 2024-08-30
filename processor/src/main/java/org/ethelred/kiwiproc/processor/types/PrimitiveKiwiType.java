package org.ethelred.kiwiproc.processor.types;

import org.ethelred.kiwiproc.processor.CoreTypes;

/**
 * Represent the duality of primitive and boxed types.
 * @param primitiveName
 * @param isNullable
 */
public record PrimitiveKiwiType(String primitiveName, boolean isNullable) implements KiwiType {
    public PrimitiveKiwiType {
        if (!CoreTypes.primitiveToBoxedStrings.containsKey(primitiveName)) {
            throw new IllegalArgumentException("Unknown primitive " + primitiveName);
        }
    }

    @Override
    public String packageName() {
        if (isNullable) {
            return "java.lang";
        }
        return "";
    }

    @Override
    public String className() {
        if (isNullable) {
            return CoreTypes.primitiveToBoxedStrings.get(primitiveName);
        }
        return primitiveName;
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public KiwiType withIsNullable(boolean b) {
        return new PrimitiveKiwiType(primitiveName, b);
    }

    @Override
    public String toString() {
        return primitiveName + (isNullable ? "/nullable" : "/non-null");
    }
}
