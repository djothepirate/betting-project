package com.bettingproject.collection.application.imports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7ImportServiceTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-09-02T10:00:00Z");
    private static final String CERTIFICATE_A = "a".repeat(64);
    private static final String CERTIFICATE_B = "b".repeat(64);

    @Test
    void persistsFirstReceiptAuditAndOutboxWithConfiguredRetention() throws Exception {
        InMemoryStore store = new InMemoryStore();
        J7ImportService service = service(store, Duration.ofDays(45));
        J7ImportCommand command = command(
                UUID.fromString("10000000-0000-4000-8000-000000000001"), "{\"one\":1}\n");

        J7ImportResult result = service.receive(command, CERTIFICATE_A);

        J7ImportResult.Imported imported = (J7ImportResult.Imported) result;
        assertThat(imported.receipt().receivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(imported.receipt().payloadExpiresAt())
                .isEqualTo(RECEIVED_AT.plus(Duration.ofDays(45)));
        assertThat(imported.receipt().providerEventId()).isEqualTo(123456L);
        assertThat(store.payloads.get(imported.receipt().id())).containsExactly(command.content());
        assertThat(store.audits).extracting(J7ImportAuditEntry::type)
                .containsExactly(J7ImportAuditType.IMPORTED);
        assertThat(store.outbox).hasSize(1);
    }

    @Test
    void exactRetryIsDuplicateAndCertificateRotationDoesNotChangeContentIdentity()
            throws Exception {
        InMemoryStore store = new InMemoryStore();
        J7ImportService service = service(store, Duration.ofDays(30));
        J7ImportCommand command = command(
                UUID.fromString("10000000-0000-4000-8000-000000000002"), "{\"two\":2}\n");
        J7ImportResult.Imported first = (J7ImportResult.Imported) service.receive(
                command, CERTIFICATE_A);

        J7ImportResult result = service.receive(command, CERTIFICATE_B);

        J7ImportResult.Duplicate duplicate = (J7ImportResult.Duplicate) result;
        assertThat(duplicate.receipt().id()).isEqualTo(first.receipt().id());
        assertThat(store.outbox).hasSize(1);
        assertThat(store.audits).extracting(J7ImportAuditEntry::type)
                .containsExactly(J7ImportAuditType.IMPORTED, J7ImportAuditType.DUPLICATE);
        assertThat(store.audits.getLast().clientCertificateSha256())
                .isEqualTo(CERTIFICATE_B);
    }

    @Test
    void aDifferentPayloadForTheSameExportIsAConflictWithoutSecondOutbox()
            throws Exception {
        InMemoryStore store = new InMemoryStore();
        J7ImportService service = service(store, Duration.ofDays(30));
        UUID exportId = UUID.fromString("10000000-0000-4000-8000-000000000003");
        service.receive(command(exportId, "{\"three\":3}\n"), CERTIFICATE_A);

        J7ImportResult result = service.receive(
                command(exportId, "{\"three\":4}\n"), CERTIFICATE_A);

        assertThat(result).isInstanceOf(J7ImportResult.Conflict.class);
        assertThat(((J7ImportResult.Conflict) result).reason())
                .isEqualTo(J7ImportAuditReason.EXPORT_ID_DIVERGENCE);
        assertThat(store.outbox).hasSize(1);
        assertThat(store.audits.getLast().type())
                .isEqualTo(J7ImportAuditType.DIVERGENCE_REJECTED);
    }

    @Test
    void duplicateRemainsDeterministicAfterQualifiedPayloadPurge() throws Exception {
        InMemoryStore store = new InMemoryStore();
        J7ImportService service = service(store, Duration.ofDays(30));
        J7ImportCommand command = command(
                UUID.fromString("10000000-0000-4000-8000-000000000004"), "{\"four\":4}\n");
        J7ImportResult.Imported first = (J7ImportResult.Imported) service.receive(
                command, CERTIFICATE_A);
        store.markPayloadPurged(first.receipt());

        assertThat(service.receive(command, CERTIFICATE_A))
                .isInstanceOf(J7ImportResult.Duplicate.class);
    }

    @Test
    void refusesAFalseDuplicateWhenPayloadAndPurgeProofAreBothMissing() throws Exception {
        InMemoryStore store = new InMemoryStore();
        J7ImportService service = service(store, Duration.ofDays(30));
        J7ImportCommand command = command(
                UUID.fromString("10000000-0000-4000-8000-000000000007"),
                "{\"seven\":7}\n");
        J7ImportResult.Imported first = (J7ImportResult.Imported) service.receive(
                command, CERTIFICATE_A);
        store.payloads.remove(first.receipt().id());

        assertThatThrownBy(() -> service.receive(command, CERTIFICATE_A))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing without a qualified purge tombstone");
        assertThat(store.audits).extracting(J7ImportAuditEntry::type)
                .containsExactly(J7ImportAuditType.IMPORTED);
    }

    @Test
    void refusesFutureValidationAndPropagatesOutboxFailure() throws Exception {
        InMemoryStore store = new InMemoryStore();
        J7ImportService service = service(store, Duration.ofDays(30));
        J7ImportCommand future = command(
                UUID.fromString("10000000-0000-4000-8000-000000000005"),
                "{\"five\":5}\n",
                Instant.parse("2026-09-02T11:00:00Z"));
        assertThatThrownBy(() -> service.receive(future, CERTIFICATE_A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt time");

        store.failOutbox = true;
        J7ImportCommand accepted = command(
                UUID.fromString("10000000-0000-4000-8000-000000000006"), "{\"six\":6}\n");
        assertThatThrownBy(() -> service.receive(accepted, CERTIFICATE_A))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synthetic outbox failure");
    }

    private J7ImportService service(InMemoryStore store, Duration retention) {
        return new J7ImportService(
                store,
                new J7ImportRetentionPolicy(retention),
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC));
    }

    private J7ImportCommand command(UUID exportId, String content) throws Exception {
        return command(exportId, content, Instant.parse("2026-09-02T09:00:00Z"));
    }

    private J7ImportCommand command(
            UUID exportId,
            String content,
            Instant validationDecidedAt) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String fileSha256 = sha256(bytes);
        return new J7ImportCommand(
                "j7:" + exportId + ":sha256:" + fileSha256,
                exportId,
                fileSha256,
                "d".repeat(64),
                StoredJ7Import.SCHEMA_ID,
                StoredJ7Import.SCHEMA_VERSION,
                UUID.fromString("20000000-0000-4000-8000-000000000001"),
                123456L,
                Instant.parse("2026-09-02T08:00:00Z"),
                "test-1.0.0",
                "e".repeat(64),
                StoredJ7Import.VALIDATION_STATUS,
                validationDecidedAt,
                bytes);
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static final class InMemoryStore implements J7ImportStore {

        private final Map<String, StoredJ7Import> byKey = new LinkedHashMap<>();
        private final Map<UUID, StoredJ7Import> byExport = new LinkedHashMap<>();
        private final Map<UUID, byte[]> payloads = new LinkedHashMap<>();
        private final List<J7ImportAuditEntry> audits = new ArrayList<>();
        private final List<J7AcceptedOutboxEvent> outbox = new ArrayList<>();
        private boolean failOutbox;

        private void markPayloadPurged(StoredJ7Import receipt) {
            payloads.remove(receipt.id());
            StoredJ7Import purged = new StoredJ7Import(
                    receipt.id(),
                    receipt.idempotencyKey(),
                    receipt.exportId(),
                    receipt.canonicalEventId(),
                    receipt.providerEventId(),
                    receipt.protocolVersion(),
                    receipt.schemaId(),
                    receipt.schemaVersion(),
                    receipt.generatedAt(),
                    receipt.generatorVersion(),
                    receipt.selectionMode(),
                    receipt.sourceSetSha256(),
                    receipt.validationStatus(),
                    receipt.decidedAt(),
                    receipt.fileSha256(),
                    receipt.dataSha256(),
                    receipt.clientCertificateSha256(),
                    receipt.payloadSizeBytes(),
                    receipt.receivedAt(),
                    receipt.payloadExpiresAt(),
                    Optional.of(receipt.payloadExpiresAt()));
            byKey.put(purged.idempotencyKey(), purged);
            byExport.put(purged.exportId(), purged);
        }

        @Override
        public void acquireImportLock(UUID exportId, String idempotencyKey) {
        }

        @Override
        public Optional<StoredJ7Import> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(byKey.get(idempotencyKey));
        }

        @Override
        public Optional<StoredJ7Import> findByExportId(UUID exportId) {
            return Optional.ofNullable(byExport.get(exportId));
        }

        @Override
        public void insertImport(StoredJ7Import storedImport, byte[] payload) {
            byKey.put(storedImport.idempotencyKey(), storedImport);
            byExport.put(storedImport.exportId(), storedImport);
            payloads.put(storedImport.id(), payload.clone());
        }

        @Override
        public Optional<byte[]> findPayload(UUID importId) {
            return Optional.ofNullable(payloads.get(importId)).map(byte[]::clone);
        }

        @Override
        public void appendAudit(J7ImportAuditEntry entry) {
            audits.add(entry);
        }

        @Override
        public void appendAcceptedOutbox(J7AcceptedOutboxEvent event) {
            if (failOutbox) {
                throw new IllegalStateException("synthetic outbox failure");
            }
            outbox.add(event);
        }

        @Override
        public int purgeExpiredPayloads(
                Instant expiredBefore,
                Instant purgedAt,
                int maximumRows) {
            return 0;
        }
    }
}
