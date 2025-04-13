/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import org.jspecify.annotations.Nullable;

public record VoidConversion() implements Conversion {
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
