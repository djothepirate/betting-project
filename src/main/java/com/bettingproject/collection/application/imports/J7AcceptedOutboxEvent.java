package com.bettingproject.collection.application.imports;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record J7AcceptedOutboxEvent(
        UUID id,
        String idempotencyKey,
        UUID importId,
        UUID exportId,
        UUID canonicalEventId,
        String fileSha256,
        String dataSha256,
        Instant occurredAt) {

    public J7AcceptedOutboxEvent {
        id = Objects.requireNonNull(id, "id");
        importId = Objects.requireNonNull(importId, "importId");
        exportId = Objects.requireNonNull(exportId, "exportId");
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        StoredJ7Import.requireSha256(fileSha256, "fileSha256");
        StoredJ7Import.requireSha256(dataSha256, "dataSha256");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        String expectedKey = "j7-import-accepted:" + importId;
        if (!expectedKey.equals(idempotencyKey)) {
            throw new IllegalArgumentException("outbox idempotencyKey is not deterministic");
        }
    }
}
