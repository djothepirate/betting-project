package com.bettingproject.catalog.application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V004MigrationIT {

    private static final String CONFIRMED_MAPPING_ID = "10000000-0000-0000-0000-000000000001";
    private static final String AMBIGUOUS_MAPPING_ID = "10000000-0000-0000-0000-000000000002";
    private static final String REJECTED_MAPPING_ID = "10000000-0000-0000-0000-000000000003";
    private static final String CANONICAL_TEAM_ID = "20000000-0000-0000-0000-000000000001";
    private static final String SNAPSHOT_ID = "30000000-0000-0000-0000-000000000001";
    private static final String OPEN_ANOMALY_ID = "40000000-0000-0000-0000-000000000001";
    private static final String RESOLVED_ANOMALY_ID = "40000000-0000-0000-0000-000000000002";
    private static final String IGNORED_ANOMALY_ID = "40000000-0000-0000-0000-000000000003";

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migratesPopulatedV003WithoutInventingHumanHistory() throws Exception {
        migrateToV003();

        try (Connection connection = connection()) {
            seedPopulatedV003(connection);
            assertV003Baseline(connection);
        }

        migrateToLatest();

        try (Connection connection = connection()) {
            assertExistingMappingAndAnomalyFactsArePreserved(connection);
            assertCompatibilityBackfills(connection);
            assertNewSchemaObjectsExist(connection);
            assertNewConstraintsRejectInconsistentRows(connection);
        }
    }

    private void migrateToV003() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("003"))
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

    private void seedPopulatedV003(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO provider_mapping (
                        id, provider, entity_type, provider_entity_id, canonical_entity_id,
                        season, phase, confidence, mapping_status, created_at, updated_at
                    ) VALUES
                        ('%s', 'highlightly', 'TEAM', 'team-confirmed', '%s',
                         '2026', '', 1.0000, 'CONFIRMED',
                         '2026-08-01T00:00:00Z', '2026-08-02T00:00:00Z'),
                        ('%s', 'highlightly', 'TEAM', 'team-ambiguous', NULL,
                         '2026', '', 0.4500, 'AMBIGUOUS',
                         '2026-08-03T00:00:00Z', '2026-08-04T00:00:00Z'),
                        ('%s', 'highlightly', 'TEAM', 'team-rejected', NULL,
                         '2026', '', NULL, 'REJECTED',
                         '2026-08-05T00:00:00Z', '2026-08-06T00:00:00Z')
                    """.formatted(
                    CONFIRMED_MAPPING_ID,
                    CANONICAL_TEAM_ID,
                    AMBIGUOUS_MAPPING_ID,
                    REJECTED_MAPPING_ID));
            statement.executeUpdate("""
                    INSERT INTO raw_snapshot (
                        id, provider, endpoint, received_at, payload_sha256, payload_compression,
                        payload, connector_version
                    ) VALUES (
                        '%s', 'highlightly', 'calendar/test', '2026-08-10T10:00:00Z',
                        '%s', 'NONE', decode('7b7d', 'hex'), 'fixture-parser-3'
                    )
                    """.formatted(SNAPSHOT_ID, "a".repeat(64)));
            statement.executeUpdate("""
                    INSERT INTO normalization_anomaly (
                        id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                        anomaly_code, details, status, created_at, resolved_at
                    ) VALUES
                        ('%s', '%s', 'highlightly', 'TEAM', 'team-open',
                         'MISSING_MAPPING', 'Open migration fixture', 'OPEN',
                         '2026-08-10T10:01:00Z', NULL),
                        ('%s', '%s', 'highlightly', 'TEAM', 'team-resolved',
                         'MISSING_MAPPING', 'Resolved migration fixture', 'RESOLVED',
                         '2026-08-10T10:02:00Z', '2026-08-10T11:02:00Z'),
                        ('%s', '%s', 'highlightly', 'TEAM', 'team-ignored',
                         'AMBIGUOUS_MAPPING', 'Ignored migration fixture', 'IGNORED',
                         '2026-08-10T10:03:00Z', NULL)
                    """.formatted(
                    OPEN_ANOMALY_ID,
                    SNAPSHOT_ID,
                    RESOLVED_ANOMALY_ID,
                    SNAPSHOT_ID,
                    IGNORED_ANOMALY_ID,
                    SNAPSHOT_ID));
        }
    }

    private void assertV003Baseline(Connection connection) throws SQLException {
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM provider_mapping")).isEqualTo(3);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM normalization_anomaly")).isEqualTo(3);
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '003' AND success
                """)).isEqualTo(1);
    }

    private void assertExistingMappingAndAnomalyFactsArePreserved(Connection connection)
            throws SQLException {
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM provider_mapping")).isEqualTo(3);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM normalization_anomaly")).isEqualTo(3);
        assertThat(singleString(connection, """
                SELECT canonical_entity_id::text FROM provider_mapping
                WHERE id = '%s'
                """.formatted(CONFIRMED_MAPPING_ID))).isEqualTo(CANONICAL_TEAM_ID);
        assertThat(singleString(connection, """
                SELECT mapping_status FROM provider_mapping WHERE id = '%s'
                """.formatted(AMBIGUOUS_MAPPING_ID))).isEqualTo("AMBIGUOUS");
        assertThat(singleString(connection, """
                SELECT confidence::text FROM provider_mapping WHERE id = '%s'
                """.formatted(AMBIGUOUS_MAPPING_ID))).isEqualTo("0.4500");
        assertThat(singleString(connection, """
                SELECT mapping_status FROM provider_mapping WHERE id = '%s'
                """.formatted(REJECTED_MAPPING_ID))).isEqualTo("REJECTED");
        assertThat(singleString(connection, """
                SELECT status FROM normalization_anomaly WHERE id = '%s'
                """.formatted(RESOLVED_ANOMALY_ID))).isEqualTo("RESOLVED");
        assertThat(singleInstant(connection, """
                SELECT resolved_at FROM normalization_anomaly WHERE id = '%s'
                """.formatted(RESOLVED_ANOMALY_ID)))
                .isEqualTo(Instant.parse("2026-08-10T11:02:00Z"));
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '004' AND success
                """)).isEqualTo(1);
    }

    private void assertCompatibilityBackfills(Connection connection) throws SQLException {
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM provider_mapping WHERE version = 1
                """)).isEqualTo(3);
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM normalization_anomaly
                WHERE version = 1
                  AND occurrence_count = 1
                  AND last_seen_at = created_at
                  AND updated_at = created_at
                  AND season IS NULL
                  AND phase IS NULL
                """)).isEqualTo(3);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM control_command_receipt")).isZero();
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM provider_mapping_decision")).isZero();
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM provider_mapping_decision_anomaly
                """)).isZero();
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM normalization_anomaly_event")).isZero();
        assertThat(singleLong(connection, """
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'provider_mapping'
                  AND column_name = 'season'
                """)).isEqualTo(64);
    }

    private void assertNewSchemaObjectsExist(Connection connection) throws SQLException {
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'control_command_receipt',
                    'provider_mapping_decision',
                    'provider_mapping_decision_anomaly',
                    'normalization_anomaly_event'
                  )
                """)).isEqualTo(4);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND (
                    (table_name = 'provider_mapping' AND column_name = 'version')
                    OR (
                        table_name = 'normalization_anomaly'
                        AND column_name IN (
                            'season', 'phase', 'version', 'last_seen_at',
                            'updated_at', 'occurrence_count'
                        )
                    )
                  )
                """)).isEqualTo(7);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                    'ix_provider_mapping_page',
                    'ix_provider_mapping_status_provider_page',
                    'ix_provider_mapping_canonical_page',
                    'ix_normalization_anomaly_page',
                    'ix_normalization_anomaly_mapping_context',
                    'ix_provider_mapping_decision_history',
                    'ix_provider_mapping_decision_page',
                    'ix_normalization_anomaly_event_page',
                    'ix_normalization_anomaly_event_history',
                    'ix_provider_mapping_decision_anomaly_snapshot'
                  )
                """)).isEqualTo(10);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname IN (
                    'ck_provider_mapping_version',
                    'uq_normalization_anomaly_context',
                    'ck_normalization_anomaly_version',
                    'ck_normalization_anomaly_occurrences',
                    'ck_control_command_receipt_key',
                    'ck_control_command_receipt_sha256',
                    'ck_provider_mapping_decision_versions',
                    'ck_provider_mapping_decision_previous',
                    'ck_provider_mapping_decision_result',
                    'ck_provider_mapping_decision_operator',
                    'ck_provider_mapping_decision_justification',
                    'ck_normalization_anomaly_event_type',
                    'ck_normalization_anomaly_event_statuses'
                  )
                """)).isEqualTo(13);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM pg_constraint constraint_definition
                JOIN pg_class relation
                  ON relation.oid = constraint_definition.conrelid
                WHERE constraint_definition.contype = 'f'
                  AND relation.relname IN (
                    'provider_mapping_decision',
                    'normalization_anomaly_event',
                    'provider_mapping_decision_anomaly'
                  )
                """)).isEqualTo(6);
        assertThat(singleBoolean(connection, """
                SELECT index_definition.indnullsnotdistinct
                FROM pg_index index_definition
                WHERE index_definition.indexrelid =
                    'uq_normalization_anomaly_context'::regclass
                """)).isTrue();
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND (
                    (table_name = 'provider_mapping' AND column_name = 'version')
                    OR (
                        table_name = 'normalization_anomaly'
                        AND column_name IN (
                            'version', 'last_seen_at', 'updated_at', 'occurrence_count'
                        )
                    )
                  )
                  AND is_nullable = 'NO'
                  AND column_default IS NULL
                """)).isEqualTo(5);
    }

    private void assertNewConstraintsRejectInconsistentRows(Connection connection) throws SQLException {
        assertThatThrownBy(() -> executeUpdate(connection, """
                UPDATE provider_mapping SET version = 0 WHERE id = '%s'
                """.formatted(CONFIRMED_MAPPING_ID))).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeUpdate(connection, """
                UPDATE normalization_anomaly SET occurrence_count = 0 WHERE id = '%s'
                """.formatted(OPEN_ANOMALY_ID))).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO normalization_anomaly (
                    id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                    season, phase, anomaly_code, details, status, version,
                    created_at, last_seen_at, updated_at, occurrence_count
                ) VALUES (
                    '40000000-0000-0000-0000-000000000099', '%s', 'highlightly',
                    'TEAM', 'team-open', NULL, NULL, 'MISSING_MAPPING',
                    'Duplicate null context', 'OPEN', 1,
                    '2026-08-10T10:04:00Z', '2026-08-10T10:04:00Z',
                    '2026-08-10T10:04:00Z', 1
                )
                """.formatted(SNAPSHOT_ID))).isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> insertReceipt(
                connection,
                "50000000-0000-0000-0000-000000000001",
                "contains space",
                "a".repeat(64),
                "60000000-0000-0000-0000-000000000001"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertReceipt(
                connection,
                "50000000-0000-0000-0000-000000000002",
                "valid-key",
                "NOT_A_SHA256",
                "60000000-0000-0000-0000-000000000002"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertReceipt(
                connection,
                "50000000-0000-0000-0000-000000000009",
                "clé-non-ascii",
                "f".repeat(64),
                "60000000-0000-0000-0000-000000000009"))
                .isInstanceOf(SQLException.class);

        insertReceipt(
                connection,
                "50000000-0000-0000-0000-000000000003",
                "valid-decision-key",
                "b".repeat(64),
                "60000000-0000-0000-0000-000000000003");

        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO provider_mapping_decision (
                    id, provider_mapping_id, control_command_receipt_id, decision_type,
                    expected_version, resulting_version, previous_mapping_status,
                    previous_canonical_entity_id, previous_confidence,
                    resulting_mapping_status, resulting_canonical_entity_id,
                    resulting_confidence, operator_id, justification, created_at
                ) VALUES (
                    '60000000-0000-0000-0000-000000000003', '%s',
                    '50000000-0000-0000-0000-000000000003', 'REJECT',
                    1, 3, 'CONFIRMED', '%s', 1.0000,
                    'CONFIRMED', '%s', 1.0000,
                    'operator', 'Valid justification', '2026-09-01T10:00:00Z'
                )
                """.formatted(
                CONFIRMED_MAPPING_ID,
                CANONICAL_TEAM_ID,
                CANONICAL_TEAM_ID))).isInstanceOf(SQLException.class);

        insertReceipt(
                connection,
                "50000000-0000-0000-0000-000000000005",
                "missing-previous-key",
                "d".repeat(64),
                "60000000-0000-0000-0000-000000000005");
        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO provider_mapping_decision (
                    id, provider_mapping_id, control_command_receipt_id, decision_type,
                    expected_version, resulting_version, previous_mapping_status,
                    resulting_mapping_status, resulting_canonical_entity_id,
                    resulting_confidence, operator_id, justification, created_at
                ) VALUES (
                    '60000000-0000-0000-0000-000000000005', '%s',
                    '50000000-0000-0000-0000-000000000005', 'CONFIRM',
                    1, 2, NULL, 'CONFIRMED', '%s', 1.0000,
                    'operator', 'Valid justification', '2026-09-01T10:00:30Z'
                )
                """.formatted(CONFIRMED_MAPPING_ID, CANONICAL_TEAM_ID)))
                .isInstanceOf(SQLException.class);

        insertReceipt(
                connection,
                "50000000-0000-0000-0000-000000000006",
                "missing-result-confidence-key",
                "e".repeat(64),
                "60000000-0000-0000-0000-000000000006");
        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO provider_mapping_decision (
                    id, provider_mapping_id, control_command_receipt_id, decision_type,
                    expected_version, resulting_version, previous_mapping_status,
                    previous_canonical_entity_id, previous_confidence,
                    resulting_mapping_status, resulting_canonical_entity_id,
                    resulting_confidence, operator_id, justification, created_at
                ) VALUES (
                    '60000000-0000-0000-0000-000000000006', '%s',
                    '50000000-0000-0000-0000-000000000006', 'CONFIRM',
                    1, 2, 'CONFIRMED', '%s', 1.0000,
                    'CONFIRMED', '%s', NULL,
                    'operator', 'Valid justification', '2026-09-01T10:00:45Z'
                )
                """.formatted(
                CONFIRMED_MAPPING_ID,
                CANONICAL_TEAM_ID,
                CANONICAL_TEAM_ID))).isInstanceOf(SQLException.class);

        insertReceipt(
                connection,
                "50000000-0000-0000-0000-000000000004",
                "invalid-operator-key",
                "c".repeat(64),
                "60000000-0000-0000-0000-000000000004");
        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO provider_mapping_decision (
                    id, provider_mapping_id, control_command_receipt_id, decision_type,
                    expected_version, resulting_version, previous_mapping_status,
                    resulting_mapping_status, operator_id, justification, created_at
                ) VALUES (
                    '60000000-0000-0000-0000-000000000004', '%s',
                    '50000000-0000-0000-0000-000000000004', 'REJECT',
                    1, 2, 'AMBIGUOUS', 'REJECTED',
                    ' operator ', 'Valid justification', '2026-09-01T10:01:00Z'
                )
                """.formatted(AMBIGUOUS_MAPPING_ID))).isInstanceOf(SQLException.class);

        insertReceipt(
                connection,
                "50000000-0000-0000-0000-000000000007",
                "invalid-justification-key",
                "1".repeat(64),
                "60000000-0000-0000-0000-000000000007");
        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO provider_mapping_decision (
                    id, provider_mapping_id, control_command_receipt_id, decision_type,
                    expected_version, resulting_version, previous_mapping_status,
                    resulting_mapping_status, operator_id, justification, created_at
                ) VALUES (
                    '60000000-0000-0000-0000-000000000007', '%s',
                    '50000000-0000-0000-0000-000000000007', 'REJECT',
                    1, 2, 'AMBIGUOUS', 'REJECTED',
                    'operator', '', '2026-09-01T10:01:30Z'
                )
                """.formatted(AMBIGUOUS_MAPPING_ID))).isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO normalization_anomaly_event (
                    id, normalization_anomaly_id, event_type, previous_status,
                    resulting_status, details, created_at
                ) VALUES (
                    '70000000-0000-0000-0000-000000000001', '%s', 'REOPENED',
                    'OPEN', 'OPEN', 'Invalid transition', '2026-09-01T10:02:00Z'
                )
                """.formatted(OPEN_ANOMALY_ID))).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO normalization_anomaly_event (
                    id, normalization_anomaly_id, event_type, previous_status,
                    resulting_status, details, created_at
                ) VALUES (
                    '70000000-0000-0000-0000-000000000002', '%s', 'OBSERVED',
                    NULL, 'OPEN', 'Missing previous status', '2026-09-01T10:03:00Z'
                )
                """.formatted(OPEN_ANOMALY_ID))).isInstanceOf(SQLException.class);
    }

    private void insertReceipt(
            Connection connection,
            String id,
            String idempotencyKey,
            String commandSha256,
            String resultResourceId) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO control_command_receipt (
                    id, idempotency_key, command_type, command_sha256,
                    result_resource_id, created_at
                ) VALUES (
                    '%s', '%s', 'MAPPING_CONFIRM', '%s', '%s', '2026-09-01T09:00:00Z'
                )
                """.formatted(id, idempotencyKey, commandSha256, resultResourceId));
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

    private String singleString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private boolean singleBoolean(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getBoolean(1);
        }
    }

    private Instant singleInstant(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getObject(1, OffsetDateTime.class).toInstant();
        }
    }
}
