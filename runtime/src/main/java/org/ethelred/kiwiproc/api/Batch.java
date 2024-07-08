package org.ethelred.kiwiproc.api;

import java.sql.SQLException;
import java.util.Map;

public interface Batch<T extends Batchable<T>> extends AutoCloseable {
    Map<BatchId, int[]> execute() throws SQLException;

    BatchId addBatch(DAORunnable<T> consumer) throws SQLException;

    record BatchId(String id){}
}
