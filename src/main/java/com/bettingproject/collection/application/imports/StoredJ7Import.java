package com.bettingproject.collection.application.imports;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record StoredJ7Import(
        UUID id,
        String idempotencyKey,
        UUID exportId,
        UUID canonicalEventId,
        long providerEventId,
        String protocolVersion,
        String schemaId,
        String schemaVersion,
        Instant generatedAt,
        String generatorVersion,
        String selectionMode,
        String sourceSetSha256,
        String validationStatus,
        Instant decidedAt,
        String fileSha256,
        String dataSha256,
        String clientCertificateSha256,
        long payloadSizeBytes,
        Instant receivedAt,
        Instant payloadExpiresAt,
        Optional<Instant> payloadPurgedAt) {

    public static final String PROTOCOL_VERSION = "1.0";
    public static final String SCHEMA_ID =
            "urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String SELECTION_MODE = "LATEST_AVAILABLE";
    public static final String VALIDATION_STATUS = "HUMAN_VALIDATED";
    public static final long MAX_PAYLOAD_BYTES = 5L * 1024 * 1024;
    public static final Duration DEFAULT_PAYLOAD_RETENTION = Duration.ofDays(30);
    public static final Duration MAXIMUM_PAYLOAD_RETENTION = Duration.ofDays(3650);

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern GENERATOR_VERSION =
            Pattern.compile("[A-Za-z0-9._+\\-]{1,64}");

    public StoredJ7Import {
        id = Objects.requireNonNull(id, "id");
        exportId = Objects.requireNonNull(exportId, "exportId");
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        requireExact(protocolVersion, PROTOCOL_VERSION, "protocolVersion");
        requireExact(schemaId, SCHEMA_ID, "schemaId");
        requireExact(schemaVersion, SCHEMA_VERSION, "schemaVersion");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        if (generatorVersion == null || !GENERATOR_VERSION.matcher(generatorVersion).matches()) {
            throw new IllegalArgumentException("generatorVersion is invalid");
        }
        requireExact(selectionMode, SELECTION_MODE, "selectionMode");
        requireSha256(sourceSetSha256, "sourceSetSha256");
        requireExact(validationStatus, VALIDATION_STATUS, "validationStatus");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        requireSha256(fileSha256, "fileSha256");
        requireSha256(dataSha256, "dataSha256");
        requireSha256(clientCertificateSha256, "clientCertificateSha256");
        if (payloadSizeBytes < 1 || payloadSizeBytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payloadSizeBytes must be between 1 and 5 MiB");
        }
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        payloadExpiresAt = Objects.requireNonNull(payloadExpiresAt, "payloadExpiresAt");
        payloadPurgedAt = Objects.requireNonNull(payloadPurgedAt, "payloadPurgedAt");

        String expectedKey = idempotencyKey(exportId, fileSha256);
        if (!expectedKey.equals(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey does not match export and file hash");
        }
        if (generatedAt.isAfter(decidedAt) || decidedAt.isAfter(receivedAt)) {
            throw new IllegalArgumentException("J7 import chronology is invalid");
        }
        if (!payloadExpiresAt.isAfter(receivedAt)
                || payloadExpiresAt.isAfter(receivedAt.plus(MAXIMUM_PAYLOAD_RETENTION))) {
            throw new IllegalArgumentException(
                    "payloadExpiresAt must be after receipt and within 3650 days");
        }
        if (payloadPurgedAt.isPresent()
                && payloadPurgedAt.orElseThrow().isBefore(payloadExpiresAt)) {
            throw new IllegalArgumentException("payload cannot be purged before retention expiry");
        }
    }

    public static String idempotencyKey(UUID exportId, String fileSha256) {
        Objects.requireNonNull(exportId, "exportId");
        requireSha256(fileSha256, "fileSha256");
        return "j7:" + exportId + ":sha256:" + fileSha256;
    }

    static void requireSha256(String value, String name) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
    }

    public static String requireVisibleAscii(String value, String name, int maximumLength) {
        if (value == null || value.isEmpty() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException(name + " must contain visible ASCII only");
            }
        }
        return value;
    }

    private static void requireExact(String value, String expected, String name) {
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(name + " must be " + expected);
        }
    }
}
