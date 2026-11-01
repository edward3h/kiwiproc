/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import org.ethelred.kiwiproc.processor.RowCount;

public record CollectionType(As as, ValidCollection type, KiwiType containedType, boolean isNullable)
        implements KiwiType {

    public enum As {
        UNRESOLVED, // unknown, depends on whether the mapped column is SQL Array or not
        ROWS, // MUST map to a container of values for the type to be valid
        SIMPLE // MUST map to a SQL Array in order for the type to be valid
    }

    public CollectionType(As as, ValidCollection type, KiwiType containedType) {
        this(as, type, containedType, false);
    }

    public CollectionType(ValidCollection type, KiwiType containedType) {
        this(containedType instanceof RecordType ? As.ROWS : As.UNRESOLVED, type, containedType, false);
    }

    @Override
    public KiwiType withIsNullable(boolean b) {
        return new CollectionType(as, type, containedType, b);
    }

    @Override
    public String packageName() {
        return type.javaType().getPackageName();
    }

    @Override
    public String className() {
        return type.javaType().getSimpleName();
    }

    @Override
    public boolean isSimple() {
        return as == As.SIMPLE;
    }

    @Override
    public RowCount expectedRows() {
        // a collection read from a single SQL array column comes from exactly one row
        if (as == As.SIMPLE) {
            return isNullable ? RowCount.ZERO_OR_ONE : RowCount.EXACTLY_ONE;
        }
        return RowCount.MANY;
    }

    @Override
    public KiwiType valueComponentType() {
        // a SIMPLE collection is itself the value, read from a single SQL array column
        return as == As.SIMPLE ? this : containedType.valueComponentType();
    }

    @Override
    public String toString() {
        var prefix =
                switch (as) {
                    case UNRESOLVED -> "U";
                    case ROWS -> "";
                    case SIMPLE -> "S";
                };
        return prefix + className() + "<" + containedType + ">";
    }

    public KiwiType asSimple() {
        if (containedType.isSimple()) {
            return new CollectionType(As.SIMPLE, type, containedType, isNullable);
        }
        return KiwiType.unsupported();
    }
}
