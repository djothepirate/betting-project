package com.bettingproject.collection.domain.capability;

import java.time.Instant;
import java.util.Objects;

public record CapabilityEvidenceReference(String logicalId, Instant observedAt, String sha256) {
    public CapabilityEvidenceReference {
        logicalId = CapabilityText.exact(logicalId, "logicalId", 200);
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid capability evidence hash");
        }
    }
}
