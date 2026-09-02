package com.bettingproject.catalog.application;

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
class V005MigrationIT {

    private static final String SNAPSHOT_ID = "10000000-0000-0000-0000-000000000001";
    private static final String RECEIPT_ID = "20000000-0000-0000-0000-000000000001";
    private static final String REQUEST_ID = "30000000-0000-0000-0000-000000000001";
    private static final String SHA_256 = "a".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migratesPopulatedV004WithoutInventingReplayHistory() throws Exception {
        migrateToV004();
        try (Connection connection = connection()) {
            seedPopulatedV004(connection);
        }

        migrateToLatest();

        try (Connection connection = connection()) {
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM raw_snapshot")).isEqualTo(1);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM control_command_receipt")).isEqualTo(1);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM normalization_replay_request")).isZero();
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM normalization_replay_attempt")).isZero();
            assertThat(singleLong(connection, """
                    SELECT COUNT(*) FROM normalization_replay_attempt_application
                    """)).isZero();
            assertThat(singleLong(connection, """
                    SELECT COUNT(*) FROM normalization_replay_attempt_anomaly_event
                    """)).isZero();
            assertThat(singleLong(connection, """
                    SELECT COUNT(*) FROM flyway_schema_history WHERE version = '005' AND success
                    """)).isEqualTo(1);

            assertSchemaObjects(connection);
            assertRequestAndAttemptConstraints(connection);
        }
    }

    private void migrateToV004() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("004"))
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .load()
                .migrate();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    }

    private void seedPopulatedV004(Connection connection) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO raw_snapshot (
                    id, provider, endpoint, received_at, payload_sha256,
                    payload_compression, payload, connector_version
                ) VALUES (
                    '%s', 'synthetic', 'calendar/test', '2026-09-01T08:00:00Z',
                    '%s', 'identity', decode('7b7d', 'hex'), 'fixture-parser-3'
                )
                """.formatted(SNAPSHOT_ID, SHA_256));
        executeUpdate(connection, """
                INSERT INTO control_command_receipt (
                    id, idempotency_key, command_type, command_sha256,
                    result_resource_id, created_at
                ) VALUES (
                    '%s', 'replay-migration-fixture', 'REPLAY_REQUEST', '%s',
                    '%s', '2026-09-01T08:01:00Z'
                )
                """.formatted(RECEIPT_ID, "b".repeat(64), REQUEST_ID));
    }

    private void assertSchemaObjects(Connection connection) throws SQLException {
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'normalization_replay_request',
                    'normalization_replay_attempt',
                    'normalization_replay_attempt_application',
                    'normalization_replay_attempt_anomaly_event'
                  )
                """)).isEqualTo(4);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                    'ix_raw_snapshot_payload_sha256',
                    'uq_normalization_replay_request_mapping_snapshot',
                    'ix_normalization_replay_request_status',
                    'ix_normalization_replay_request_snapshot',
                    'ix_normalization_replay_request_decision',
                    'ix_normalization_replay_attempt_history',
                    'ix_normalization_replay_attempt_page',
                    'ix_normalization_replay_attempt_application_log',
                    'ix_normalization_replay_attempt_anomaly_event_log'
                  )
                """)).isEqualTo(9);
    }

    private void assertRequestAndAttemptConstraints(Connection connection) throws SQLException {
        insertPendingRequest(connection, REQUEST_ID, "SNAPSHOT_ID", SNAPSHOT_ID, "MANUAL", null);

        assertThatThrownBy(() -> insertPendingRequest(
                connection,
                "30000000-0000-0000-0000-000000000002",
                "PAYLOAD_SHA256",
                "not-a-hash",
                "MANUAL",
                null)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeUpdate(connection, """
                UPDATE normalization_replay_request
                SET status = 'COMPLETED', completed_at = NULL
                WHERE id = '%s'
                """.formatted(REQUEST_ID))).isInstanceOf(SQLException.class);

        executeUpdate(connection, """
                INSERT INTO normalization_replay_attempt (
                    id, normalization_replay_request_id, attempt_number, outcome,
                    expected_payload_sha256, actual_payload_sha256,
                    error_code, error_message, started_at, finished_at
                ) VALUES (
                    '40000000-0000-0000-0000-000000000001', '%s', 1,
                    'FAILED_TERMINAL', '%s', '%s', 'PAYLOAD_HASH_MISMATCH',
                    'Stored payload fingerprint differs',
                    '2026-09-01T08:02:00Z', '2026-09-01T08:02:01Z'
                )
                """.formatted(REQUEST_ID, SHA_256, "c".repeat(64)));
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM normalization_replay_attempt"))
                .isEqualTo(1);

        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO normalization_replay_attempt (
                    id, normalization_replay_request_id, attempt_number, outcome,
                    expected_payload_sha256, actual_payload_sha256,
                    compatible, started_at, finished_at
                ) VALUES (
                    '40000000-0000-0000-0000-000000000002', '%s', 2,
                    'COMPLETED', '%s', '%s', TRUE,
                    '2026-09-01T08:03:00Z', '2026-09-01T08:03:01Z'
                )
                """.formatted(REQUEST_ID, SHA_256, SHA_256))).isInstanceOf(SQLException.class);
    }

    private void insertPendingRequest(
            Connection connection,
            String id,
            String selectorType,
            String selectorValue,
            String origin,
            String mappingDecisionId) throws SQLException {
        String decisionLiteral = mappingDecisionId == null ? "NULL" : "'" + mappingDecisionId + "'";
        executeUpdate(connection, """
                INSERT INTO normalization_replay_request (
                    id, control_command_receipt_id, raw_snapshot_id,
                    expected_payload_sha256, provider_mapping_decision_id,
                    origin, selector_type, selector_value, status, version,
                    attempt_count, created_at, updated_at
                ) VALUES (
                    '%s', '%s', '%s', '%s', %s,
                    '%s', '%s', '%s', 'PENDING', 1, 0,
                    '2026-09-01T08:01:00Z', '2026-09-01T08:01:00Z'
                )
                """.formatted(
                id,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA_256,
                decisionLiteral,
                origin,
                selectorType,
                selectorValue));
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
