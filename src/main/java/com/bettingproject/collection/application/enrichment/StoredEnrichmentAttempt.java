package com.bettingproject.collection.application.enrichment;

import java.util.Objects;

public record StoredEnrichmentAttempt(EnrichmentCollectionAttempt attempt, boolean inserted) {
    public StoredEnrichmentAttempt { Objects.requireNonNull(attempt); }
}
