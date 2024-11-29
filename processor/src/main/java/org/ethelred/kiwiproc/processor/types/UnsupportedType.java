/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

public record UnsupportedType() implements KiwiType {
    @Override
    public String packageName() {
        throw new UnsupportedOperationException("UnsupportedType.packageName");
    }

    @Override
    public String className() {
        throw new UnsupportedOperationException("UnsupportedType.className");
    }

    @Override
    public boolean isSimple() {
        return false;
    }
}
