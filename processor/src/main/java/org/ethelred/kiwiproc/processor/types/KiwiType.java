/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import org.ethelred.kiwiproc.processor.RowCount;

/**
 * Simplified view of type for parameters and return.
 */
public sealed interface KiwiType
        permits CollectionType,
                MapType,
                ObjectType,
                OptionalType,
                PrimitiveKiwiType,
                RecordType,
                SqlArrayType,
                UnsupportedType,
                VoidType {
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

    default RowCount expectedRows() {
        return isNullable() ? RowCount.ZERO_OR_ONE : RowCount.EXACTLY_ONE;
    }

    default KiwiType valueComponentType() {
        return this;
    }
}
