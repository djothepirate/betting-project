package com.bettingproject.collection.application.enrichment;

import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal bounded read of a previously derived representation for deterministic comparison. */
public record StoredEnrichmentQualityEvidence(UUID observationId, String provider, String providerFixtureId,
        String logicalCompetition, String logicalSeason, String logicalPhase, EnrichmentFamily family,
        EnrichmentObservationState state, String parserVersion, Instant receivedAt, String representationJson) {
    public StoredEnrichmentQualityEvidence {
        Objects.requireNonNull(observationId, "observationId");
        provider = required(provider, "provider", 64);
        providerFixtureId = required(providerFixtureId, "providerFixtureId", 200);
        logicalCompetition = required(logicalCompetition, "logicalCompetition", 64);
        logicalSeason = required(logicalSeason, "logicalSeason", 64);
        logicalPhase = Objects.requireNonNull(logicalPhase, "logicalPhase");
        family = Objects.requireNonNull(family, "family");
        state = Objects.requireNonNull(state, "state");
        parserVersion = required(parserVersion, "parserVersion", 64);
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        if (representationJson == null || representationJson.isBlank()) {
            throw new IllegalArgumentException("representationJson must not be blank");
        }
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
