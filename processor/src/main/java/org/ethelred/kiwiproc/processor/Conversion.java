/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import org.jspecify.annotations.Nullable;

public sealed interface Conversion
        permits AssignmentConversion,
                EnumFromStringConversion,
                EnumToStringConversion,
                FromSqlArrayConversion,
                InvalidConversion,
                NullableSourceConversion,
                StringFormatConversion,
                ToSqlArrayConversion,
                VoidConversion,
                CollectionConversion {
    boolean isValid();

    boolean hasWarning();

    @Nullable String warning();
}
