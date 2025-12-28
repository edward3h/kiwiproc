/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import org.ethelred.kiwiproc.processor.CoreTypes;

/**
 * Represent the duality of primitive and boxed types.
 * @param primitiveClass
 * @param isNullable
 */
public record PrimitiveKiwiType(Class<?> primitiveClass, boolean isNullable) implements KiwiType {
    public PrimitiveKiwiType {
        if (!CoreTypes.primitiveToBoxed.containsKey(primitiveClass)) {
            throw new IllegalArgumentException("Unknown primitive " + primitiveClass);
        }
    }

    public PrimitiveKiwiType(String primitiveName, boolean isNullable) {
        this(CoreTypes.primitiveNameToType.get(primitiveName), isNullable);
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
            return CoreTypes.primitiveToBoxed.get(primitiveClass).getSimpleName();
        }
        return primitiveClass.getSimpleName();
    }

    public Class<?> type() {
        if (isNullable) {
            return CoreTypes.primitiveToBoxed.get(primitiveClass);
        }
        return primitiveClass;
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public PrimitiveKiwiType withIsNullable(boolean b) {
        return new PrimitiveKiwiType(primitiveClass, b);
    }

    @Override
    public String toString() {
        return primitiveClass.getSimpleName() + (isNullable ? "/nullable" : "/non-null");
    }
}
