package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FixtureAuthorityStamp(
        UUID observationId,
        Instant observedAt,
        String provider,
        String policyVersion) {

    public FixtureAuthorityStamp {
        observationId = Objects.requireNonNull(observationId, "observationId");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        provider = requireText(provider, "provider");
        policyVersion = requireText(policyVersion, "policyVersion");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
