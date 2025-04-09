/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

public record OptionalType(Class<?> optionalClass, KiwiType containedType) implements KiwiType {
    @Override
    public String packageName() {
        return optionalClass.getPackageName();
    }

    @Override
    public String className() {
        return optionalClass.getSimpleName();
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public boolean isNullable() {
        return true; // behaves like a nullable
    }

    @Override
    public KiwiType valueComponentType() {
        return containedType.valueComponentType();
    }
}
