package org.ethelred.kiwiproc.meta;

import io.soabase.recordbuilder.core.RecordBuilderFull;

import java.util.List;

@RecordBuilderFull
public record QueryMetaData(
        List<ColumnMetaData> resultColumns,
        List<ColumnMetaData> parameters
) {
}
