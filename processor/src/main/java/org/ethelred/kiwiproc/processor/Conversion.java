/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import org.jspecify.annotations.Nullable;

public sealed interface Conversion
        permits AssignmentConversion,
                FromSqlArrayConversion,
                InvalidConversion,
                NullableSourceConversion,
                StringFormatConversion,
                ToSqlArrayConversion {
    boolean isValid();

    boolean hasWarning();

    @Nullable String warning();
}
