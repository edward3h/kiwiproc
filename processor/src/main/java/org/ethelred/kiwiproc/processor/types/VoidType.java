/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

public record VoidType() implements KiwiType {

    @Override
    public String packageName() {
        return "";
    }

    @Override
    public String className() {
        return "void";
    }

    @Override
    public boolean isSimple() {
        return false;
    }
}
