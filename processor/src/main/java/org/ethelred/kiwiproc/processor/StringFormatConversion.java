package org.ethelred.kiwiproc.processor;

import org.jspecify.annotations.Nullable;

public record StringFormatConversion(@Nullable String warning, String conversionFormat) implements Conversion {
    public boolean hasWarning() {
        return warning != null;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
