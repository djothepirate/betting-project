package com.bettingproject.collection.application.budget;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V009MigrationIT {

    private static final List<String> BUDGET_TABLES = List.of(
            "provider_budget_scope", "provider_budget_window", "provider_call_intent",
            "provider_quota_observation", "provider_quota_observation_intent",
            "provider_budget_incident", "provider_budget_event");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void installsFreshWithoutActivatingAnyProviderOrInventingHistory() throws Exception {
        migrate("fresh_budget", null);
        try (Connection connection = connection("fresh_budget")) {
            assertEmptyBudget(connection);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM flyway_schema_history
                    WHERE version = '009' AND success
                    """)).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = current_schema() AND indexname IN (
                        'uq_provider_budget_window_open', 'ix_provider_call_intent_window_state',
                        'ix_provider_call_intent_scope_cadence', 'ix_provider_quota_observation_time',
                        'ix_provider_budget_incident_window', 'ix_provider_budget_event_window'
                    )
                    """)).isEqualTo(6);
        }
    }

    @Test
    void upgradesPopulatedV008WithoutChangingExistingFactsOrBackfillingCalls() throws Exception {
        migrate("upgrade_budget", "008");
        try (Connection connection = connection("upgrade_budget")) {
            execute(connection, """
                    INSERT INTO canonical_team (
                        id, canonical_name, country_code, created_at, updated_at
                    ) VALUES (
                        '10000000-0000-0000-0000-000000000001', 'Synthetic existing team', 'FRA',
                        '2026-09-01T09:00:00Z', '2026-09-02T10:00:00Z'
                    );
                    INSERT INTO provider_mapping (
                        id, provider, entity_type, provider_entity_id, canonical_entity_id,
                        season, phase, confidence, mapping_status, created_at, updated_at, version
                    ) VALUES (
                        '20000000-0000-0000-0000-000000000001', 'synthetic', 'TEAM', 'existing-team',
                        '10000000-0000-0000-0000-000000000001', '2026/2027', 'LEAGUE', 1,
                        'CONFIRMED', '2026-09-01T09:00:00Z', '2026-09-02T10:00:00Z', 3
                    );
                    INSERT INTO provider_call_audit (
                        id, provider, logical_endpoint, requested_at, received_at,
                        http_status, latency_ms, quota_remaining, payload_sha256, connector_version,
                        created_at
                    ) VALUES (
                        '30000000-0000-0000-0000-000000000001', 'synthetic', 'calendar',
                        '2026-09-01T09:00:00Z', '2026-09-01T09:00:01Z', 200, 1000, 99,
                        '%s', 'synthetic-v1', '2026-09-01T09:00:01Z'
                    );
                    INSERT INTO outbox_message (
                        id, idempotency_key, aggregate_type, aggregate_id, destination,
                        payload_json, status, attempt_count, created_at, updated_at
                    ) VALUES (
                        '40000000-0000-0000-0000-000000000001', 'existing-before-v009',
                        'synthetic', '30000000-0000-0000-0000-000000000001', 'synthetic',
                        '{"existing":true}', 'PENDING', 0,
                        '2026-09-01T09:00:01Z', '2026-09-01T09:00:01Z'
                    )
                    """.formatted("a".repeat(64)));
        }

        migrate("upgrade_budget", null);
        try (Connection connection = connection("upgrade_budget")) {
            assertEmptyBudget(connection);
            assertThat(number(connection, "SELECT COUNT(*) FROM canonical_team")).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM canonical_team
                    WHERE id = '10000000-0000-0000-0000-000000000001'
                      AND canonical_name = 'Synthetic existing team' AND country_code = 'FRA'
                      AND created_at = '2026-09-01T09:00:00Z'
                      AND updated_at = '2026-09-02T10:00:00Z'
                    """)).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM provider_mapping
                    WHERE id = '20000000-0000-0000-0000-000000000001'
                      AND canonical_entity_id = '10000000-0000-0000-0000-000000000001'
                      AND mapping_status = 'CONFIRMED' AND confidence = 1 AND version = 3
                      AND season = '2026/2027' AND phase = 'LEAGUE'
                      AND created_at = '2026-09-01T09:00:00Z'
                      AND updated_at = '2026-09-02T10:00:00Z'
                    """)).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM provider_call_audit
                    WHERE id = '30000000-0000-0000-0000-000000000001'
                      AND http_status = 200 AND latency_ms = 1000 AND quota_remaining = 99
                      AND connector_version = 'synthetic-v1' AND payload_sha256 = '%s'
                      AND requested_at = '2026-09-01T09:00:00Z'
                      AND received_at = '2026-09-01T09:00:01Z'
                    """.formatted("a".repeat(64)))).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM outbox_message
                    WHERE id = '40000000-0000-0000-0000-000000000001'
                      AND aggregate_id = '30000000-0000-0000-0000-000000000001'
                      AND payload_json = '{"existing":true}'::jsonb
                      AND status = 'PENDING' AND attempt_count = 0
                    """)).isEqualTo(1);
        }
    }

    @Test
    void rejectsInvalidProjectionStatesAndCrossWindowProvenance() throws Exception {
        migrate("constraints_budget", null);
        try (Connection connection = connection("constraints_budget")) {
            execute(connection, """
                    INSERT INTO provider_budget_scope (id, provider, account_ref, created_at) VALUES
                        ('10000000-0000-0000-0000-000000000001', 'synthetic-one', 'account', '2026-09-13T08:00:00Z'),
                        ('10000000-0000-0000-0000-000000000002', 'synthetic-two', 'account', '2026-09-13T08:00:00Z');
                    INSERT INTO provider_budget_window (
                        id, scope_id, starts_at, ends_at, capacity, project_limit, reserve,
                        shared_initial, project_initial, state, quota_inconsistent, version,
                        created_at, updated_at, proof_logical_id, proof_sha256
                    ) VALUES
                        ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
                         '2026-09-13T08:00:00Z', '2026-09-14T08:00:00Z', 100, 80, 20, 0, 0,
                         'ACTIVE', false, 1, '2026-09-13T08:00:00Z', '2026-09-13T08:00:00Z', 'synthetic', '%s'),
                        ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
                         '2026-09-13T08:00:00Z', '2026-09-14T08:00:00Z', 100, 80, 20, 0, 0,
                         'ACTIVE', false, 1, '2026-09-13T08:00:00Z', '2026-09-13T08:00:00Z', 'synthetic', '%s');
                    INSERT INTO provider_call_intent (
                        id, window_id, idempotency_key, logical_endpoint, request_sha256, state,
                        version, created_at, updated_at, committed_at
                    ) VALUES (
                        '30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                        'migration-intent', 'calendar', '%s', 'COMMITTED_FOR_SEND', 2,
                        '2026-09-13T08:00:00Z', '2026-09-13T08:00:01Z', '2026-09-13T08:00:01Z'
                    );
                    INSERT INTO provider_quota_observation (
                        id, window_id, remaining, observed_at, valid_until, proof_logical_id,
                        proof_sha256, disposition, created_at
                    ) VALUES
                        ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                         99, '2026-09-13T08:01:00Z', '2026-09-14T08:00:00Z', 'synthetic', '%s',
                         'ACCEPTED', '2026-09-13T08:01:00Z'),
                        ('40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002',
                         100, '2026-09-13T08:01:00Z', '2026-09-14T08:00:00Z', 'synthetic', '%s',
                         'ACCEPTED', '2026-09-13T08:01:00Z');
                    INSERT INTO provider_quota_observation_intent (observation_id, window_id, intent_id)
                    VALUES ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                            '30000000-0000-0000-0000-000000000001');
                    UPDATE provider_budget_window
                    SET current_observation_id = '40000000-0000-0000-0000-000000000001'
                    WHERE id = '20000000-0000-0000-0000-000000000001'
                    """.formatted("a".repeat(64), "a".repeat(64), "b".repeat(64),
                    "c".repeat(64), "d".repeat(64)));

            for (String assignment : List.of("version = 0", "reserve = 101", "capacity = NULL",
                    "ends_at = starts_at", "cadence_limit = 1", "state = 'UNKNOWN'",
                    "proof_sha256 = 'bad'", "project_initial = 1")) {
                assertThatThrownBy(() -> execute(connection,
                        "UPDATE provider_budget_window SET " + assignment))
                        .as(assignment).isInstanceOf(SQLException.class);
            }
            for (String assignment : List.of("version = 0", "idempotency_key = 'non ascii é'",
                    "request_sha256 = 'bad'", "state = 'RESULT_RECORDED'", "committed_at = NULL",
                    "http_status = 200", "updated_at = '2026-09-12T00:00:00Z'",
                    "logical_endpoint = 'https://synthetic.invalid'", "logical_endpoint = 'calendar?query'",
                    "logical_endpoint = 'calendar#fragment'", "logical_endpoint = 'calendar&value'",
                    "logical_endpoint = 'calendar=value'", "logical_endpoint = '/absolute'",
                    "logical_endpoint = 'calendar' || chr(92) || 'matches'")) {
                assertThatThrownBy(() -> execute(connection,
                        "UPDATE provider_call_intent SET " + assignment))
                        .as(assignment).isInstanceOf(SQLException.class);
            }
            for (String assignment : List.of("remaining = -1", "valid_until = observed_at",
                    "proof_sha256 = 'bad'", "disposition = 'UNKNOWN'")) {
                assertThatThrownBy(() -> execute(connection,
                        "UPDATE provider_quota_observation SET " + assignment))
                        .as(assignment).isInstanceOf(SQLException.class);
            }
            assertThatThrownBy(() -> execute(connection, """
                    UPDATE provider_budget_window
                    SET current_observation_id = '40000000-0000-0000-0000-000000000002'
                    WHERE id = '20000000-0000-0000-0000-000000000001'
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO provider_budget_window (
                        id, scope_id, starts_at, ends_at, capacity, project_limit, reserve,
                        shared_initial, project_initial, state, quota_inconsistent, version,
                        created_at, updated_at, proof_logical_id, proof_sha256
                    ) SELECT '20000000-0000-0000-0000-000000000003', scope_id,
                             starts_at, ends_at, capacity, project_limit, reserve,
                             shared_initial, project_initial, 'SUSPENDED', false, 1,
                             created_at, updated_at, proof_logical_id, proof_sha256
                      FROM provider_budget_window
                      WHERE id = '20000000-0000-0000-0000-000000000001'
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO provider_quota_observation_intent (observation_id, window_id, intent_id)
                    VALUES ('40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002',
                            '30000000-0000-0000-0000-000000000001')
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO provider_budget_incident (id, window_id, intent_id, code, created_at)
                    VALUES ('50000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002',
                            '30000000-0000-0000-0000-000000000001', 'UNCERTAIN_SEND', '2026-09-13T08:00:02Z')
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO provider_budget_event (id, scope_id, window_id, type, created_at)
                    VALUES ('60000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002',
                            '20000000-0000-0000-0000-000000000001', 'RESERVED', '2026-09-13T08:00:02Z')
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO provider_budget_event (
                        id, scope_id, window_id, type, operator_id, justification, created_at
                    ) VALUES (
                        '60000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
                        '20000000-0000-0000-0000-000000000001', 'INITIALIZED', ' ', 'synthetic',
                        '2026-09-13T08:00:02Z'
                    )
                    """)).isInstanceOf(SQLException.class);
            assertThat(number(connection, "SELECT COUNT(*) FROM provider_call_intent")).isEqualTo(1);
            assertThat(number(connection, "SELECT COUNT(*) FROM provider_quota_observation_intent")).isEqualTo(1);
            assertThat(number(connection, "SELECT COUNT(*) FROM provider_budget_incident")).isZero();
            assertThat(number(connection, "SELECT COUNT(*) FROM provider_budget_event")).isZero();
        }
    }

    private void assertEmptyBudget(Connection connection) throws SQLException {
        for (String table : BUDGET_TABLES) {
            assertThat(number(connection, "SELECT COUNT(*) FROM " + table)).as(table).isZero();
        }
    }

    private void migrate(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema);
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        configuration.load().migrate();
    }

    private Connection connection(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private long number(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
