package org.ethelred.kiwiproc.impl;

import org.ethelred.kiwiproc.api.Batch;
import org.ethelred.kiwiproc.api.Batchable;
import org.ethelred.kiwiproc.api.DAORunnable;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractBatch<T extends Batchable<T>> implements Batch<T>
{
    private final Map<BatchId, PreparedStatement> batches = new ConcurrentHashMap<>();

    protected AbstractBatch(){}

    protected abstract DeferredContext<T> newBatchContext();
    @Override
    public BatchId addBatch(DAORunnable<T> consumer) throws SQLException {
        var context = newBatchContext();
        var toAdd = context.run(consumer);
        toAdd.setter().accept(
                batches.computeIfAbsent(toAdd.batchId(), b -> toAdd.prepare().get())
        );
        return toAdd.batchId();
    }

    @Override
    public void close() throws Exception {
        batches.values().forEach(this::closeOne);
    }

    private void closeOne(PreparedStatement preparedStatement) {
        try {
            preparedStatement.close();
        } catch (SQLException e) {
            // ignore
        }
    }

    @Override
    public Map<BatchId, int[]> execute() throws SQLException {
        try {
            return batches.entrySet()
                    .stream()
                    .map(this::executeOne)
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (UncheckedSQLException e) {
            throw e.getCause();
        }
    }

    private Map.Entry<BatchId, int[]> executeOne(Map.Entry<BatchId, PreparedStatement> batchIdPreparedStatementEntry) {
        try {
            var stmt = batchIdPreparedStatementEntry.getValue();
            var result = stmt.executeBatch();
            return Map.entry(
                    batchIdPreparedStatementEntry.getKey(),
                    result
            );
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    protected interface DeferredContext<T> {
        DeferredAddBatch run(DAORunnable<T> daoRunnable) throws SQLException;
    }

    protected record DeferredAddBatch(BatchId batchId, Supplier<PreparedStatement> prepare, Consumer<PreparedStatement> setter){}
}
