package com.bettingproject.collection.adapter.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.bettingproject.collection.application.imports.J7AcceptedOutboxEvent;
import com.bettingproject.collection.application.imports.J7ImportAuditEntry;
import com.bettingproject.collection.application.imports.J7ImportAuditReason;
import com.bettingproject.collection.application.imports.J7ImportAuditType;
import com.bettingproject.collection.application.imports.J7ImportCommand;
import com.bettingproject.collection.application.imports.J7ImportResult;
import com.bettingproject.collection.application.imports.J7ImportRetentionPolicy;
import com.bettingproject.collection.application.imports.J7ImportService;
import com.bettingproject.collection.application.imports.J7ImportStore;
import com.bettingproject.collection.application.imports.StoredJ7Import;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JdbcJ7ImportStoreIT {

    private static final String DATA_SHA_256 = "b".repeat(64);
    private static final String SOURCE_SET_SHA_256 = "c".repeat(64);
    private static final String CERTIFICATE_SHA_256 = "d".repeat(64);
    private static final UUID CANONICAL_EVENT_ID =
            UUID.fromString("40000000-0000-4000-8000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    private static J7ImportStore store;
    private static JdbcClient jdbcClient;
    private static PlatformTransactionManager transactionManager;
    private static J7ImportService importService;

    @BeforeAll
    static void configurePersistenceHarness() {
        Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword())
                .load()
                .migrate();

        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        store = new JdbcJ7ImportStore(jdbcClient);
        importService = new J7ImportService(
                store,
                new J7ImportRetentionPolicy(Duration.ofDays(30)),
                Clock.systemUTC());
    }

    @Test
    void serviceCommitsOneImportThenReturnsDuplicateAndConflictDeterministically() {
        UUID exportId = UUID.fromString("50000000-0000-4000-8000-000000000041");
        Instant generatedAt = Instant.parse("2026-09-01T10:00:00.123456789Z");
        J7ImportCommand command = command(exportId, payload("service-import"), generatedAt);

        J7ImportResult first = receive(command, CERTIFICATE_SHA_256);
        J7ImportResult second = receive(command, "e".repeat(64));
        J7ImportResult conflict = receive(
                command(exportId, payload("service-divergence"), generatedAt),
                CERTIFICATE_SHA_256);

        assertThat(first).isInstanceOf(J7ImportResult.Imported.class);
        assertThat(second).isInstanceOf(J7ImportResult.Duplicate.class);
        assertThat(conflict).isInstanceOf(J7ImportResult.Conflict.class);
        StoredJ7Import receipt = ((J7ImportResult.Imported) first).receipt();
        StoredJ7Import duplicate = ((J7ImportResult.Duplicate) second).receipt();
        assertThat(command.generatedAt()).isEqualTo(
                Instant.parse("2026-09-01T10:00:00.123456Z"));
        assertThat(receipt.receivedAt()).isEqualTo(duplicate.receivedAt());
        assertThat(receipt.receivedAt().getNano() % 1_000).isZero();
        assertThat(countByExportId(exportId)).isEqualTo(1);
        assertThat(auditCount(receipt.id(), "IMPORTED")).isEqualTo(1);
        assertThat(auditCount(receipt.id(), "DUPLICATE")).isEqualTo(1);
        assertThat(auditCount(receipt.id(), "DIVERGENCE_REJECTED")).isEqualTo(1);
        assertThat(outboxCount("j7-import-accepted:" + receipt.id())).isEqualTo(1);
    }

    @Test
    void storesExactPayloadImmutableAuditAndMetadataOnlyOutbox() {
        byte[] payload = payload("stored-exactly");
        StoredJ7Import candidate = storedImport(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                payload,
                Instant.parse("2026-01-01T10:00:00Z"));

        inTransaction(() -> {
            store.acquireImportLock(candidate.exportId(), candidate.idempotencyKey());
            store.insertImport(candidate, payload);
            store.appendAudit(importedAudit(candidate));
            store.appendAcceptedOutbox(outbox(candidate));
        });

        assertThat(store.findByIdempotencyKey(candidate.idempotencyKey()))
                .contains(candidate);
        assertThat(store.findByExportId(candidate.exportId())).contains(candidate);
        assertThat(store.findPayload(candidate.id())).hasValueSatisfying(
                storedPayload -> assertThat(storedPayload).containsExactly(payload));
        assertThat(count("j7_import_receipt", candidate.id())).isEqualTo(1);
        assertThat(count("j7_import_audit", candidate.id())).isEqualTo(1);
        assertThat(outboxCount(outbox(candidate).idempotencyKey())).isEqualTo(1);

        String outboxJson = jdbcClient.sql("""
                SELECT payload_json::text
                FROM outbox_message
                WHERE idempotency_key = :idempotencyKey
                """)
                .param("idempotencyKey", outbox(candidate).idempotencyKey())
                .query(String.class)
                .single();
        assertThat(outboxJson)
                .contains("J7_IMPORT_ACCEPTED", candidate.exportId().toString())
                .contains(candidate.fileSha256(), candidate.dataSha256())
                .doesNotContain(new String(payload, StandardCharsets.UTF_8))
                .doesNotContain(candidate.clientCertificateSha256());

        assertThatThrownBy(() -> jdbcClient.sql("""
                UPDATE j7_import_receipt
                SET generator_version = 'tampered'
                WHERE id = :id
                """).param("id", candidate.id()).update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcClient.sql("""
                DELETE FROM j7_import_audit
                WHERE import_id = :id
                """).param("id", candidate.id()).update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesSameLengthPayloadWhoseBytesDoNotMatchTheReceiptHash() {
        byte[] declaredPayload = payload("same-a");
        byte[] divergentPayload = payload("same-b");
        assertThat(divergentPayload).hasSameSizeAs(declaredPayload);
        StoredJ7Import candidate = storedImport(
                UUID.fromString("10000000-0000-0000-0000-000000000051"),
                UUID.fromString("20000000-0000-4000-8000-000000000051"),
                declaredPayload,
                Instant.parse("2026-01-01T10:00:00Z"));

        assertThatThrownBy(() -> inTransaction(
                () -> store.insertImport(candidate, divergentPayload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload hash");
        assertThat(count("j7_import_receipt", candidate.id())).isZero();
        assertThat(count("j7_import_payload", candidate.id())).isZero();
    }

    @Test
    void purgeIsBoundedAndLeavesAnIdempotentTombstoneWithoutPayloadRehydration() {
        Instant receivedAt = Instant.parse("2026-01-02T10:00:00Z");
        StoredJ7Import first = storedImport(
                UUID.fromString("10000000-0000-0000-0000-000000000011"),
                UUID.fromString("20000000-0000-4000-8000-000000000011"),
                payload("purge-first"),
                receivedAt);
        StoredJ7Import second = storedImport(
                UUID.fromString("10000000-0000-0000-0000-000000000012"),
                UUID.fromString("20000000-0000-4000-8000-000000000012"),
                payload("purge-second"),
                receivedAt.plusSeconds(1));
        persistAccepted(first, payload("purge-first"));
        persistAccepted(second, payload("purge-second"));

        Instant cutoff = second.payloadExpiresAt();
        Instant purgedAt = cutoff.plusSeconds(1);
        assertThat(store.purgeExpiredPayloads(cutoff, purgedAt, 1)).isZero();
        assertThat(store.findPayload(first.id())).isPresent();

        assertThatThrownBy(() -> jdbcClient.sql("""
                DELETE FROM j7_import_payload
                WHERE import_id = :importId
                """).param("importId", first.id()).update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> {
            jdbcClient.sql("""
                    SELECT set_config('bettingproject.j7_payload_purge', 'v1', true)
                    """).query(String.class).single();
            jdbcClient.sql("""
                    DELETE FROM j7_import_payload
                    WHERE import_id = :importId
                    """).param("importId", first.id()).update();
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO j7_import_payload_tombstone (
                    import_id, file_sha256, data_sha256, payload_size_bytes,
                    received_at, payload_expires_at, purged_at, reason_code
                ) VALUES (
                    :importId, :fileSha256, :dataSha256, :payloadSizeBytes,
                    :receivedAt, :payloadExpiresAt, :purgedAt, 'RETENTION_EXPIRED'
                )
                """)
                .param("importId", first.id())
                .param("fileSha256", first.fileSha256())
                .param("dataSha256", first.dataSha256())
                .param("payloadSizeBytes", first.payloadSizeBytes())
                .param("receivedAt", first.receivedAt().atOffset(ZoneOffset.UTC))
                .param("payloadExpiresAt", first.payloadExpiresAt().atOffset(ZoneOffset.UTC))
                .param("purgedAt", first.receivedAt().atOffset(ZoneOffset.UTC))
                .update())
                .isInstanceOf(DataAccessException.class);
        assertThat(tombstoneCount(first.id())).isZero();

        markOutboxDelivered(first, purgedAt);
        markOutboxDelivered(second, purgedAt);
        Instant future = Instant.parse("2099-01-01T00:00:00Z");
        assertThatThrownBy(() -> store.purgeExpiredPayloads(future, future, 1))
                .isInstanceOf(DataAccessException.class);
        assertThat(store.purgeExpiredPayloads(cutoff, purgedAt, 1)).isEqualTo(1);

        StoredJ7Import tombstone = store.findByIdempotencyKey(first.idempotencyKey())
                .orElseThrow();
        assertThat(tombstone.payloadPurgedAt()).contains(purgedAt);
        assertThat(store.findPayload(first.id())).isEmpty();
        assertThat(store.findPayload(second.id())).isPresent();
        assertThat(count("j7_import_receipt", first.id())).isEqualTo(1);
        assertThat(auditCount(first.id(), "PAYLOAD_PURGED")).isEqualTo(1);
        assertThat(tombstoneCount(first.id())).isEqualTo(1);

        J7ImportResult postPurgeDuplicate = receive(
                commandFrom(first, payload("purge-first")),
                CERTIFICATE_SHA_256);
        assertThat(postPurgeDuplicate).isInstanceOf(J7ImportResult.Duplicate.class);

        StoredJ7Import replayed = storedImport(
                UUID.fromString("10000000-0000-0000-0000-000000000013"),
                first.exportId(),
                payload("purge-first"),
                receivedAt.plusSeconds(10));
        assertThatThrownBy(() -> store.insertImport(replayed, payload("purge-first")))
                .isInstanceOf(DataAccessException.class);
        assertThat(store.findPayload(first.id())).isEmpty();
        assertThat(countByExportId(first.exportId())).isEqualTo(1);
    }

    @Test
    void transactionRollsBackReceiptPayloadAndAuditWhenOutboxInsertFails() {
        byte[] payload = payload("rollback-on-outbox");
        StoredJ7Import candidate = storedImport(
                UUID.fromString("10000000-0000-0000-0000-000000000021"),
                UUID.fromString("20000000-0000-0000-0000-000000000021"),
                payload,
                Instant.parse("2026-02-01T10:00:00Z"));
        J7AcceptedOutboxEvent event = outbox(candidate);
        seedOutboxKey(event.idempotencyKey());

        assertThatThrownBy(() -> inTransaction(() -> {
            store.acquireImportLock(candidate.exportId(), candidate.idempotencyKey());
            store.insertImport(candidate, payload);
            store.appendAudit(importedAudit(candidate));
            store.appendAcceptedOutbox(event);
        })).isInstanceOf(DataAccessException.class);

        assertThat(store.findByExportId(candidate.exportId())).isEmpty();
        assertThat(store.findPayload(candidate.id())).isEmpty();
        assertThat(count("j7_import_audit", candidate.id())).isZero();
        assertThat(outboxCount(event.idempotencyKey())).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalImportsSerializeAndReturnOneDurableReceipt() throws Exception {
        byte[] payload = payload("concurrent-import");
        StoredJ7Import candidate = storedImport(
                UUID.fromString("10000000-0000-0000-0000-000000000031"),
                UUID.fromString("20000000-0000-0000-0000-000000000031"),
                payload,
                Instant.parse("2026-03-01T10:00:00Z"));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> importIfAbsent(candidate, payload, start));
            Future<Boolean> second = executor.submit(() -> importIfAbsent(candidate, payload, start));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(countByExportId(candidate.exportId())).isEqualTo(1);
        assertThat(outboxCount(outbox(candidate).idempotencyKey())).isEqualTo(1);
        assertThat(auditCount(candidate.id(), "IMPORTED")).isEqualTo(1);
    }

    private boolean importIfAbsent(
            StoredJ7Import candidate,
            byte[] payload,
            CountDownLatch start) throws InterruptedException {
        start.await();
        return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status -> {
            store.acquireImportLock(candidate.exportId(), candidate.idempotencyKey());
            Optional<StoredJ7Import> existing = store.findByExportId(candidate.exportId());
            if (existing.isPresent()) {
                return false;
            }
            store.insertImport(candidate, payload);
            store.appendAudit(importedAudit(candidate));
            store.appendAcceptedOutbox(outbox(candidate));
            return true;
        }));
    }

    private void persistAccepted(StoredJ7Import candidate, byte[] payload) {
        inTransaction(() -> {
            store.acquireImportLock(candidate.exportId(), candidate.idempotencyKey());
            store.insertImport(candidate, payload);
            store.appendAudit(importedAudit(candidate));
            store.appendAcceptedOutbox(outbox(candidate));
        });
    }

    private StoredJ7Import storedImport(
            UUID id,
            UUID exportId,
            byte[] payload,
            Instant receivedAt) {
        String fileSha256 = sha256(payload);
        return new StoredJ7Import(
                id,
                StoredJ7Import.idempotencyKey(exportId, fileSha256),
                exportId,
                CANONICAL_EVENT_ID,
                123456L,
                StoredJ7Import.PROTOCOL_VERSION,
                StoredJ7Import.SCHEMA_ID,
                StoredJ7Import.SCHEMA_VERSION,
                receivedAt.minusSeconds(120),
                "j7-exporter-1",
                StoredJ7Import.SELECTION_MODE,
                SOURCE_SET_SHA_256,
                StoredJ7Import.VALIDATION_STATUS,
                receivedAt.minusSeconds(60),
                fileSha256,
                DATA_SHA_256,
                CERTIFICATE_SHA_256,
                payload.length,
                receivedAt,
                receivedAt.plus(StoredJ7Import.DEFAULT_PAYLOAD_RETENTION),
                Optional.empty());
    }

    private J7ImportCommand command(UUID exportId, byte[] payload, Instant generatedAt) {
        String fileSha256 = sha256(payload);
        return new J7ImportCommand(
                StoredJ7Import.idempotencyKey(exportId, fileSha256),
                exportId,
                fileSha256,
                DATA_SHA_256,
                StoredJ7Import.SCHEMA_ID,
                StoredJ7Import.SCHEMA_VERSION,
                UUID.fromString("60000000-0000-4000-8000-000000000041"),
                123456L,
                generatedAt,
                "j7-exporter-1",
                SOURCE_SET_SHA_256,
                StoredJ7Import.VALIDATION_STATUS,
                generatedAt.plusSeconds(60),
                payload);
    }

    private J7ImportCommand commandFrom(StoredJ7Import receipt, byte[] payload) {
        return new J7ImportCommand(
                receipt.idempotencyKey(),
                receipt.exportId(),
                receipt.fileSha256(),
                receipt.dataSha256(),
                receipt.schemaId(),
                receipt.schemaVersion(),
                receipt.canonicalEventId(),
                receipt.providerEventId(),
                receipt.generatedAt(),
                receipt.generatorVersion(),
                receipt.sourceSetSha256(),
                receipt.validationStatus(),
                receipt.decidedAt(),
                payload);
    }

    private J7ImportAuditEntry importedAudit(StoredJ7Import candidate) {
        return new J7ImportAuditEntry(
                UUID.nameUUIDFromBytes(("audit:" + candidate.id()).getBytes(StandardCharsets.UTF_8)),
                candidate.id(),
                J7ImportAuditType.IMPORTED,
                candidate.idempotencyKey(),
                candidate.fileSha256(),
                candidate.dataSha256(),
                candidate.clientCertificateSha256(),
                J7ImportAuditReason.ACCEPTED,
                candidate.receivedAt());
    }

    private J7AcceptedOutboxEvent outbox(StoredJ7Import candidate) {
        return new J7AcceptedOutboxEvent(
                UUID.nameUUIDFromBytes(("outbox:" + candidate.id()).getBytes(StandardCharsets.UTF_8)),
                "j7-import-accepted:" + candidate.id(),
                candidate.id(),
                candidate.exportId(),
                candidate.canonicalEventId(),
                candidate.fileSha256(),
                candidate.dataSha256(),
                candidate.receivedAt());
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private J7ImportResult receive(
            J7ImportCommand command,
            String clientCertificateSha256) {
        return new TransactionTemplate(transactionManager).execute(status ->
                importService.receive(command, clientCertificateSha256));
    }

    private byte[] payload(String marker) {
        return ("{\"manifest\":{\"marker\":\"" + marker + "\"},\"data\":{}}\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String sha256(byte[] payload) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void seedOutboxKey(String idempotencyKey) {
        Instant now = Instant.parse("2026-02-01T09:00:00Z");
        jdbcClient.sql("""
                INSERT INTO outbox_message (
                    id, idempotency_key, aggregate_type, aggregate_id, destination,
                    payload_json, status, attempt_count, created_at, updated_at
                ) VALUES (
                    :id, :idempotencyKey, 'test', :aggregateId, 'test',
                    '{}', 'PENDING', 0, :now, :now
                )
                """)
                .param("id", UUID.randomUUID())
                .param("idempotencyKey", idempotencyKey)
                .param("aggregateId", UUID.randomUUID())
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }

    private void markOutboxDelivered(StoredJ7Import candidate, Instant deliveredAt) {
        jdbcClient.sql("""
                UPDATE outbox_message
                SET status = 'DELIVERED', delivered_at = :deliveredAt, updated_at = :deliveredAt
                WHERE idempotency_key = :idempotencyKey
                """)
                .param("deliveredAt", deliveredAt.atOffset(ZoneOffset.UTC))
                .param("idempotencyKey", outbox(candidate).idempotencyKey())
                .update();
    }

    private long count(String table, UUID importId) {
        String idColumn = table.equals("j7_import_receipt") ? "id" : "import_id";
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = :id")
                .param("id", importId)
                .query(Long.class)
                .single();
    }

    private long countByExportId(UUID exportId) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM j7_import_receipt WHERE export_id = :exportId
                """)
                .param("exportId", exportId)
                .query(Long.class)
                .single();
    }

    private long auditCount(UUID importId, String eventType) {
        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM j7_import_audit
                WHERE import_id = :importId AND event_type = :eventType
                """)
                .param("importId", importId)
                .param("eventType", eventType)
                .query(Long.class)
                .single();
    }

    private long tombstoneCount(UUID importId) {
        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM j7_import_payload_tombstone
                WHERE import_id = :importId
                """)
                .param("importId", importId)
                .query(Long.class)
                .single();
    }

    private long outboxCount(String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM outbox_message WHERE idempotency_key = :idempotencyKey
                """)
                .param("idempotencyKey", idempotencyKey)
                .query(Long.class)
                .single();
    }
}
