/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

public record EnumType(String packageName, String className, boolean isNullable) implements KiwiType {
    @Override
    public String toString() {
        return className + "(enum)" + (isNullable ? "/nullable" : "/non-null");
    }

    @Override
    public EnumType withIsNullable(boolean newValue) {
        return new EnumType(packageName, className, newValue);
    }

    @Override
    public boolean isSimple() {
        return true;
    }
}
