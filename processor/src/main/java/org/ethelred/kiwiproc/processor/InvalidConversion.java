/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.jspecify.annotations.Nullable;

public record InvalidConversion(@Nullable KiwiType source,
                                @Nullable KiwiType target) implements Conversion {
    @Override
    public boolean isValid() {
        return false;
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
