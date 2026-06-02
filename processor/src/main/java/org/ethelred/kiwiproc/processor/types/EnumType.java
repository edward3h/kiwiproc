/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import java.util.List;

public record EnumType(String packageName, String className, boolean isNullable, List<String> constants)
        implements KiwiType {

    public EnumType(String packageName, String className, boolean isNullable) {
        this(packageName, className, isNullable, List.of());
    }

    @Override
    public String toString() {
        return className + "(enum)" + (isNullable ? "/nullable" : "/non-null");
    }

    @Override
    public EnumType withIsNullable(boolean newValue) {
        return new EnumType(packageName, className, newValue, constants);
    }

    @Override
    public boolean isSimple() {
        return true;
    }
}
