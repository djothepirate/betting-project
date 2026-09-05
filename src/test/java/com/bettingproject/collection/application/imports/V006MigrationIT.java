package com.bettingproject.collection.application.imports;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V006MigrationIT {

    private static final String EXISTING_OUTBOX_ID =
            "10000000-0000-0000-0000-000000000001";

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migratesPopulatedV005AndV006WithoutLosingExistingTombstones() throws Exception {
        migrateToV005();
        try (Connection connection = connection()) {
            executeUpdate(connection, """
                    INSERT INTO outbox_message (
                        id, idempotency_key, aggregate_type, aggregate_id, destination,
                        payload_json, status, attempt_count, created_at, updated_at
                    ) VALUES (
                        '%s', 'existing-v005-outbox', 'test', '%s', 'test',
                        '{}', 'PENDING', 0,
                        '2026-09-02T08:00:00Z', '2026-09-02T08:00:00Z'
                    )
                    """.formatted(EXISTING_OUTBOX_ID, EXISTING_OUTBOX_ID));
        }

        migrateToV006();
        try (Connection connection = connection()) {
            executeUpdate(connection, validV006ReceiptAndTombstoneSql());
        }

        migrateToLatest();

        try (Connection connection = connection()) {
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM outbox_message"))
                    .isEqualTo(2);
            assertThat(singleLong(connection, """
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE version = '006' AND success
                    """)).isEqualTo(1);
            assertThat(singleLong(connection, """
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE version = '007' AND success
                    """)).isEqualTo(1);
            assertThat(singleLong(connection, """
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE version = '008' AND success
                    """)).isEqualTo(1);
            assertThat(singleLong(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'j7_import_payload_tombstone'
                      AND column_name = 'payload_expires_at'
                      AND is_nullable = 'NO'
                    """)).isEqualTo(1);
            assertThat(singleLong(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN (
                        'j7_import_receipt',
                        'j7_import_payload',
                        'j7_import_audit',
                        'j7_import_payload_tombstone'
                      )
                    """)).isEqualTo(4);
            assertThat(singleLong(connection, """
                    SELECT COUNT(*)
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname IN (
                        'ix_j7_import_receipt_received',
                        'ix_j7_import_receipt_payload_expiry',
                        'ix_j7_import_audit_history',
                        'ix_j7_import_audit_chronology'
                      )
                    """)).isEqualTo(4);
            assertThat(singleLong(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.triggers
                    WHERE event_object_schema = 'public'
                      AND trigger_name IN (
                        'trg_j7_import_receipt_immutable',
                        'trg_j7_import_payload_immutable',
                        'trg_j7_import_audit_append_only',
                        'trg_j7_import_payload_bounded_delete',
                        'trg_j7_import_payload_tombstone_immutable'
                      )
                    """)).isEqualTo(8);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM j7_import_receipt"))
                    .isEqualTo(1);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM j7_import_payload"))
                    .isZero();
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM j7_import_audit"))
                    .isEqualTo(1);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM j7_import_payload_tombstone"))
                    .isEqualTo(1);
            assertThat(singleLong(connection, """
                    SELECT COUNT(*)
                    FROM j7_import_payload_tombstone
                    WHERE import_id = '20000000-0000-4000-8000-000000000001'
                      AND payload_expires_at = '2026-08-31T08:02:00Z'
                    """)).isEqualTo(1);

            assertThatThrownBy(() -> executeUpdate(connection, invalidReceiptSql()))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void refusesFutureDatedV006PurgeEvidenceDuringV008Upgrade() throws Exception {
        try (PostgreSQLContainer<?> isolated =
                     new PostgreSQLContainer<>("postgres:17-alpine")) {
            isolated.start();
            Flyway.configure()
                    .dataSource(
                            isolated.getJdbcUrl(),
                            isolated.getUsername(),
                            isolated.getPassword())
                    .target(MigrationVersion.fromVersion("006"))
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    isolated.getJdbcUrl(),
                    isolated.getUsername(),
                    isolated.getPassword())) {
                executeUpdate(connection, futureDatedV006PurgeSql());
            }

            assertThatThrownBy(() -> Flyway.configure()
                    .dataSource(
                            isolated.getJdbcUrl(),
                            isolated.getUsername(),
                            isolated.getPassword())
                    .load()
                    .migrate())
                    .hasStackTraceContaining(
                            "V006/V007 J7 tombstone evidence is future-dated");
        }
    }

    private void migrateToV005() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("005"))
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .load()
                .migrate();
    }

    private void migrateToV006() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("006"))
                .load()
                .migrate();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    }

    private String invalidReceiptSql() {
        return """
                INSERT INTO j7_import_receipt (
                    id, idempotency_key, export_id, canonical_event_id,
                    provider_event_id,
                    protocol_version, schema_id, schema_version, generated_at,
                    generator_version, selection_mode, source_set_sha256,
                    validation_status, decided_at, file_sha256, data_sha256,
                    client_certificate_sha256, payload_size_bytes, received_at,
                    payload_expires_at
                ) VALUES (
                    '20000000-0000-0000-0000-000000000001',
                    'j7:wrong-identity:sha256:%s',
                    '30000000-0000-0000-0000-000000000001',
                    '40000000-0000-0000-0000-000000000001',
                    123456,
                    '1.0',
                    'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1',
                    '1.0.0', '2026-09-01T08:00:00Z', 'j7-exporter-1',
                    'LATEST_AVAILABLE', '%s', 'HUMAN_VALIDATED',
                    '2026-09-01T08:01:00Z', '%s', '%s', '%s', 2,
                    '2026-09-01T08:02:00Z', '2026-10-01T08:02:00Z'
                )
                """.formatted(
                "a".repeat(64),
                "b".repeat(64),
                "a".repeat(64),
                "c".repeat(64),
                "d".repeat(64));
    }

    private String validV006ReceiptAndTombstoneSql() {
        return """
                INSERT INTO j7_import_receipt (
                    id, idempotency_key, export_id, canonical_event_id,
                    provider_event_id,
                    protocol_version, schema_id, schema_version, generated_at,
                    generator_version, selection_mode, source_set_sha256,
                    validation_status, decided_at, file_sha256, data_sha256,
                    client_certificate_sha256, payload_size_bytes, received_at,
                    payload_expires_at
                ) VALUES (
                    '20000000-0000-4000-8000-000000000001',
                    'j7:30000000-0000-4000-8000-000000000001:sha256:%s',
                    '30000000-0000-4000-8000-000000000001',
                    '40000000-0000-4000-8000-000000000001',
                    123456,
                    '1.0',
                    'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1',
                    '1.0.0', '2026-08-01T08:00:00Z', 'j7-exporter-1',
                    'LATEST_AVAILABLE', '%s', 'HUMAN_VALIDATED',
                    '2026-08-01T08:01:00Z', '%s', '%s', '%s', 2,
                    '2026-08-01T08:02:00Z', '2026-08-31T08:02:00Z'
                );

                INSERT INTO j7_import_payload (
                    import_id, file_sha256, payload_size_bytes, payload
                ) VALUES (
                    '20000000-0000-4000-8000-000000000001',
                    '%s', 2, decode('7b7d', 'hex')
                );

                INSERT INTO outbox_message (
                    id, idempotency_key, aggregate_type, aggregate_id, destination,
                    payload_json, status, attempt_count, delivered_at, created_at, updated_at
                ) VALUES (
                    '50000000-0000-4000-8000-000000000001',
                    'j7-import-accepted:20000000-0000-4000-8000-000000000001',
                    'j7_import_receipt',
                    '20000000-0000-4000-8000-000000000001',
                    'J7_IMPORT_ACCEPTED', '{}', 'DELIVERED', 0,
                    '2026-08-01T08:02:00Z',
                    '2026-08-01T08:02:00Z',
                    '2026-08-01T08:02:00Z'
                );

                DO $$
                BEGIN
                    PERFORM purge_j7_import_payloads(
                        '2026-08-31T08:02:00Z',
                        '2026-09-01T08:02:00Z',
                        1
                    );
                END;
                $$
                """.formatted(
                "a".repeat(64),
                "b".repeat(64),
                "a".repeat(64),
                "c".repeat(64),
                "d".repeat(64),
                "a".repeat(64));
    }

    private String futureDatedV006PurgeSql() {
        return """
                INSERT INTO j7_import_receipt (
                    id, idempotency_key, export_id, canonical_event_id,
                    provider_event_id,
                    protocol_version, schema_id, schema_version, generated_at,
                    generator_version, selection_mode, source_set_sha256,
                    validation_status, decided_at, file_sha256, data_sha256,
                    client_certificate_sha256, payload_size_bytes, received_at,
                    payload_expires_at
                ) VALUES (
                    '60000000-0000-4000-8000-000000000001',
                    'j7:70000000-0000-4000-8000-000000000001:sha256:%s',
                    '70000000-0000-4000-8000-000000000001',
                    '80000000-0000-4000-8000-000000000001',
                    123456,
                    '1.0',
                    'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1',
                    '1.0.0', '2026-07-01T08:00:00Z', 'j7-exporter-1',
                    'LATEST_AVAILABLE', '%s', 'HUMAN_VALIDATED',
                    '2026-07-01T08:01:00Z', '%s', '%s', '%s', 2,
                    '2026-07-01T08:02:00Z', '2026-07-31T08:02:00Z'
                );

                INSERT INTO j7_import_payload (
                    import_id, file_sha256, payload_size_bytes, payload
                ) VALUES (
                    '60000000-0000-4000-8000-000000000001',
                    '%s', 2, decode('7b7d', 'hex')
                );

                INSERT INTO outbox_message (
                    id, idempotency_key, aggregate_type, aggregate_id, destination,
                    payload_json, status, attempt_count, delivered_at, created_at, updated_at
                ) VALUES (
                    '90000000-0000-4000-8000-000000000001',
                    'j7-import-accepted:60000000-0000-4000-8000-000000000001',
                    'j7_import_receipt',
                    '60000000-0000-4000-8000-000000000001',
                    'J7_IMPORT_ACCEPTED', '{}', 'DELIVERED', 0,
                    '2026-07-01T08:02:00Z',
                    '2026-07-01T08:02:00Z',
                    '2026-07-01T08:02:00Z'
                );

                DO $$
                BEGIN
                    PERFORM purge_j7_import_payloads(
                        '2099-01-01T00:00:00Z',
                        '2099-01-01T00:00:01Z',
                        1
                    );
                END;
                $$;
                """.formatted(
                "a".repeat(64),
                "b".repeat(64),
                "a".repeat(64),
                "c".repeat(64),
                "d".repeat(64),
                "a".repeat(64));
    }

    private void executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private long singleLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
