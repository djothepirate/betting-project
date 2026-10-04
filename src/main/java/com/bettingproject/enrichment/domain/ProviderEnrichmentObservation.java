package com.bettingproject.enrichment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Provenance envelope for one parsed family. Provider values and the canonical route stay
 * separate; the envelope never contains raw payload bytes or authenticated request URLs.
 */
public record ProviderEnrichmentObservation(
        UUID id,
        UUID admissionId,
        UUID canonicalFixtureId,
        UUID budgetIntentId,
        UUID rawSnapshotId,
        String provider,
        String providerFixtureId,
        String logicalCompetition,
        String logicalSeason,
        String logicalPhase,
        String sourceSeasonReference,
        String sourcePhaseReference,
        EnrichmentFamily family,
        EnrichmentObservationState state,
        String payloadSha256,
        String parserVersion,
        Instant requestedAt,
        Instant receivedAt,
        Instant sourceObservedAt) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public ProviderEnrichmentObservation {
        id = Objects.requireNonNull(id, "id");
        admissionId = Objects.requireNonNull(admissionId, "admissionId");
        canonicalFixtureId = Objects.requireNonNull(canonicalFixtureId, "canonicalFixtureId");
        budgetIntentId = Objects.requireNonNull(budgetIntentId, "budgetIntentId");
        rawSnapshotId = Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
        provider = requireText(provider, "provider", 64);
        providerFixtureId = requireText(providerFixtureId, "providerFixtureId", 200);
        logicalCompetition = requireText(logicalCompetition, "logicalCompetition", 64);
        logicalSeason = requireText(logicalSeason, "logicalSeason", 64);
        logicalPhase = logicalPhase == null ? "" : logicalPhase.trim();
        if (logicalPhase.length() > 64 || containsControl(logicalPhase)) {
            throw new IllegalArgumentException("logicalPhase is invalid");
        }
        sourceSeasonReference = optionalText(sourceSeasonReference, 128);
        sourcePhaseReference = optionalText(sourcePhaseReference, 128);
        family = Objects.requireNonNull(family, "family");
        state = Objects.requireNonNull(state, "state");
        payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256");
        if (!SHA256.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be lowercase hexadecimal SHA-256");
        }
        parserVersion = requireText(parserVersion, "parserVersion", 64);
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        if (receivedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("receivedAt must not precede requestedAt");
        }
    }

    private static String requireText(String value, String name, int maxLength) {
        String normalized = optionalText(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (containsControl(value)) {
            throw new IllegalArgumentException("text must not contain control characters");
        }
        if (value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("text exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
