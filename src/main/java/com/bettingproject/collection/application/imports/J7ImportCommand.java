package com.bettingproject.collection.application.imports;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fully validated, byte-exact J7 import intent crossing the inbound adapter boundary.
 */
public record J7ImportCommand(
        String idempotencyKey,
        UUID exportId,
        String fileSha256,
        String dataSha256,
        String schemaId,
        String schemaVersion,
        UUID canonicalEventId,
        long providerEventId,
        Instant generatedAt,
        String generatorVersion,
        String sourceSetSha256,
        String validationStatus,
        Instant validationDecidedAt,
        byte[] content) {

    private static final int MAXIMUM_CONTENT_BYTES = 5_242_880;
    private static final String EXPECTED_SCHEMA_ID =
            "urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1";
    private static final String EXPECTED_SCHEMA_VERSION = "1.0.0";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern GENERATOR_VERSION = Pattern.compile("[A-Za-z0-9._+-]{1,64}");

    public J7ImportCommand {
        Objects.requireNonNull(exportId, "exportId");
        requireProtocolUuid(exportId);
        fileSha256 = requireSha256(fileSha256, "fileSha256");
        dataSha256 = requireSha256(dataSha256, "dataSha256");
        if (!EXPECTED_SCHEMA_ID.equals(schemaId)) {
            throw new IllegalArgumentException("schemaId is not the J7 v1 schema");
        }
        if (!EXPECTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion is not the J7 v1 version");
        }
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        requireProtocolUuid(canonicalEventId, "canonicalEventId");
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt")
                .truncatedTo(ChronoUnit.MICROS);
        if (generatorVersion == null || !GENERATOR_VERSION.matcher(generatorVersion).matches()) {
            throw new IllegalArgumentException("generatorVersion is invalid");
        }
        sourceSetSha256 = requireSha256(sourceSetSha256, "sourceSetSha256");
        if (!"HUMAN_VALIDATED".equals(validationStatus)) {
            throw new IllegalArgumentException("validationStatus must be HUMAN_VALIDATED");
        }
        validationDecidedAt = Objects.requireNonNull(
                validationDecidedAt,
                "validationDecidedAt").truncatedTo(ChronoUnit.MICROS);
        if (validationDecidedAt.isBefore(generatedAt)) {
            throw new IllegalArgumentException("validationDecidedAt precedes generatedAt");
        }
        String expectedKey = "j7:" + exportId + ":sha256:" + fileSha256;
        if (!expectedKey.equals(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey does not match the import identity");
        }
        content = Objects.requireNonNull(content, "content").clone();
        if (content.length < 1 || content.length > MAXIMUM_CONTENT_BYTES) {
            throw new IllegalArgumentException("content must contain between 1 byte and 5 MiB");
        }
        if (!MessageDigest.isEqual(
                HexFormat.of().parseHex(fileSha256),
                sha256(content))) {
            throw new IllegalArgumentException("content does not match fileSha256");
        }
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    private static String requireSha256(String value, String name) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lower-case SHA-256");
        }
        return value;
    }

    private static void requireProtocolUuid(UUID value) {
        requireProtocolUuid(value, "exportId");
    }

    private static void requireProtocolUuid(UUID value, String name) {
        if (value.variant() != 2 || value.version() < 1 || value.version() > 5) {
            throw new IllegalArgumentException(name + " must be a canonical RFC 4122 UUID");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
