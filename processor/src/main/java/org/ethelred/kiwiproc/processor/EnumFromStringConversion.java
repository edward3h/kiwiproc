/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.processor.types.EnumType;

public record EnumFromStringConversion(EnumType enumType) implements Conversion {
    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean hasWarning() {
        return true;
    }

    @Override
    public String warning() {
        return "possible IllegalArgumentException if value doesn't match any %s constant"
                .formatted(enumType.className());
    }
}
