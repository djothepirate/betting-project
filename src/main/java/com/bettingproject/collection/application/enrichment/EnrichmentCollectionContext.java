package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.bettingproject.enrichment.domain.EnrichmentPlanStep;

/** Immutable admission, scheduled-step, and canonical-route data needed to resolve a provider call. */
public record EnrichmentCollectionContext(UUID planId, UUID admissionId, UUID canonicalFixtureId,
        UUID planBudgetWindowId, String planRegistrySha256, Instant kickoffAt,
        String logicalCompetition, String logicalSeason, String logicalPhase,
        String authorityProvider, String authorityCompetitionId, String authoritySourceSeason,
        String authoritySourcePhase, String authorityProviderFixtureId, EnrichmentPlanStep step) {
    public EnrichmentCollectionContext {
        Objects.requireNonNull(planId);
        Objects.requireNonNull(admissionId);
        Objects.requireNonNull(canonicalFixtureId);
        Objects.requireNonNull(planBudgetWindowId);
        Objects.requireNonNull(kickoffAt);
        Objects.requireNonNull(authorityProvider);
        Objects.requireNonNull(authorityCompetitionId);
        Objects.requireNonNull(authoritySourceSeason);
        Objects.requireNonNull(authoritySourcePhase);
        Objects.requireNonNull(authorityProviderFixtureId);
        Objects.requireNonNull(step);
        logicalCompetition = require(logicalCompetition, "logicalCompetition");
        logicalSeason = require(logicalSeason, "logicalSeason");
        logicalPhase = logicalPhase == null ? "" : logicalPhase;
        if (!logicalPhase.equals(logicalPhase.trim()) || logicalPhase.length() > 64
                || logicalPhase.codePoints().anyMatch(Character::isISOControl)
                || planRegistrySha256 == null || !planRegistrySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid logical route or registry fingerprint");
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 64
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
