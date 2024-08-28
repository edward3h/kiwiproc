package org.ethelred.kiwiproc.processor.types;

public record SqlArrayType(KiwiType componentType) implements KiwiType {
    @Override
    public String packageName() {
        return "";
    }

    @Override
    public String className() {
        return "ARRAY";
    }

    @Override
    public boolean isSimple() {
        return true; // kinda
    }
}
