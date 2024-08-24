package org.ethelred.kiwiproc.processor;

/**
 * Simplified view of type for parameters and return.
 */
public sealed interface KiwiType
        permits ContainerType, RecordType, SimpleType, SqlArrayType, UnsupportedType, VoidType {
    static KiwiType unsupported() {
        return new UnsupportedType();
    }

    String packageName();

    String className();
}
