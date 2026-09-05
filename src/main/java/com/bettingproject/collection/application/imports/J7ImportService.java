package com.bettingproject.collection.application.imports;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class J7ImportService {

    private final J7ImportStore store;
    private final J7ImportRetentionPolicy retentionPolicy;
    private final Clock clock;

    public J7ImportService(
            J7ImportStore store,
            J7ImportRetentionPolicy retentionPolicy,
            Clock clock) {
        this.store = store;
        this.retentionPolicy = retentionPolicy;
        this.clock = clock;
    }

    @Transactional
    public J7ImportResult receive(
            J7ImportCommand command,
            String clientCertificateSha256) {
        Objects.requireNonNull(command, "command");
        StoredJ7Import.requireSha256(
                clientCertificateSha256, "clientCertificateSha256");
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (command.validationDecidedAt().isAfter(receivedAt)) {
            throw new IllegalArgumentException(
                    "validationDecidedAt cannot be after the durable receipt time");
        }

        store.acquireImportLock(command.exportId(), command.idempotencyKey());
        Optional<StoredJ7Import> byKey = store.findByIdempotencyKey(command.idempotencyKey());
        Optional<StoredJ7Import> byExport = store.findByExportId(command.exportId());

        if (byKey.isEmpty() && byExport.isEmpty()) {
            return importFirstReceipt(command, clientCertificateSha256, receivedAt);
        }

        StoredJ7Import existing = byKey.orElseGet(byExport::orElseThrow);
        J7ImportAuditReason conflict = conflictReason(command, existing, byKey, byExport);
        if (conflict != null) {
            store.appendAudit(audit(
                    existing.id(),
                    command,
                    clientCertificateSha256,
                    J7ImportAuditType.DIVERGENCE_REJECTED,
                    conflict,
                    receivedAt));
            return new J7ImportResult.Conflict(conflict);
        }

        Optional<byte[]> durablePayload = store.findPayload(existing.id());
        if (durablePayload.isPresent()
                && !MessageDigest.isEqual(durablePayload.orElseThrow(), command.content())) {
            store.appendAudit(audit(
                    existing.id(),
                    command,
                    clientCertificateSha256,
                    J7ImportAuditType.DIVERGENCE_REJECTED,
                    J7ImportAuditReason.FILE_HASH_DIVERGENCE,
                    receivedAt));
            return new J7ImportResult.Conflict(J7ImportAuditReason.FILE_HASH_DIVERGENCE);
        }
        if (durablePayload.isEmpty() && existing.payloadPurgedAt().isEmpty()) {
            throw new IllegalStateException(
                    "durable J7 payload is missing without a qualified purge tombstone");
        }

        store.appendAudit(audit(
                existing.id(),
                command,
                clientCertificateSha256,
                J7ImportAuditType.DUPLICATE,
                J7ImportAuditReason.BYTE_IDENTICAL,
                receivedAt));
        return new J7ImportResult.Duplicate(existing);
    }

    private J7ImportResult importFirstReceipt(
            J7ImportCommand command,
            String clientCertificateSha256,
            Instant receivedAt) {
        StoredJ7Import receipt = new StoredJ7Import(
                UUID.randomUUID(),
                command.idempotencyKey(),
                command.exportId(),
                command.canonicalEventId(),
                command.providerEventId(),
                StoredJ7Import.PROTOCOL_VERSION,
                command.schemaId(),
                command.schemaVersion(),
                command.generatedAt(),
                command.generatorVersion(),
                StoredJ7Import.SELECTION_MODE,
                command.sourceSetSha256(),
                command.validationStatus(),
                command.validationDecidedAt(),
                command.fileSha256(),
                command.dataSha256(),
                clientCertificateSha256,
                command.content().length,
                receivedAt,
                receivedAt.plus(retentionPolicy.payloadRetention()),
                Optional.empty());
        store.insertImport(receipt, command.content());
        store.appendAudit(audit(
                receipt.id(),
                command,
                clientCertificateSha256,
                J7ImportAuditType.IMPORTED,
                J7ImportAuditReason.ACCEPTED,
                receivedAt));
        store.appendAcceptedOutbox(new J7AcceptedOutboxEvent(
                UUID.randomUUID(),
                "j7-import-accepted:" + receipt.id(),
                receipt.id(),
                receipt.exportId(),
                receipt.canonicalEventId(),
                receipt.fileSha256(),
                receipt.dataSha256(),
                receivedAt));
        return new J7ImportResult.Imported(receipt);
    }

    private J7ImportAuditReason conflictReason(
            J7ImportCommand command,
            StoredJ7Import existing,
            Optional<StoredJ7Import> byKey,
            Optional<StoredJ7Import> byExport) {
        if (byKey.isPresent() && byExport.isPresent()
                && !byKey.orElseThrow().id().equals(byExport.orElseThrow().id())) {
            return J7ImportAuditReason.METADATA_DIVERGENCE;
        }
        if (!existing.idempotencyKey().equals(command.idempotencyKey())) {
            return J7ImportAuditReason.EXPORT_ID_DIVERGENCE;
        }
        if (!existing.exportId().equals(command.exportId())) {
            return J7ImportAuditReason.IDEMPOTENCY_KEY_DIVERGENCE;
        }
        if (!existing.fileSha256().equals(command.fileSha256())) {
            return J7ImportAuditReason.FILE_HASH_DIVERGENCE;
        }
        if (!existing.dataSha256().equals(command.dataSha256())) {
            return J7ImportAuditReason.DATA_HASH_DIVERGENCE;
        }
        if (!sameMetadata(existing, command)) {
            return J7ImportAuditReason.METADATA_DIVERGENCE;
        }
        return null;
    }

    private boolean sameMetadata(StoredJ7Import existing, J7ImportCommand command) {
        return existing.canonicalEventId().equals(command.canonicalEventId())
                && existing.providerEventId() == command.providerEventId()
                && existing.schemaId().equals(command.schemaId())
                && existing.schemaVersion().equals(command.schemaVersion())
                && existing.generatedAt().equals(command.generatedAt())
                && existing.generatorVersion().equals(command.generatorVersion())
                && existing.sourceSetSha256().equals(command.sourceSetSha256())
                && existing.validationStatus().equals(command.validationStatus())
                && existing.decidedAt().equals(command.validationDecidedAt());
    }

    private J7ImportAuditEntry audit(
            UUID importId,
            J7ImportCommand command,
            String clientCertificateSha256,
            J7ImportAuditType type,
            J7ImportAuditReason reason,
            Instant occurredAt) {
        return new J7ImportAuditEntry(
                UUID.randomUUID(),
                importId,
                type,
                command.idempotencyKey(),
                command.fileSha256(),
                command.dataSha256(),
                clientCertificateSha256,
                reason,
                occurredAt);
    }
}
