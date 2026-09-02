package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizationReplayRequestTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000610");
    private static final UUID RECEIPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000611");
    private static final UUID SNAPSHOT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000612");
    private static final UUID DECISION_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000613");
    private static final String SHA_256 = "b".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T19:00:00Z");

    @Test
    void pendingManualRequestStartsAsClaimableVersionOne() {
        NormalizationReplayRequest request = NormalizationReplayRequest.pending(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA_256,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.PAYLOAD_SHA256,
                SHA_256,
                CREATED_AT);

        assertThat(request.id()).isEqualTo(REQUEST_ID);
        assertThat(request.rawSnapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(request.status()).isEqualTo(NormalizationReplayStatus.PENDING);
        assertThat(request.status().claimable()).isTrue();
        assertThat(request.version()).isEqualTo(1);
        assertThat(request.attemptCount()).isZero();
        assertThat(request.lastErrorCode()).isNull();
        assertThat(request.completedAt()).isNull();
        assertThat(request.createdAt()).isEqualTo(CREATED_AT);
        assertThat(request.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void mappingDecisionRequestRequiresItsDecisionAndSnapshotSelector() {
        NormalizationReplayRequest request = NormalizationReplayRequest.pending(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA_256,
                DECISION_ID,
                NormalizationReplayOrigin.MAPPING_DECISION,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                CREATED_AT);

        assertThat(request.providerMappingDecisionId()).isEqualTo(DECISION_ID);
        assertThat(request.selectorValue()).isEqualTo(SNAPSHOT_ID.toString());

        assertThatThrownBy(() -> NormalizationReplayRequest.pending(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA_256,
                null,
                NormalizationReplayOrigin.MAPPING_DECISION,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerMappingDecisionId");
        assertThatThrownBy(() -> NormalizationReplayRequest.pending(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA_256,
                DECISION_ID,
                NormalizationReplayOrigin.MAPPING_DECISION,
                NormalizationReplaySelectorType.PAYLOAD_SHA256,
                SHA_256,
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot UUID");
    }

    @Test
    void terminalAndFailureStateMustRemainCoherent() {
        assertThatThrownBy(() -> request(
                NormalizationReplayStatus.COMPLETED,
                2,
                1,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completedAt");
        assertThatThrownBy(() -> request(
                NormalizationReplayStatus.FAILED_RETRYABLE,
                3,
                1,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failure details");
        assertThatThrownBy(() -> request(
                NormalizationReplayStatus.PENDING,
                1,
                0,
                "ERROR",
                "must not be retained",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failure details");

        NormalizationReplayRequest terminalFailure = request(
                NormalizationReplayStatus.FAILED_TERMINAL,
                3,
                1,
                "  HASH_MISMATCH  ",
                "  Payload altered  ",
                CREATED_AT.plusSeconds(2));

        assertThat(terminalFailure.lastErrorCode()).isEqualTo("HASH_MISMATCH");
        assertThat(terminalFailure.lastErrorMessage()).isEqualTo("Payload altered");
        assertThat(terminalFailure.status().terminal()).isTrue();
    }

    @Test
    void invalidVersionAttemptCountTimelineAndSelectorAreRejected() {
        assertThatThrownBy(() -> request(
                NormalizationReplayStatus.PENDING, 0, 0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
        assertThatThrownBy(() -> request(
                NormalizationReplayStatus.PENDING, 1, -1, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptCount");
        assertThatThrownBy(() -> new NormalizationReplayRequest(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA_256,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                "not-a-uuid",
                NormalizationReplayStatus.PENDING,
                1,
                0,
                null,
                null,
                CREATED_AT,
                CREATED_AT,
                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NormalizationReplayRequest(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA_256,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                NormalizationReplayStatus.PENDING,
                1,
                0,
                null,
                null,
                CREATED_AT,
                CREATED_AT.minusSeconds(1),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedAt");
    }

    private NormalizationReplayRequest request(
            NormalizationReplayStatus status,
            long version,
            int attemptCount,
            String errorCode,
            String errorMessage,
            Instant completedAt) {
        return new NormalizationReplayRequest(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA_256,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                status,
                version,
                attemptCount,
                errorCode,
                errorMessage,
                CREATED_AT,
                CREATED_AT.plusSeconds(1),
                completedAt);
    }
}
