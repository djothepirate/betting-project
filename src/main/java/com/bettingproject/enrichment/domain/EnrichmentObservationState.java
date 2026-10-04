package com.bettingproject.enrichment.domain;

/** Structural state of a family in a successfully received provider response. */
public enum EnrichmentObservationState {
    NOT_PRESENT,
    NULL_VALUE,
    EMPTY,
    AVAILABLE,
    PARTIAL,
    INCOMPATIBLE
}
