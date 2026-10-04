package com.bettingproject.collection.application.enrichment;

import java.util.Optional;
import java.util.UUID;

public interface EnrichmentJobInputStore {
    void insertIfAbsentAndResolve(EnrichmentJobInput input);

    Optional<EnrichmentJobInput> find(UUID jobId);
}
