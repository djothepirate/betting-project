package com.bettingproject.collection.application.enrichment;

public interface EnrichmentObservationStore {
    /** Stores or resolves an identical derivation and appends any not-yet-recorded findings atomically. */
    StoredEnrichmentObservation storeAndResolve(EnrichmentObservationWrite observation);
}
