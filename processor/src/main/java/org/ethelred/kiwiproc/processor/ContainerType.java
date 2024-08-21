package org.ethelred.kiwiproc.processor;

public record ContainerType(ValidContainerType type, KiwiType containedType) implements KiwiType {

    @Override
    public String packageName() {
        return type.javaType().getPackageName();
    }

    @Override
    public String className() {
        return type.javaType().getSimpleName();
    }
}
