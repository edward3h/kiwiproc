package org.ethelred.kiwiproc.processor;

public record SimpleType(String packageName, String className, boolean isNullable) implements KiwiType {
    public static SimpleType ofClass(Class<?> aClass, boolean isNullable) {
        var packageName = aClass.getPackageName();

        if (aClass.isPrimitive()) {
            packageName = "";
        }

        return new SimpleType(packageName, aClass.getSimpleName(), isNullable);
    }

    public static SimpleType ofClass(Class<?> aClass) {
        return ofClass(aClass, false);
    }

    @Override
    public String toString() {
        return className + (isNullable ? "/nullable" : "/non-null");
    }

    public SimpleType withIsNullable(boolean newValue) {
        return new SimpleType(packageName, className, newValue);
    }
}
