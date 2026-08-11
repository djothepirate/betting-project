package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FixtureObservation(
        UUID id,
        UUID rawSnapshotId,
        UUID canonicalFixtureId,
        String provider,
        String providerFixtureId,
        String providerCompetitionId,
        String providerHomeTeamId,
        String providerAwayTeamId,
        Instant sourceKickoff,
        String sourceStatus,
        String sourcePhase,
        ObservationStatus normalizationStatus,
        String reasonCode,
        Instant observedAt,
        Instant createdAt) {

    public FixtureObservation {
        id = Objects.requireNonNull(id, "id");
        rawSnapshotId = Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
        provider = requireText(provider, "provider");
        providerFixtureId = requireText(providerFixtureId, "providerFixtureId");
        sourceKickoff = Objects.requireNonNull(sourceKickoff, "sourceKickoff");
        sourceStatus = requireText(sourceStatus, "sourceStatus");
        sourcePhase = sourcePhase == null ? "" : sourcePhase.trim();
        normalizationStatus = Objects.requireNonNull(normalizationStatus, "normalizationStatus");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public enum ObservationStatus {
        NORMALIZED,
        BLOCKED,
        REJECTED
    }
}
