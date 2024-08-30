package org.ethelred.kiwiproc.processor.types;

public record ContainerType(ValidContainerType type, KiwiType containedType) implements KiwiType {

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
        return false;
    }
}
