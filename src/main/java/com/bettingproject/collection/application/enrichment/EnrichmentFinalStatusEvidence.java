package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.util.Objects;

/** A final state accepted by the provider-specific policy, tied to the response receipt time. */
public record EnrichmentFinalStatusEvidence(Instant observedAt, String policyVersion) {
    public EnrichmentFinalStatusEvidence {
        Objects.requireNonNull(observedAt, "observedAt");
        if (policyVersion == null || policyVersion.isBlank() || !policyVersion.equals(policyVersion.trim())
                || policyVersion.length() > 64 || policyVersion.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid final status policy version");
        }
    }
}
