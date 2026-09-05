package com.bettingproject.collection.application.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class J7ImportCommandTest {

    private static final UUID EXPORT_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SCHEMA_ID =
            "urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1";
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String DATA_SHA = "a".repeat(64);
    private static final String SOURCE_SET_SHA = "b".repeat(64);
    private static final UUID CANONICAL_EVENT_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void clonesSubmittedAndReturnedBytes() {
        byte[] submitted = {1, 2, 3};
        J7ImportCommand command = command(submitted);

        submitted[0] = 9;
        byte[] returned = command.content();
        returned[1] = 9;

        assertThat(command.content()).containsExactly(1, 2, 3);
    }

    @Test
    void acceptsTheInclusiveFiveMiBBoundary() {
        byte[] maximum = new byte[5_242_880];
        assertThat(command(maximum).content()).hasSize(5_242_880);
    }

    @Test
    void normalizesProtocolTimestampsToPostgreSqlMicrosecondPrecision() {
        byte[] content = {1, 2, 3};
        String fileSha = sha256(content);
        J7ImportCommand command = new J7ImportCommand(
                key(EXPORT_ID, fileSha),
                EXPORT_ID,
                fileSha,
                DATA_SHA,
                SCHEMA_ID,
                SCHEMA_VERSION,
                CANONICAL_EVENT_ID,
                42L,
                Instant.parse("2026-09-02T10:00:00.123456789Z"),
                "test-v1",
                SOURCE_SET_SHA,
                "HUMAN_VALIDATED",
                Instant.parse("2026-09-02T10:01:00.987654321Z"),
                content);

        assertThat(command.generatedAt())
                .isEqualTo(Instant.parse("2026-09-02T10:00:00.123456Z"));
        assertThat(command.validationDecidedAt())
                .isEqualTo(Instant.parse("2026-09-02T10:01:00.987654Z"));
    }

    @Test
    void rejectsEmptyAndOversizedContent() {
        assertThatThrownBy(() -> command(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(new byte[5_242_881]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDivergentIdentitySchemaHashesAndNonRfcUuid() {
        byte[] content = {1};
        String fileSha = sha256(content);
        assertThatThrownBy(() -> new J7ImportCommand(
                "wrong", EXPORT_ID, fileSha, DATA_SHA, SCHEMA_ID, SCHEMA_VERSION,
                CANONICAL_EVENT_ID, 42L, Instant.EPOCH, "test-v1", SOURCE_SET_SHA,
                "HUMAN_VALIDATED", Instant.EPOCH, content))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new J7ImportCommand(
                key(EXPORT_ID, fileSha), EXPORT_ID, "A".repeat(64), DATA_SHA,
                SCHEMA_ID, SCHEMA_VERSION,
                CANONICAL_EVENT_ID, 42L, Instant.EPOCH, "test-v1", SOURCE_SET_SHA,
                "HUMAN_VALIDATED", Instant.EPOCH, content))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new J7ImportCommand(
                key(EXPORT_ID, fileSha), EXPORT_ID, fileSha, DATA_SHA,
                "urn:wrong", SCHEMA_VERSION,
                CANONICAL_EVENT_ID, 42L, Instant.EPOCH, "test-v1", SOURCE_SET_SHA,
                "HUMAN_VALIDATED", Instant.EPOCH, content))
                .isInstanceOf(IllegalArgumentException.class);
        UUID nil = new UUID(0, 0);
        assertThatThrownBy(() -> new J7ImportCommand(
                key(nil, fileSha), nil, fileSha, DATA_SHA,
                SCHEMA_ID, SCHEMA_VERSION,
                CANONICAL_EVENT_ID, 42L, Instant.EPOCH, "test-v1", SOURCE_SET_SHA,
                "HUMAN_VALIDATED", Instant.EPOCH, content))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] different = {2};
        assertThatThrownBy(() -> new J7ImportCommand(
                key(EXPORT_ID, fileSha), EXPORT_ID, fileSha, DATA_SHA,
                SCHEMA_ID, SCHEMA_VERSION,
                CANONICAL_EVENT_ID, 42L, Instant.EPOCH, "test-v1", SOURCE_SET_SHA,
                "HUMAN_VALIDATED", Instant.EPOCH, different))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private J7ImportCommand command(byte[] content) {
        String fileSha = sha256(content);
        return new J7ImportCommand(
                key(EXPORT_ID, fileSha),
                EXPORT_ID,
                fileSha,
                DATA_SHA,
                SCHEMA_ID,
                SCHEMA_VERSION,
                CANONICAL_EVENT_ID,
                42L,
                Instant.EPOCH,
                "test-v1",
                SOURCE_SET_SHA,
                "HUMAN_VALIDATED",
                Instant.EPOCH,
                content);
    }

    private String key(UUID exportId, String fileSha) {
        return "j7:" + exportId + ":sha256:" + fileSha;
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
