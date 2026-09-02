package com.bettingproject.collection.application.imports;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record J7ImportAuditEntry(
        UUID id,
        UUID importId,
        J7ImportAuditType type,
        String requestIdempotencyKey,
        String observedFileSha256,
        String observedDataSha256,
        String clientCertificateSha256,
        J7ImportAuditReason reason,
        Instant occurredAt) {

    public J7ImportAuditEntry {
        id = Objects.requireNonNull(id, "id");
        importId = Objects.requireNonNull(importId, "importId");
        type = Objects.requireNonNull(type, "type");
        requestIdempotencyKey = StoredJ7Import.requireVisibleAscii(
                requestIdempotencyKey, "requestIdempotencyKey", 128);
        StoredJ7Import.requireSha256(observedFileSha256, "observedFileSha256");
        StoredJ7Import.requireSha256(observedDataSha256, "observedDataSha256");
        StoredJ7Import.requireSha256(
                clientCertificateSha256, "clientCertificateSha256");
        reason = Objects.requireNonNull(reason, "reason");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        if (!compatible(type, reason)) {
            throw new IllegalArgumentException("audit type and reason are inconsistent");
        }
    }

    private static boolean compatible(J7ImportAuditType type, J7ImportAuditReason reason) {
        return switch (type) {
            case IMPORTED -> reason == J7ImportAuditReason.ACCEPTED;
            case DUPLICATE -> reason == J7ImportAuditReason.BYTE_IDENTICAL;
            case PAYLOAD_PURGED -> reason == J7ImportAuditReason.RETENTION_EXPIRED;
            case DIVERGENCE_REJECTED -> switch (reason) {
                case IDEMPOTENCY_KEY_DIVERGENCE,
                        EXPORT_ID_DIVERGENCE,
                        FILE_HASH_DIVERGENCE,
                        DATA_HASH_DIVERGENCE,
                        CERTIFICATE_DIVERGENCE,
                        METADATA_DIVERGENCE -> true;
                default -> false;
            };
        };
    }
}
