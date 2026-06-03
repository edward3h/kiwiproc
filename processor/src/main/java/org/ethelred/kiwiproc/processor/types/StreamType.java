/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processor.types;

import org.ethelred.kiwiproc.processor.RowCount;

public record StreamType(KiwiType containedType) implements KiwiType {

    @Override
    public String packageName() {
        return "java.util.stream";
    }

    @Override
    public String className() {
        return "Stream";
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public RowCount expectedRows() {
        return RowCount.MANY;
    }

    @Override
    public KiwiType valueComponentType() {
        return containedType.valueComponentType();
    }

    @Override
    public String toString() {
        return "Stream<" + containedType + ">";
    }
}
