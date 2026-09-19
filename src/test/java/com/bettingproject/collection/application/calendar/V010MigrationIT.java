package com.bettingproject.collection.application.calendar;

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
class V010MigrationIT {
    private static final String HASH = "a".repeat(64);
    private static final List<String> TABLES = List.of(
            "calendar_collection", "calendar_collection_page", "calendar_collection_derivation");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void installsFreshWithoutCallsCollectionsOrProviderActivation() throws Exception {
        migrate("calendar_fresh", null);
        try (Connection connection = connection("calendar_fresh")) {
            assertEmpty(connection);
            assertThat(number(connection, "SELECT COUNT(*) FROM provider_budget_window")).isZero();
            assertThat(number(connection, "SELECT COUNT(*) FROM provider_call_audit")).isZero();
            assertThat(number(connection, "SELECT COUNT(*) FROM outbox_message")).isZero();
            assertThat(number(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '010' AND success"))
                    .isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema()
                    AND indexname IN ('ix_calendar_collection_window', 'ix_calendar_page_collection',
                                      'ix_calendar_derivation_page')
                    """)).isEqualTo(3);
        }
    }

    @Test
    void upgradesPopulatedV009KeepingBudgetRawAuditOutboxAndCatalogUnchanged() throws Exception {
        migrate("calendar_upgrade", "009");
        try (Connection connection = connection("calendar_upgrade")) {
            seedV009(connection);
        }
        migrate("calendar_upgrade", null);
        try (Connection connection = connection("calendar_upgrade")) {
            assertEmpty(connection);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM provider_budget_scope
                    WHERE id = '10000000-0000-0000-0000-000000000001'
                    AND provider = 'synthetic' AND account_ref = 'synthetic-account'
                    AND created_at = '2026-09-18T08:00:00Z'
                    """)).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM provider_budget_window
                    WHERE id = '20000000-0000-0000-0000-000000000001'
                    AND scope_id = '10000000-0000-0000-0000-000000000001'
                    AND capacity = 100 AND project_limit = 80 AND reserve = 20
                    AND shared_initial = 4 AND project_initial = 2
                    AND state = 'ACTIVE' AND version = 3 AND NOT quota_inconsistent
                    AND starts_at = '2026-09-18T08:00:00Z' AND ends_at = '2026-09-19T08:00:00Z'
                    AND created_at = '2026-09-18T08:00:00Z' AND updated_at = '2026-09-18T08:00:02Z'
                    """)).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM provider_call_intent
                    WHERE id = '30000000-0000-0000-0000-000000000001'
                    AND window_id = '20000000-0000-0000-0000-000000000001'
                    AND state = 'COMMITTED_FOR_SEND' AND version = 2
                    AND idempotency_key = 'migration-calendar-intent' AND logical_endpoint = 'calendar/matches'
                    AND committed_at = '2026-09-18T08:00:01Z'
                    AND result_at IS NULL AND http_status IS NULL
                    """)).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM raw_snapshot WHERE id = '40000000-0000-0000-0000-000000000001'
                    AND provider = 'synthetic' AND endpoint = 'synthetic-history'
                    AND payload = decode('7b7d', 'hex') AND payload_compression = 'identity'
                    AND received_at = '2026-09-18T08:00:02Z' AND connector_version = 'synthetic-v1'
                    AND payload_sha256 = '%s'
                    """.formatted(HASH))).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM provider_call_audit WHERE id = '50000000-0000-0000-0000-000000000001'
                    AND provider = 'synthetic' AND logical_endpoint = 'synthetic-history'
                    AND requested_at = '2026-09-18T08:00:01Z' AND received_at = '2026-09-18T08:00:02Z'
                    AND http_status = 200 AND latency_ms = 1000 AND quota_remaining = 95
                    AND payload_sha256 = '%s' AND connector_version = 'synthetic-v1'
                    """.formatted(HASH))).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM outbox_message WHERE id = '60000000-0000-0000-0000-000000000001'
                    AND idempotency_key = 'synthetic-history' AND payload_json = '{"synthetic":true}'::jsonb
                    AND status = 'DELIVERED' AND attempt_count = 1
                    AND delivered_at = '2026-09-18T08:00:02Z'
                    """)).isEqualTo(1);
            assertThat(number(connection, """
                    SELECT COUNT(*) FROM canonical_team WHERE id = '70000000-0000-0000-0000-000000000001'
                    AND canonical_name = 'Synthetic prior team' AND country_code = 'FRA'
                    AND created_at = '2026-09-18T08:00:00Z' AND updated_at = '2026-09-18T08:00:01Z'
                    """)).isEqualTo(1);
        }
    }

    @Test
    void enforcesStatesDatesHashesPaginationAndSameWindowProvenance() throws Exception {
        migrate("calendar_constraints", null);
        try (Connection connection = connection("calendar_constraints")) {
            seedV009(connection);
            execute(connection, """
                    INSERT INTO calendar_collection (
                        id, window_id, provider, provider_competition_id, source_season, source_phase,
                        data_type, collection_date, season_start_year, command_sha256, registry_sha256,
                        status, created_at, updated_at
                    ) VALUES ('80000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                        'synthetic', 'competition', '2026/2027', 'LEAGUE', 'CALENDAR', '2026-09-18', 2026,
                        '%s', '%s', 'RUNNING', '2026-09-18T08:00:01Z', '2026-09-18T08:00:01Z');
                    INSERT INTO calendar_collection_page (
                        id, collection_id, window_id, intent_id, audit_id, page_number, page_offset,
                        page_limit, connector_version, requested_at, response_code
                    ) VALUES ('90000000-0000-0000-0000-000000000001', '80000000-0000-0000-0000-000000000001',
                        '20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001',
                        '50000000-0000-0000-0000-000000000001', 1, 0, 100, 'synthetic-v1',
                        '2026-09-18T08:00:01Z', 'PENDING')
                    """.formatted(HASH, HASH));
            for (String assignment : List.of("status = 'OTHER'", "status = 'INCOMPLETE'",
                    "data_type = 'EVENTS'", "season_start_year = -1", "command_sha256 = 'bad'",
                    "registry_sha256 = 'bad'", "source_season = ''", "source_phase = ''",
                    "updated_at = '2026-09-17T00:00:00Z'")) {
                assertThatThrownBy(() -> execute(connection, "UPDATE calendar_collection SET " + assignment))
                        .as(assignment).isInstanceOf(SQLException.class);
            }
            for (String assignment : List.of("page_number = 0", "page_number = 101", "page_offset = -1",
                    "page_limit = 0", "response_code = 'OTHER'", "response_code = 'RECEIVED'",
                    "quota_remaining = -1", "http_status = 999", "raw_sha256 = 'bad'",
                    "received_at = '2026-09-17T00:00:00Z'", "window_id = '20000000-0000-0000-0000-000000000002'")) {
                assertThatThrownBy(() -> execute(connection, "UPDATE calendar_collection_page SET " + assignment))
                        .as(assignment).isInstanceOf(SQLException.class);
            }
            execute(connection, """
                    UPDATE calendar_collection_page SET response_code = 'RECEIVED',
                        received_at = '2026-09-18T08:00:02Z', http_status = 200,
                        raw_snapshot_id = '40000000-0000-0000-0000-000000000001', raw_sha256 = '%s';
                    INSERT INTO calendar_collection_derivation (
                        id, page_id, parser_version, outcome, raw_sha256, evaluated_at
                    ) VALUES ('a0000000-0000-0000-0000-000000000001', '90000000-0000-0000-0000-000000000001',
                        'synthetic-v1', 'INCOMPATIBLE', '%s', '2026-09-18T08:00:02Z')
                    """.formatted(HASH, HASH));
            for (String assignment : List.of("outcome = 'UNKNOWN'", "outcome = 'APPLIED'", "raw_sha256 = 'bad'",
                    "parser_version = ''", "page_id = '90000000-0000-0000-0000-000000000002'")) {
                assertThatThrownBy(() -> execute(connection, "UPDATE calendar_collection_derivation SET " + assignment))
                        .as(assignment).isInstanceOf(SQLException.class);
            }
            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO calendar_collection_page
                    SELECT '90000000-0000-0000-0000-000000000002', collection_id, window_id, intent_id,
                           audit_id, page_number, page_offset, page_limit, connector_version, raw_snapshot_id,
                           raw_sha256, requested_at, received_at, http_status, quota_remaining, response_code
                    FROM calendar_collection_page
                    """)).isInstanceOf(SQLException.class);
            assertThat(number(connection, "SELECT COUNT(*) FROM calendar_collection_page")).isEqualTo(1);
            assertThat(number(connection, "SELECT COUNT(*) FROM calendar_collection_derivation")).isEqualTo(1);
        }
    }

    private void seedV009(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO provider_budget_scope (id, provider, account_ref, created_at)
                VALUES ('10000000-0000-0000-0000-000000000001', 'synthetic', 'synthetic-account', '2026-09-18T08:00:00Z');
                INSERT INTO provider_budget_window (
                    id, scope_id, starts_at, ends_at, capacity, project_limit, reserve, shared_initial,
                    project_initial, state, quota_inconsistent, version, created_at, updated_at,
                    proof_logical_id, proof_sha256
                ) VALUES ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
                    '2026-09-18T08:00:00Z', '2026-09-19T08:00:00Z', 100, 80, 20, 4, 2, 'ACTIVE', false,
                    3, '2026-09-18T08:00:00Z', '2026-09-18T08:00:02Z', 'synthetic-migration-proof', '%s');
                INSERT INTO provider_call_intent (
                    id, window_id, idempotency_key, logical_endpoint, request_sha256, state,
                    version, created_at, updated_at, committed_at
                ) VALUES ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                    'migration-calendar-intent', 'calendar/matches', '%s', 'COMMITTED_FOR_SEND', 2,
                    '2026-09-18T08:00:00Z', '2026-09-18T08:00:01Z', '2026-09-18T08:00:01Z');
                INSERT INTO raw_snapshot (
                    id, provider, endpoint, received_at, payload_sha256, payload_compression, payload, connector_version
                ) VALUES ('40000000-0000-0000-0000-000000000001', 'synthetic', 'synthetic-history',
                    '2026-09-18T08:00:02Z', '%s', 'identity', decode('7b7d','hex'), 'synthetic-v1');
                INSERT INTO provider_call_audit (
                    id, provider, logical_endpoint, requested_at, received_at, http_status, latency_ms,
                    quota_remaining, payload_sha256, connector_version, created_at
                ) VALUES ('50000000-0000-0000-0000-000000000001', 'synthetic', 'synthetic-history',
                    '2026-09-18T08:00:01Z', '2026-09-18T08:00:02Z', 200, 1000, 95,
                    '%s', 'synthetic-v1', '2026-09-18T08:00:02Z');
                INSERT INTO outbox_message (
                    id, idempotency_key, aggregate_type, aggregate_id, destination, payload_json,
                    status, attempt_count, delivered_at, created_at, updated_at
                ) VALUES ('60000000-0000-0000-0000-000000000001', 'synthetic-history', 'synthetic',
                    '50000000-0000-0000-0000-000000000001', 'synthetic', '{"synthetic":true}', 'DELIVERED',
                    1, '2026-09-18T08:00:02Z', '2026-09-18T08:00:01Z', '2026-09-18T08:00:02Z');
                INSERT INTO canonical_team (id, canonical_name, country_code, created_at, updated_at)
                VALUES ('70000000-0000-0000-0000-000000000001', 'Synthetic prior team', 'FRA',
                        '2026-09-18T08:00:00Z', '2026-09-18T08:00:01Z')
                """.formatted(HASH, HASH, HASH, HASH));
    }

    private void assertEmpty(Connection connection) throws SQLException {
        for (String table : TABLES) {
            assertThat(number(connection, "SELECT COUNT(*) FROM " + table)).as(table).isZero();
        }
    }

    private void migrate(String schema, String target) {
        var config = Flyway.configure().dataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema);
        if (target != null) {
            config.target(MigrationVersion.fromVersion(target));
        }
        config.load().migrate();
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
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }
}
