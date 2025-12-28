/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.processor.types.CollectionType;
import org.jspecify.annotations.Nullable;

public record CollectionConversion(
        CollectionType sourceType, CollectionType targetType, Conversion containedTypeConversion)
        implements Conversion {
    @Override
    public boolean isValid() {
        return containedTypeConversion.isValid();
    }

    @Override
    public boolean hasWarning() {
        return containedTypeConversion.hasWarning();
    }

    @Override
    public @Nullable String warning() {
        return containedTypeConversion.warning();
    }
}
