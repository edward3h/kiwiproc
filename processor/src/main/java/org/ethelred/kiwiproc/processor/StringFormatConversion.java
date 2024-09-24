package org.ethelred.kiwiproc.processor;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

public record StringFormatConversion(@Nullable String warning, String conversionFormat, Object... baseArgs)
        implements Conversion {
    public boolean hasWarning() {
        return warning != null;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public Object[] withAccessor(String accessor) {
        var r = Arrays.copyOf(baseArgs, baseArgs.length + 1);
        r[baseArgs.length] = accessor;
        return r;
    }
}
