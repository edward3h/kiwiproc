package org.ethelred.kiwiproc.processor;

import org.jspecify.annotations.Nullable;

public record NullableSourceConversion(Conversion conversion) implements Conversion {
    @Override
    public boolean isValid() {
        return conversion.isValid();
    }

    @Override
    public boolean hasWarning() {
        return conversion.hasWarning();
    }

    @Override
    public @Nullable String warning() {
        return conversion.warning();
    }
}
