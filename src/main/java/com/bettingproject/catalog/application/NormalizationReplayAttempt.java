package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record NormalizationReplayAttempt(
        UUID id,
        UUID requestId,
        int attemptNumber,
        NormalizationReplayAttemptOutcome outcome,
        String expectedPayloadSha256,
        String actualPayloadSha256,
        Boolean compatible,
        Integer fixturesCreated,
        Integer fixturesUpdated,
        Integer fixturesUnchanged,
        Integer fixturesBlocked,
        Integer anomalies,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public NormalizationReplayAttempt {
        id = Objects.requireNonNull(id, "id");
        requestId = Objects.requireNonNull(requestId, "requestId");
        outcome = Objects.requireNonNull(outcome, "outcome");
        expectedPayloadSha256 = requireSha256(expectedPayloadSha256, "expectedPayloadSha256");
        actualPayloadSha256 = requireSha256(actualPayloadSha256, "actualPayloadSha256");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");

        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be at least 1");
        }
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not precede startedAt");
        }

        errorCode = normalizeOptionalText(errorCode);
        errorMessage = normalizeOptionalText(errorMessage);
        if (outcome == NormalizationReplayAttemptOutcome.COMPLETED) {
            requireCounters(compatible, fixturesCreated, fixturesUpdated, fixturesUnchanged, fixturesBlocked, anomalies);
            if (errorCode != null || errorMessage != null) {
                throw new IllegalArgumentException("a completed attempt must not contain an error");
            }
        } else {
            if (compatible != null || fixturesCreated != null || fixturesUpdated != null
                    || fixturesUnchanged != null || fixturesBlocked != null || anomalies != null) {
                throw new IllegalArgumentException("a failed attempt must not contain normalization counters");
            }
            if (errorCode == null || errorMessage == null) {
                throw new IllegalArgumentException("a failed attempt requires an error code and message");
            }
            if (errorCode.length() > 100 || errorMessage.length() > 2000) {
                throw new IllegalArgumentException("replay attempt error exceeds its maximum length");
            }
        }
    }

    public static NormalizationReplayAttempt completed(
            UUID id,
            UUID requestId,
            int attemptNumber,
            String expectedPayloadSha256,
            String actualPayloadSha256,
            NormalizationResult result,
            Instant startedAt,
            Instant finishedAt) {
        Objects.requireNonNull(result, "result");
        return new NormalizationReplayAttempt(
                id,
                requestId,
                attemptNumber,
                NormalizationReplayAttemptOutcome.COMPLETED,
                expectedPayloadSha256,
                actualPayloadSha256,
                result.compatible(),
                result.fixturesCreated(),
                result.fixturesUpdated(),
                result.fixturesUnchanged(),
                result.fixturesBlocked(),
                result.anomalies(),
                null,
                null,
                startedAt,
                finishedAt);
    }

    public static NormalizationReplayAttempt failed(
            UUID id,
            UUID requestId,
            int attemptNumber,
            NormalizationReplayAttemptOutcome outcome,
            String expectedPayloadSha256,
            String actualPayloadSha256,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) {
        if (outcome == NormalizationReplayAttemptOutcome.COMPLETED) {
            throw new IllegalArgumentException("failed attempt requires a failed outcome");
        }
        return new NormalizationReplayAttempt(
                id,
                requestId,
                attemptNumber,
                outcome,
                expectedPayloadSha256,
                actualPayloadSha256,
                null,
                null,
                null,
                null,
                null,
                null,
                errorCode,
                errorMessage,
                startedAt,
                finishedAt);
    }

    private static void requireCounters(Boolean compatible, Integer... counters) {
        if (compatible == null) {
            throw new IllegalArgumentException("a completed attempt requires compatibility");
        }
        for (Integer counter : counters) {
            if (counter == null || counter < 0) {
                throw new IllegalArgumentException("completed attempt counters must not be negative");
            }
        }
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
}
