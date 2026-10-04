package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;

/** Durable single-send audit projection. It contains logical IDs and hashes, never an authenticated URL. */
public record EnrichmentCollectionAttempt(UUID id, UUID admissionId, UUID stepId,
        EnrichmentPlanStepCode stepCode, UUID canonicalFixtureId, UUID budgetWindowId,
        UUID budgetIntentId, UUID auditId, ProviderCapabilityKey capability,
        String logicalCompetition, String logicalSeason, String logicalPhase,
        String providerFixtureId, EnrichmentFamily family, String logicalEndpoint,
        String connectorVersion, String parserVersion, String requestSha256,
        EnrichmentAttemptState state, UUID rawSnapshotId, String payloadSha256,
        Instant kickoffAt, Instant requestedAt, Instant receivedAt, Instant resultRecordedAt,
        Integer httpStatus, Long quotaRemaining, String reasonCode,
        Instant createdAt, Instant updatedAt) {
    public EnrichmentCollectionAttempt {
        Objects.requireNonNull(id);
        Objects.requireNonNull(admissionId);
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(stepCode);
        Objects.requireNonNull(canonicalFixtureId);
        Objects.requireNonNull(budgetWindowId);
        Objects.requireNonNull(budgetIntentId);
        Objects.requireNonNull(auditId);
        Objects.requireNonNull(capability);
        logicalCompetition = text(logicalCompetition, "logicalCompetition", 64);
        logicalSeason = text(logicalSeason, "logicalSeason", 64);
        logicalPhase = logicalPhase == null ? "" : logicalPhase;
        if (logicalPhase.length() > 64 || logicalPhase.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("logicalPhase is invalid");
        }
        providerFixtureId = text(providerFixtureId, "providerFixtureId", 200);
        Objects.requireNonNull(family);
        logicalEndpoint = text(logicalEndpoint, "logicalEndpoint", 200);
        connectorVersion = text(connectorVersion, "connectorVersion", 64);
        parserVersion = text(parserVersion, "parserVersion", 64);
        if (requestSha256 == null || !requestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestSha256 is invalid");
        }
        Objects.requireNonNull(state);
        Objects.requireNonNull(kickoffAt);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (updatedAt.isBefore(createdAt) || (payloadSha256 != null && !payloadSha256.matches("[0-9a-f]{64}"))
                || (quotaRemaining != null && quotaRemaining < 0)
                || (httpStatus != null && (httpStatus < 100 || httpStatus > 599))) {
            throw new IllegalArgumentException("invalid enrichment attempt evidence");
        }
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max
                || !value.equals(value.trim()) || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
