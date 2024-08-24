package org.ethelred.kiwiproc.processor;

public record SqlArrayType(KiwiType componentType) implements KiwiType {
    @Override
    public String packageName() {
        return "";
    }

    @Override
    public String className() {
        return "ARRAY";
    }
}
