package com.bettingproject.qualification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Safe, structured quality reason linked to its immutable enrichment observation. */
public record QualityFinding(
        UUID id,
        UUID enrichmentObservationId,
        QualityIssueCode code,
        QualityEntityScope entityScope,
        String providerEntityId,
        Instant detectedAt) {

    public QualityFinding {
        id = Objects.requireNonNull(id, "id");
        enrichmentObservationId = Objects.requireNonNull(enrichmentObservationId, "enrichmentObservationId");
        code = Objects.requireNonNull(code, "code");
        entityScope = Objects.requireNonNull(entityScope, "entityScope");
        if (providerEntityId != null
                && providerEntityId.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("providerEntityId must not contain control characters");
        }
        if (providerEntityId != null && providerEntityId.isBlank()) {
            providerEntityId = null;
        } else if (providerEntityId != null && providerEntityId.length() > 200) {
            throw new IllegalArgumentException("providerEntityId is invalid");
        }
        detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
    }
}
