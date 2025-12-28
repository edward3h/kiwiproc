/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class DAOResultMapping {
    public static DAOResultMapping INVALID = new DAOResultMapping(new InvalidConversion(null, null));
    private @Nullable Conversion conversion;

    private final List<DAOResultColumn> columns = new ArrayList<>();

    public DAOResultMapping() {}

    public DAOResultMapping(Conversion conversion) {
        this.conversion = conversion;
    }

    public DAOResultMapping(DAOResultColumn column) {
        addColumn(column);
    }

    public boolean isValid() {
        return (conversion == null || conversion.isValid())
                && columns.stream().allMatch(c -> c.conversion().isValid());
    }

    public void addColumn(DAOResultColumn valueColumn) {
        columns.add(valueColumn);
    }

    public List<DAOResultColumn> getColumns() {
        return columns;
    }

    public Optional<Conversion> getConversion() {
        return Optional.ofNullable(conversion);
    }

    public DAOResultMapping merge(DAOResultMapping other) {
        columns.addAll(other.columns);
        return this;
    }
}
