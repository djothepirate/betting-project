package com.bettingproject.collection.application.enrichment;

import com.bettingproject.enrichment.domain.EnrichmentFamily;

public interface EnrichmentPayloadParser<T> {
    String provider();

    EnrichmentFamily family();

    String version();

    /** Whether this version may be selected for a newly planned collection. */
    default boolean preferredForCollection() { return true; }

    T parse(byte[] payload, java.time.Instant scheduledKickoff, java.time.Instant receivedAt);
}
