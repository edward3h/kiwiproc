package org.ethelred.kiwiproc.processor;

public record VoidType() implements KiwiType {

    @Override
    public String packageName() {
        return "";
    }

    @Override
    public String className() {
        return "void";
    }
}
