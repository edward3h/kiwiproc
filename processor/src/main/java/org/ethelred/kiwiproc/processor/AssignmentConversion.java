package org.ethelred.kiwiproc.processor;

import org.jspecify.annotations.Nullable;

/**
 * It's not really a conversion.
 */
public record AssignmentConversion() implements Conversion {
    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean hasWarning() {
        return false;
    }

    @Override
    public @Nullable String warning() {
        return null;
    }
}
