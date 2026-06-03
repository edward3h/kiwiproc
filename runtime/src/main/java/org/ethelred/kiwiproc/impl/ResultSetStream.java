/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;

public final class ResultSetStream {
    private ResultSetStream() {}

    public static <T> Stream<T> of(PreparedStatement statement, ResultSet rs, ResultSetMapper<T> mapper) {
        var spliterator = new Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(java.util.function.Consumer<? super T> action) {
                try {
                    if (rs.next()) {
                        action.accept(mapper.map(rs));
                        return true;
                    }
                    return false;
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                }
            }
        };
        return StreamSupport.stream(spliterator, false).onClose(() -> {
            try {
                statement.close();
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            }
        });
    }
}
