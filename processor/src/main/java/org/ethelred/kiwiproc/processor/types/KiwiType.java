/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

/**
 * Simplified view of type for parameters and return.
 */
public sealed interface KiwiType
        permits BasicType, ContainerType, PrimitiveKiwiType, RecordType, SqlArrayType, UnsupportedType, VoidType {
    static KiwiType unsupported() {
        return new UnsupportedType();
    }

    String packageName();

    String className();

    boolean isSimple();

    default boolean isNullable() {
        return false;
    }

    default KiwiType withIsNullable(boolean b) {
        return this;
    }
}
