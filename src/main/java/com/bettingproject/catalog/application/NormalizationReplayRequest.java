package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record NormalizationReplayRequest(
        UUID id,
        UUID controlCommandReceiptId,
        UUID rawSnapshotId,
        String expectedPayloadSha256,
        UUID providerMappingDecisionId,
        NormalizationReplayOrigin origin,
        NormalizationReplaySelectorType selectorType,
        String selectorValue,
        NormalizationReplayStatus status,
        long version,
        int attemptCount,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public NormalizationReplayRequest {
        id = Objects.requireNonNull(id, "id");
        controlCommandReceiptId = Objects.requireNonNull(controlCommandReceiptId, "controlCommandReceiptId");
        rawSnapshotId = Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
        expectedPayloadSha256 = requireSha256(expectedPayloadSha256, "expectedPayloadSha256");
        origin = Objects.requireNonNull(origin, "origin");
        selectorType = Objects.requireNonNull(selectorType, "selectorType");
        selectorValue = requireSelector(selectorType, selectorValue);
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");

        if ((origin == NormalizationReplayOrigin.MAPPING_DECISION) != (providerMappingDecisionId != null)) {
            throw new IllegalArgumentException(
                    "providerMappingDecisionId is required only for MAPPING_DECISION requests");
        }
        if (origin == NormalizationReplayOrigin.MAPPING_DECISION
                && selectorType != NormalizationReplaySelectorType.SNAPSHOT_ID) {
            throw new IllegalArgumentException("mapping-decision replay must select a snapshot UUID");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        if ((status == NormalizationReplayStatus.PENDING) != (attemptCount == 0)) {
            throw new IllegalArgumentException(
                    "only a pending request can have no replay attempt");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        if (status.terminal() != (completedAt != null)) {
            throw new IllegalArgumentException("completedAt must be present exactly for a terminal status");
        }
        if (completedAt != null && completedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("completedAt must not precede createdAt");
        }

        boolean failed = status == NormalizationReplayStatus.FAILED_RETRYABLE
                || status == NormalizationReplayStatus.FAILED_TERMINAL;
        lastErrorCode = normalizeOptionalText(lastErrorCode);
        lastErrorMessage = normalizeOptionalText(lastErrorMessage);
        if (failed != (lastErrorCode != null && lastErrorMessage != null)) {
            throw new IllegalArgumentException("failure details must be present exactly for a failed status");
        }
        if (!failed && (lastErrorCode != null || lastErrorMessage != null)) {
            throw new IllegalArgumentException("failure details are forbidden for a successful status");
        }
        requireMaximum(lastErrorCode, 100, "lastErrorCode");
        requireMaximum(lastErrorMessage, 2000, "lastErrorMessage");
    }

    public static NormalizationReplayRequest pending(
            UUID id,
            UUID controlCommandReceiptId,
            UUID rawSnapshotId,
            String expectedPayloadSha256,
            UUID providerMappingDecisionId,
            NormalizationReplayOrigin origin,
            NormalizationReplaySelectorType selectorType,
            String selectorValue,
            Instant now) {
        return new NormalizationReplayRequest(
                id,
                controlCommandReceiptId,
                rawSnapshotId,
                expectedPayloadSha256,
                providerMappingDecisionId,
                origin,
                selectorType,
                selectorValue,
                NormalizationReplayStatus.PENDING,
                1,
                0,
                null,
                null,
                now,
                now,
                null);
    }

    private static String requireSelector(NormalizationReplaySelectorType type, String value) {
        if (value == null) {
            throw new IllegalArgumentException("selectorValue must not be null");
        }
        return switch (type) {
            case SNAPSHOT_ID -> UUID.fromString(value).toString();
            case PAYLOAD_SHA256 -> requireSha256(value, "selectorValue");
        };
    }

    private static String requireSha256(String value, String name) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void requireMaximum(String value, int maximum, String name) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(name + " must not exceed " + maximum + " characters");
        }
    }
}
