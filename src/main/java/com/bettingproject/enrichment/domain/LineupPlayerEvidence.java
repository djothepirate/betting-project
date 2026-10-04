package com.bettingproject.enrichment.domain;

import java.util.Objects;

/** Identity fields explicitly present in one source lineup row; absence is never filled in. */
public record LineupPlayerEvidence(String providerPlayerId, ObservedScalar fullName, ObservedScalar role) {
    public LineupPlayerEvidence {
        if (providerPlayerId == null || providerPlayerId.length() > 200
                || providerPlayerId.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("providerPlayerId is invalid");
        }
        fullName = Objects.requireNonNull(fullName, "fullName");
        role = Objects.requireNonNull(role, "role");
        if (fullName.state() == EnrichmentObservationState.AVAILABLE
                && fullName.type() != ObservedScalarType.TEXT
                || role.state() == EnrichmentObservationState.AVAILABLE
                && role.type() != ObservedScalarType.TEXT) {
            throw new IllegalArgumentException("lineup identity evidence must be text when available");
        }
    }
}
