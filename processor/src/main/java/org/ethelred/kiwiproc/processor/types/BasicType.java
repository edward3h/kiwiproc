package org.ethelred.kiwiproc.processor.types;

public record BasicType(String packageName, String className, boolean isNullable) implements KiwiType {
    @Override
    public String toString() {
        return className + (isNullable ? "/nullable" : "/non-null");
    }

    public BasicType withIsNullable(boolean newValue) {
        return new BasicType(packageName, className, newValue);
    }

    @Override
    public boolean isSimple() {
        return true;
    }
}
