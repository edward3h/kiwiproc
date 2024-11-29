/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.processor.types.ContainerType;
import org.ethelred.kiwiproc.processor.types.SqlArrayType;
import org.jspecify.annotations.Nullable;

public record FromSqlArrayConversion(SqlArrayType sat, ContainerType ct, Conversion elementConversion)
        implements Conversion {
    @Override
    public boolean isValid() {
        return elementConversion.isValid();
    }

    @Override
    public boolean hasWarning() {
        return elementConversion.hasWarning();
    }

    @Override
    public @Nullable String warning() {
        return elementConversion.warning();
    }
}
