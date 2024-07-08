package org.ethelred.kiwiproc.api;

public interface Batchable<T extends Batchable<T>> {
    Batch<T> startBatch();
}
