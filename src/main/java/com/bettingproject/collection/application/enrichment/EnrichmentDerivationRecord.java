package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.bettingproject.enrichment.domain.EnrichmentFamily;

public record EnrichmentDerivationRecord(UUID id, UUID attemptId, EnrichmentFamily family,
        String parserVersion, String outcome, UUID observationId, String rawSha256, Instant evaluatedAt) {
    public EnrichmentDerivationRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(attemptId);
        Objects.requireNonNull(family);
        if (parserVersion == null || parserVersion.isBlank()
                || !Objects.requireNonNull(outcome).matches("[A-Z][A-Z_]{0,63}")
                || rawSha256 == null || !rawSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid derivation audit");
        }
        Objects.requireNonNull(evaluatedAt);
    }
}
