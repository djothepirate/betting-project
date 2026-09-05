package com.bettingproject.collection.application.imports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface J7ImportStore {

    void acquireImportLock(UUID exportId, String idempotencyKey);

    Optional<StoredJ7Import> findByIdempotencyKey(String idempotencyKey);

    Optional<StoredJ7Import> findByExportId(UUID exportId);

    void insertImport(StoredJ7Import storedImport, byte[] payload);

    Optional<byte[]> findPayload(UUID importId);

    void appendAudit(J7ImportAuditEntry entry);

    void appendAcceptedOutbox(J7AcceptedOutboxEvent event);

    int purgeExpiredPayloads(Instant expiredBefore, Instant purgedAt, int maximumRows);
}
