/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

public record UnsupportedType(String reason) implements KiwiType {
    public UnsupportedType() {
        this("unsupported type");
    }

    @Override
    public String packageName() {
        return "";
    }

    @Override
    public String className() {
        return "<unsupported>";
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public String toString() {
        return "UnsupportedType(" + reason + ")";
    }
}
