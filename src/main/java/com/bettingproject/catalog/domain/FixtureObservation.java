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
        String sourceSchemaVersion,
        String sourceSeason,
        Boolean sourceNeutralVenue,
        boolean sourceParticipantsUnordered,
        Instant sourceKickoff,
        String sourceStatus,
        String sourcePhase,
        ObservationStatus normalizationStatus,
        String reasonCode,
        Instant observedAt,
        Instant createdAt) {

    private static final String SCHEMA_V2 = "cal01-fixture-v2";
    private static final String SCHEMA_V3 = "cal01-fixture-v3";

    public FixtureObservation {
        id = Objects.requireNonNull(id, "id");
        rawSnapshotId = Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
        provider = requireText(provider, "provider");
        providerFixtureId = requireText(providerFixtureId, "providerFixtureId");
        sourceSchemaVersion = requireText(sourceSchemaVersion, "sourceSchemaVersion");
        if (!SCHEMA_V2.equals(sourceSchemaVersion) && !SCHEMA_V3.equals(sourceSchemaVersion)) {
            throw new IllegalArgumentException("unsupported sourceSchemaVersion: " + sourceSchemaVersion);
        }
        sourceSeason = normalizeOptionalText(sourceSeason);
        sourceKickoff = Objects.requireNonNull(sourceKickoff, "sourceKickoff");
        sourceStatus = requireText(sourceStatus, "sourceStatus");
        sourcePhase = sourcePhase == null ? "" : sourcePhase.trim();
        normalizationStatus = Objects.requireNonNull(normalizationStatus, "normalizationStatus");
        reasonCode = normalizeOptionalText(reasonCode);
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");

        if (normalizationStatus == ObservationStatus.NORMALIZED) {
            if (canonicalFixtureId == null) {
                throw new IllegalArgumentException("canonicalFixtureId is required for a normalized observation");
            }
            if (reasonCode != null) {
                throw new IllegalArgumentException("a normalized observation must not have a reasonCode");
            }
        }
        else if (reasonCode == null) {
            throw new IllegalArgumentException("reasonCode is required for a blocked or rejected observation");
        }
    }

    public FixtureObservation(
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
        this(
                id,
                rawSnapshotId,
                canonicalFixtureId,
                provider,
                providerFixtureId,
                providerCompetitionId,
                providerHomeTeamId,
                providerAwayTeamId,
                SCHEMA_V2,
                null,
                null,
                false,
                sourceKickoff,
                sourceStatus,
                sourcePhase,
                normalizationStatus,
                reasonCode,
                observedAt,
                createdAt);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public enum ObservationStatus {
        NORMALIZED,
        BLOCKED,
        REJECTED
    }
}
