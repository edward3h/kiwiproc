package org.ethelred.kiwiproc.processor;

public record UnsupportedType() implements KiwiType {
    @Override
    public String packageName() {
        throw new UnsupportedOperationException("UnsupportedType.packageName");
    }

    @Override
    public String className() {
        throw new UnsupportedOperationException("UnsupportedType.className");
    }
}
