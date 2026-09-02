package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizationReplayAttemptTest {

    private static final UUID ATTEMPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000620");
    private static final UUID REQUEST_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000621");
    private static final UUID SNAPSHOT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000622");
    private static final String SHA_256 = "c".repeat(64);
    private static final Instant STARTED_AT = Instant.parse("2026-09-01T19:15:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(1);

    @Test
    void completedAttemptKeepsTheNormalizationResult() {
        NormalizationResult result = new NormalizationResult(
                SNAPSHOT_ID, SHA_256, false, true, 1, 2, 3, 4, 5);

        NormalizationReplayAttempt attempt = NormalizationReplayAttempt.completed(
                ATTEMPT_ID,
                REQUEST_ID,
                2,
                SHA_256,
                SHA_256,
                result,
                STARTED_AT,
                FINISHED_AT);

        assertThat(attempt.outcome()).isEqualTo(NormalizationReplayAttemptOutcome.COMPLETED);
        assertThat(attempt.compatible()).isTrue();
        assertThat(attempt.fixturesCreated()).isEqualTo(1);
        assertThat(attempt.fixturesUpdated()).isEqualTo(2);
        assertThat(attempt.fixturesUnchanged()).isEqualTo(3);
        assertThat(attempt.fixturesBlocked()).isEqualTo(4);
        assertThat(attempt.anomalies()).isEqualTo(5);
        assertThat(attempt.errorCode()).isNull();
    }

    @Test
    void failedAttemptKeepsOnlySanitizedFailureDetails() {
        NormalizationReplayAttempt attempt = NormalizationReplayAttempt.failed(
                ATTEMPT_ID,
                REQUEST_ID,
                1,
                NormalizationReplayAttemptOutcome.FAILED_RETRYABLE,
                SHA_256,
                SHA_256,
                "  NORMALIZATION_FAILED  ",
                "  Replay can be resumed  ",
                STARTED_AT,
                FINISHED_AT);

        assertThat(attempt.outcome())
                .isEqualTo(NormalizationReplayAttemptOutcome.FAILED_RETRYABLE);
        assertThat(attempt.errorCode()).isEqualTo("NORMALIZATION_FAILED");
        assertThat(attempt.errorMessage()).isEqualTo("Replay can be resumed");
        assertThat(attempt.compatible()).isNull();
        assertThat(attempt.fixturesCreated()).isNull();
    }

    @Test
    void completedAttemptRequiresCompatibilityAndNonNegativeCounters() {
        assertThatThrownBy(() -> attempt(
                NormalizationReplayAttemptOutcome.COMPLETED,
                null,
                0,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compatibility");
        assertThatThrownBy(() -> attempt(
                NormalizationReplayAttemptOutcome.COMPLETED,
                true,
                -1,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("counters");
        assertThatThrownBy(() -> attempt(
                NormalizationReplayAttemptOutcome.COMPLETED,
                true,
                0,
                "ERROR",
                "unexpected"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain an error");
    }

    @Test
    void failedAttemptRequiresFailureDetailsAndValidTimeline() {
        assertThatThrownBy(() -> attempt(
                NormalizationReplayAttemptOutcome.FAILED_TERMINAL,
                null,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires an error");
        assertThatThrownBy(() -> new NormalizationReplayAttempt(
                ATTEMPT_ID,
                REQUEST_ID,
                0,
                NormalizationReplayAttemptOutcome.FAILED_TERMINAL,
                SHA_256,
                SHA_256,
                null,
                null,
                null,
                null,
                null,
                null,
                "ERROR",
                "failed",
                STARTED_AT,
                FINISHED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptNumber");
        assertThatThrownBy(() -> NormalizationReplayAttempt.failed(
                ATTEMPT_ID,
                REQUEST_ID,
                1,
                NormalizationReplayAttemptOutcome.COMPLETED,
                SHA_256,
                SHA_256,
                "ERROR",
                "failed",
                STARTED_AT,
                FINISHED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failed outcome");
    }

    private NormalizationReplayAttempt attempt(
            NormalizationReplayAttemptOutcome outcome,
            Boolean compatible,
            Integer counter,
            String errorCode,
            String errorMessage) {
        return new NormalizationReplayAttempt(
                ATTEMPT_ID,
                REQUEST_ID,
                1,
                outcome,
                SHA_256,
                SHA_256,
                compatible,
                counter,
                counter,
                counter,
                counter,
                counter,
                errorCode,
                errorMessage,
                STARTED_AT,
                FINISHED_AT);
    }
}
