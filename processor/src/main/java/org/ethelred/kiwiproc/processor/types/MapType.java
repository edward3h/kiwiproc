/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

import java.util.Map;
import org.ethelred.kiwiproc.processor.RowCount;

public record MapType(KiwiType keyType, KiwiType valueType, boolean comparableKey) implements KiwiType {
    @Override
    public String packageName() {
        return Map.class.getPackageName();
    }

    @Override
    public String className() {
        return Map.class.getSimpleName();
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public String toString() {
        return "Map<" + keyType + ", " + valueType + ">";
    }

    @Override
    public RowCount expectedRows() {
        return RowCount.MANY;
    }

    @Override
    public KiwiType valueComponentType() {
        return valueType.valueComponentType();
    }
}
