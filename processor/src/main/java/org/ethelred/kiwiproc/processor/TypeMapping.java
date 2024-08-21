package org.ethelred.kiwiproc.processor;

public record TypeMapping(SimpleType source, SimpleType target) {

    public static TypeMapping of(KiwiType source, KiwiType target) {
        if (source instanceof SimpleType simpleSource && target instanceof SimpleType simpleTarget) {
            return new TypeMapping(simpleSource, simpleTarget);
        }
        throw new IllegalArgumentException("TypeMapping requires simple types (%s) -> (%s)".formatted(source, target));
    }
}
