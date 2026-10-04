package com.bettingproject.collection.application.enrichment;

import java.util.function.Supplier;

/** Guards each durable boundary of a managed enrichment execution. */
public interface EnrichmentExecution {
    EnrichmentExecution DIRECT = new EnrichmentExecution() {
        @Override public <T> T atomic(Supplier<T> work) { return work.get(); }
    };

    <T> T atomic(Supplier<T> work);
}
