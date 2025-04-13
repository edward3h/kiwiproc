/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import java.util.List;
import java.util.Optional;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.meta.JavaName;
import org.ethelred.kiwiproc.meta.SqlName;
import org.jspecify.annotations.Nullable;

public record QueryResultContext(
        QueryMethodKind kind,
        List<ColumnMetaData> columns,
        @Nullable String keyColumn,
        @Nullable String valueColumn,
        ResultPart resultPart,
        boolean asParameter) {

    public static final String DEFAULT_KEY_COLUMN = "key";
    public static final String DEFAULT_VALUE_COLUMN = "value";

    public QueryResultContext(
            QueryMethodKind kind,
            List<ColumnMetaData> columns,
            @Nullable String keyColumn,
            @Nullable String valueColumn) {
        this(kind, columns, keyColumn, valueColumn, ResultPart.SIMPLE, false);
    }

    public Optional<ColumnMetaData> getSingleMatchingColumn() {
        return switch (resultPart) {
            case SIMPLE -> columns.size() == 1 ? Optional.of(columns.get(0)) : Optional.empty();
            case KEY -> first(keyColumn).or(() -> first(DEFAULT_KEY_COLUMN));
            case VALUE -> first(valueColumn).or(() -> first(DEFAULT_VALUE_COLUMN));
        };
    }

    private Optional<ColumnMetaData> first(@Nullable String column) {
        if (column == null) {
            return Optional.empty();
        }
        var asSql = new SqlName(column);
        return columns.stream()
                .filter(columnMetaData -> columnMetaData.name().equals(asSql))
                .findFirst();
    }

    public Optional<ColumnMetaData> getMatchingColumn(JavaName name) {
        return columns.stream()
                .filter(columnMetaData -> columnMetaData.name().equivalent(name))
                .findFirst();
    }

    public QueryResultContext withMapMapping(ResultPart resultPart) {
        return new QueryResultContext(kind, columns, keyColumn, valueColumn, resultPart, asParameter);
    }

    public QueryResultContext withAsParameter(boolean b) {
        return new QueryResultContext(kind, columns, keyColumn, valueColumn, resultPart, b);
    }
}
