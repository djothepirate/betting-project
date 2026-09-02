package com.bettingproject.collection.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.application.imports.J7AcceptedOutboxEvent;
import com.bettingproject.collection.application.imports.J7ImportAuditEntry;
import com.bettingproject.collection.application.imports.J7ImportStore;
import com.bettingproject.collection.application.imports.StoredJ7Import;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class JdbcJ7ImportStore implements J7ImportStore {

    static final String LOCK_PREFIX = "betting-project:collection:j7-import:v1:";
    static final int MAXIMUM_PURGE_BATCH = 1000;

    private static final String SELECT_IMPORT = """
            SELECT receipt.id, receipt.idempotency_key, receipt.export_id,
                   receipt.canonical_event_id, receipt.provider_event_id,
                   receipt.protocol_version,
                   receipt.schema_id, receipt.schema_version, receipt.generated_at,
                   receipt.generator_version, receipt.selection_mode,
                   receipt.source_set_sha256, receipt.validation_status,
                   receipt.decided_at, receipt.file_sha256, receipt.data_sha256,
                   receipt.client_certificate_sha256, receipt.payload_size_bytes,
                   receipt.received_at, receipt.payload_expires_at,
                   tombstone.purged_at AS payload_purged_at
            FROM j7_import_receipt receipt
            LEFT JOIN j7_import_payload_tombstone tombstone
              ON tombstone.import_id = receipt.id
            """;

    private final JdbcClient jdbcClient;

    public JdbcJ7ImportStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void acquireImportLock(UUID exportId, String idempotencyKey) {
        Objects.requireNonNull(exportId, "exportId");
        StoredJ7Import.requireVisibleAscii(idempotencyKey, "idempotencyKey", 128);
        List.of(
                        LOCK_PREFIX + "export:" + exportId,
                        LOCK_PREFIX + "idempotency:" + idempotencyKey)
                .stream()
                .sorted()
                .forEach(this::acquireLock);
    }

    @Override
    public Optional<StoredJ7Import> findByIdempotencyKey(String idempotencyKey) {
        StoredJ7Import.requireVisibleAscii(idempotencyKey, "idempotencyKey", 128);
        return jdbcClient.sql(SELECT_IMPORT + " WHERE receipt.idempotency_key = :idempotencyKey")
                .param("idempotencyKey", idempotencyKey)
                .query(this::mapImport)
                .optional();
    }

    @Override
    public Optional<StoredJ7Import> findByExportId(UUID exportId) {
        Objects.requireNonNull(exportId, "exportId");
        return jdbcClient.sql(SELECT_IMPORT + " WHERE receipt.export_id = :exportId")
                .param("exportId", exportId)
                .query(this::mapImport)
                .optional();
    }

    @Override
    public void insertImport(StoredJ7Import storedImport, byte[] payload) {
        Objects.requireNonNull(storedImport, "storedImport");
        byte[] exactPayload = Objects.requireNonNull(payload, "payload").clone();
        if (storedImport.payloadPurgedAt().isPresent()) {
            throw new IllegalArgumentException("a new import cannot already be purged");
        }
        if (exactPayload.length != storedImport.payloadSizeBytes()) {
            throw new IllegalArgumentException("payload length differs from receipt metadata");
        }
        if (!MessageDigest.isEqual(
                java.util.HexFormat.of().parseHex(storedImport.fileSha256()),
                sha256(exactPayload))) {
            throw new IllegalArgumentException("payload hash differs from receipt metadata");
        }
        jdbcClient.sql("""
                WITH inserted_receipt AS (
                    INSERT INTO j7_import_receipt (
                        id, idempotency_key, export_id, canonical_event_id,
                        provider_event_id,
                        protocol_version, schema_id, schema_version, generated_at,
                        generator_version, selection_mode, source_set_sha256,
                        validation_status, decided_at, file_sha256, data_sha256,
                        client_certificate_sha256, payload_size_bytes, received_at,
                        payload_expires_at
                    ) VALUES (
                        :id, :idempotencyKey, :exportId, :canonicalEventId,
                        :providerEventId,
                        :protocolVersion, :schemaId, :schemaVersion, :generatedAt,
                        :generatorVersion, :selectionMode, :sourceSetSha256,
                        :validationStatus, :decidedAt, :fileSha256, :dataSha256,
                        :clientCertificateSha256, :payloadSizeBytes, :receivedAt,
                        :payloadExpiresAt
                    )
                    RETURNING id, file_sha256, payload_size_bytes
                )
                INSERT INTO j7_import_payload (
                    import_id, file_sha256, payload_size_bytes, payload
                )
                SELECT id, file_sha256, payload_size_bytes, :payload
                FROM inserted_receipt
                """)
                .param("id", storedImport.id())
                .param("idempotencyKey", storedImport.idempotencyKey())
                .param("exportId", storedImport.exportId())
                .param("canonicalEventId", storedImport.canonicalEventId())
                .param("providerEventId", storedImport.providerEventId())
                .param("protocolVersion", storedImport.protocolVersion())
                .param("schemaId", storedImport.schemaId())
                .param("schemaVersion", storedImport.schemaVersion())
                .param("generatedAt", utc(storedImport.generatedAt()))
                .param("generatorVersion", storedImport.generatorVersion())
                .param("selectionMode", storedImport.selectionMode())
                .param("sourceSetSha256", storedImport.sourceSetSha256())
                .param("validationStatus", storedImport.validationStatus())
                .param("decidedAt", utc(storedImport.decidedAt()))
                .param("fileSha256", storedImport.fileSha256())
                .param("dataSha256", storedImport.dataSha256())
                .param("clientCertificateSha256", storedImport.clientCertificateSha256())
                .param("payloadSizeBytes", storedImport.payloadSizeBytes())
                .param("receivedAt", utc(storedImport.receivedAt()))
                .param("payloadExpiresAt", utc(storedImport.payloadExpiresAt()))
                .param("payload", exactPayload)
                .update();
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public Optional<byte[]> findPayload(UUID importId) {
        Objects.requireNonNull(importId, "importId");
        return jdbcClient.sql("""
                SELECT payload
                FROM j7_import_payload
                WHERE import_id = :importId
                """)
                .param("importId", importId)
                .query((resultSet, rowNumber) -> resultSet.getBytes("payload"))
                .optional()
                .map(byte[]::clone);
    }

    @Override
    public void appendAudit(J7ImportAuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        jdbcClient.sql("""
                INSERT INTO j7_import_audit (
                    id, import_id, event_type, request_idempotency_key,
                    observed_file_sha256, observed_data_sha256,
                    client_certificate_sha256, reason_code, occurred_at
                ) VALUES (
                    :id, :importId, :eventType, :requestIdempotencyKey,
                    :observedFileSha256, :observedDataSha256,
                    :clientCertificateSha256, :reasonCode, :occurredAt
                )
                """)
                .param("id", entry.id())
                .param("importId", entry.importId())
                .param("eventType", entry.type().name())
                .param("requestIdempotencyKey", entry.requestIdempotencyKey())
                .param("observedFileSha256", entry.observedFileSha256())
                .param("observedDataSha256", entry.observedDataSha256())
                .param("clientCertificateSha256", entry.clientCertificateSha256())
                .param("reasonCode", entry.reason().name())
                .param("occurredAt", utc(entry.occurredAt()))
                .update();
    }

    @Override
    public void appendAcceptedOutbox(J7AcceptedOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        jdbcClient.sql("""
                INSERT INTO outbox_message (
                    id, idempotency_key, aggregate_type, aggregate_id, destination,
                    payload_json, status, attempt_count, next_attempt_at,
                    created_at, updated_at
                ) VALUES (
                    :id, :idempotencyKey, 'j7_import_receipt', :importId,
                    'J7_IMPORT_ACCEPTED',
                    jsonb_build_object(
                        'eventType', 'J7_IMPORT_ACCEPTED',
                        'importId', :importId,
                        'exportId', :exportId,
                        'canonicalEventId', :canonicalEventId,
                        'fileSha256', :fileSha256,
                        'dataSha256', :dataSha256,
                        'occurredAt', :occurredAt
                    ),
                    'PENDING', 0, :occurredAt, :occurredAt, :occurredAt
                )
                """)
                .param("id", event.id())
                .param("idempotencyKey", event.idempotencyKey())
                .param("importId", event.importId())
                .param("exportId", event.exportId())
                .param("canonicalEventId", event.canonicalEventId())
                .param("fileSha256", event.fileSha256())
                .param("dataSha256", event.dataSha256())
                .param("occurredAt", utc(event.occurredAt()))
                .update();
    }

    @Override
    public int purgeExpiredPayloads(
            Instant expiredBefore,
            Instant purgedAt,
            int maximumRows) {
        Objects.requireNonNull(expiredBefore, "expiredBefore");
        Objects.requireNonNull(purgedAt, "purgedAt");
        if (maximumRows < 1 || maximumRows > MAXIMUM_PURGE_BATCH) {
            throw new IllegalArgumentException("maximumRows must be between 1 and 1000");
        }
        if (purgedAt.isBefore(expiredBefore)) {
            throw new IllegalArgumentException("purgedAt cannot precede the expiry cutoff");
        }
        Integer purged = jdbcClient.sql("""
                SELECT purge_j7_import_payloads(
                    :expiredBefore,
                    :purgedAt,
                    :maximumRows
                )
                """)
                .param("expiredBefore", utc(expiredBefore))
                .param("purgedAt", utc(purgedAt))
                .param("maximumRows", maximumRows)
                .query(Integer.class)
                .single();
        return purged;
    }

    private void acquireLock(String lockName) {
        jdbcClient.sql("""
                SELECT pg_advisory_xact_lock(hashtextextended(:lockName, 0))
                """)
                .param("lockName", lockName)
                .query((resultSet, rowNumber) -> Boolean.TRUE)
                .single();
    }

    private StoredJ7Import mapImport(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime purgedAt = resultSet.getObject("payload_purged_at", OffsetDateTime.class);
        return new StoredJ7Import(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("idempotency_key"),
                resultSet.getObject("export_id", UUID.class),
                resultSet.getObject("canonical_event_id", UUID.class),
                resultSet.getLong("provider_event_id"),
                resultSet.getString("protocol_version"),
                resultSet.getString("schema_id"),
                resultSet.getString("schema_version"),
                resultSet.getObject("generated_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("generator_version"),
                resultSet.getString("selection_mode"),
                resultSet.getString("source_set_sha256"),
                resultSet.getString("validation_status"),
                resultSet.getObject("decided_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("file_sha256"),
                resultSet.getString("data_sha256"),
                resultSet.getString("client_certificate_sha256"),
                resultSet.getLong("payload_size_bytes"),
                resultSet.getObject("received_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("payload_expires_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(purgedAt).map(OffsetDateTime::toInstant));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
