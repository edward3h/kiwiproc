/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

public record ObjectType(String packageName, String className, boolean isNullable) implements KiwiType {
    @Override
    public String toString() {
        return className + (isNullable ? "/nullable" : "/non-null");
    }

    public ObjectType withIsNullable(boolean newValue) {
        return new ObjectType(packageName, className, newValue);
    }

    @Override
    public boolean isSimple() {
        return true;
    }
}
