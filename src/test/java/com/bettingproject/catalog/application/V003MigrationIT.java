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
class V003MigrationIT {

    private static final String COMPETITION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String HOME_TEAM_ID = "20000000-0000-0000-0000-000000000001";
    private static final String AWAY_TEAM_ID = "20000000-0000-0000-0000-000000000002";
    private static final String SEASON_ID = "30000000-0000-0000-0000-000000000001";
    private static final String FIXTURE_ID = "40000000-0000-0000-0000-000000000001";
    private static final String NORMALIZED_SNAPSHOT_ID = "50000000-0000-0000-0000-000000000001";
    private static final String BLOCKED_SNAPSHOT_ID = "50000000-0000-0000-0000-000000000002";
    private static final String NORMALIZED_OBSERVATION_ID = "60000000-0000-0000-0000-000000000001";
    private static final String BLOCKED_OBSERVATION_ID = "60000000-0000-0000-0000-000000000002";

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migratesPopulatedV002WithoutInventingAuthorityOrApplicationHistory() throws Exception {
        migrateToV002();

        try (Connection connection = connection()) {
            seedPopulatedV002(connection);
            assertV002Baseline(connection);
        }

        migrateToLatest();

        try (Connection connection = connection()) {
            assertExistingRowsAndFactsArePreserved(connection);
            assertCompatibilityBackfills(connection);
            assertNewSchemaObjectsExist(connection);
            assertNewConstraintsRejectInconsistentRows(connection);
        }
    }

    private void migrateToV002() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("002"))
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

    private void seedPopulatedV002(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO canonical_competition (
                        id, canonical_name, country_code, competition_type, created_at, updated_at
                    ) VALUES (
                        '%s', 'Liga Portugal', 'PRT', 'LEAGUE',
                        '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z'
                    )
                    """.formatted(COMPETITION_ID));
            statement.executeUpdate("""
                    INSERT INTO canonical_team (
                        id, canonical_name, country_code, created_at, updated_at
                    ) VALUES
                        ('%s', 'Home FC', 'PRT', '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z'),
                        ('%s', 'Away FC', 'PRT', '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z')
                    """.formatted(HOME_TEAM_ID, AWAY_TEAM_ID));
            statement.executeUpdate("""
                    INSERT INTO canonical_season (
                        id, competition_id, season_label, starts_on, ends_on, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '2026/2027', '2026-08-01', '2027-05-31',
                        '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z'
                    )
                    """.formatted(SEASON_ID, COMPETITION_ID));
            statement.executeUpdate("""
                    INSERT INTO canonical_fixture (
                        id, competition_id, home_team_id, away_team_id, kickoff_at, status,
                        created_at, updated_at, season_id, phase
                    ) VALUES (
                        '%s', '%s', '%s', '%s', '2026-09-05T19:00:00Z', 'SCHEDULED',
                        '2026-08-10T10:00:00Z', '2026-08-10T10:00:00Z', '%s', 'REGULAR_SEASON'
                    )
                    """.formatted(FIXTURE_ID, COMPETITION_ID, HOME_TEAM_ID, AWAY_TEAM_ID, SEASON_ID));
            statement.executeUpdate("""
                    INSERT INTO raw_snapshot (
                        id, provider, endpoint, received_at, payload_sha256, payload_compression,
                        payload, connector_version
                    ) VALUES
                        ('%s', 'highlightly', 'calendar/ppl', '2026-08-10T10:00:01Z',
                         '%s', 'NONE', decode('7b7d', 'hex'), 'fixture-parser-2'),
                        ('%s', 'highlightly', 'calendar/ppl', '2026-08-10T10:05:01Z',
                         '%s', 'NONE', decode('7b7d', 'hex'), 'fixture-parser-2')
                    """.formatted(
                    NORMALIZED_SNAPSHOT_ID,
                    "a".repeat(64),
                    BLOCKED_SNAPSHOT_ID,
                    "b".repeat(64)));
            statement.executeUpdate("""
                    INSERT INTO fixture_observation (
                        id, raw_snapshot_id, canonical_fixture_id, provider, provider_fixture_id,
                        provider_competition_id, provider_home_team_id, provider_away_team_id,
                        source_kickoff_at, source_status, source_phase, normalization_status,
                        reason_code, observed_at, created_at
                    ) VALUES
                        ('%s', '%s', '%s', 'highlightly', 'hly-fixture-1',
                         'hly-ppl', 'hly-home', 'hly-away', '2026-09-05T19:00:00Z',
                         'SCHEDULED', 'REGULAR_SEASON', 'NORMALIZED', NULL,
                         '2026-08-10T09:59:00Z', '2026-08-10T10:00:02Z'),
                        ('%s', '%s', NULL, 'highlightly', 'hly-fixture-blocked',
                         'hly-ppl', 'hly-unknown-home', 'hly-away', '2026-09-06T19:00:00Z',
                         'SCHEDULED', 'REGULAR_SEASON', 'BLOCKED', 'MISSING_MAPPING',
                         '2026-08-10T10:04:00Z', '2026-08-10T10:05:02Z')
                    """.formatted(
                    NORMALIZED_OBSERVATION_ID,
                    NORMALIZED_SNAPSHOT_ID,
                    FIXTURE_ID,
                    BLOCKED_OBSERVATION_ID,
                    BLOCKED_SNAPSHOT_ID));
            statement.executeUpdate("""
                    INSERT INTO normalization_anomaly (
                        id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                        anomaly_code, details, status, created_at
                    ) VALUES (
                        '70000000-0000-0000-0000-000000000001', '%s', 'highlightly', 'TEAM',
                        'hly-unknown-home', 'MISSING_MAPPING', 'Synthetic V002 migration fixture',
                        'OPEN', '2026-08-10T10:05:02Z'
                    )
                    """.formatted(BLOCKED_SNAPSHOT_ID));
        }
    }

    private void assertV002Baseline(Connection connection) throws SQLException {
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM canonical_fixture")).isEqualTo(1);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM raw_snapshot")).isEqualTo(2);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM fixture_observation")).isEqualTo(2);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM normalization_anomaly")).isEqualTo(1);
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '002' AND success
                """)).isEqualTo(1);
    }

    private void assertExistingRowsAndFactsArePreserved(Connection connection) throws SQLException {
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM canonical_fixture")).isEqualTo(1);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM raw_snapshot")).isEqualTo(2);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM fixture_observation")).isEqualTo(2);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM normalization_anomaly")).isEqualTo(1);
        assertThat(singleString(connection, "SELECT id::text FROM canonical_fixture"))
                .isEqualTo(FIXTURE_ID);
        assertThat(singleString(connection, "SELECT status FROM canonical_fixture"))
                .isEqualTo("SCHEDULED");
        assertThat(singleString(connection, "SELECT phase FROM canonical_fixture"))
                .isEqualTo("REGULAR_SEASON");
        assertThat(singleString(connection, "SELECT competition_id::text FROM canonical_fixture"))
                .isEqualTo(COMPETITION_ID);
        assertThat(singleString(connection, "SELECT season_id::text FROM canonical_fixture"))
                .isEqualTo(SEASON_ID);
        assertThat(singleString(connection, "SELECT home_team_id::text FROM canonical_fixture"))
                .isEqualTo(HOME_TEAM_ID);
        assertThat(singleString(connection, "SELECT away_team_id::text FROM canonical_fixture"))
                .isEqualTo(AWAY_TEAM_ID);
        assertThat(singleBoolean(connection, """
                SELECT kickoff_at = TIMESTAMPTZ '2026-09-05T19:00:00Z'
                FROM canonical_fixture
                """)).isTrue();
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM raw_snapshot
                WHERE id IN ('%s', '%s')
                """.formatted(NORMALIZED_SNAPSHOT_ID, BLOCKED_SNAPSHOT_ID)))
                .isEqualTo(2);
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM fixture_observation
                WHERE id IN ('%s', '%s')
                """.formatted(NORMALIZED_OBSERVATION_ID, BLOCKED_OBSERVATION_ID)))
                .isEqualTo(2);
        assertThat(singleString(connection, "SELECT anomaly_code FROM normalization_anomaly"))
                .isEqualTo("MISSING_MAPPING");
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '003' AND success
                """)).isEqualTo(1);
    }

    private void assertCompatibilityBackfills(Connection connection) throws SQLException {
        assertThat(singleString(connection, """
                SELECT source_schema_version FROM fixture_observation
                WHERE id = '%s'
                """.formatted(NORMALIZED_OBSERVATION_ID)))
                .isEqualTo("cal01-fixture-v2");
        assertThat(singleString(connection, """
                SELECT source_season FROM fixture_observation
                WHERE id = '%s'
                """.formatted(NORMALIZED_OBSERVATION_ID)))
                .isEqualTo("2026/2027");
        assertThat(singleBoolean(connection, """
                SELECT source_participants_unordered FROM fixture_observation
                WHERE id = '%s'
                """.formatted(NORMALIZED_OBSERVATION_ID)))
                .isFalse();
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM fixture_observation
                WHERE source_neutral_venue IS NULL
                """)).isEqualTo(2);
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM fixture_observation
                WHERE id = '%s' AND source_season IS NULL
                """.formatted(BLOCKED_OBSERVATION_ID)))
                .isEqualTo(1);
        assertThat(singleBoolean(connection, "SELECT participants_unordered FROM canonical_fixture"))
                .isFalse();
        assertThat(singleLong(connection, """
                SELECT COUNT(*) FROM canonical_fixture
                WHERE neutral_venue IS NULL
                  AND last_authority_observation_id IS NULL
                  AND last_authority_observed_at IS NULL
                  AND last_authority_provider IS NULL
                  AND last_authority_policy_version IS NULL
                """)).isEqualTo(1);
        assertThat(singleLong(connection, "SELECT COUNT(*) FROM fixture_application_log")).isZero();
    }

    private void assertNewSchemaObjectsExist(Connection connection) throws SQLException {
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND (
                    (table_name = 'canonical_fixture' AND column_name IN (
                        'neutral_venue', 'participants_unordered', 'last_authority_observation_id',
                        'last_authority_observed_at', 'last_authority_provider',
                        'last_authority_policy_version'
                    ))
                    OR
                    (table_name = 'fixture_observation' AND column_name IN (
                        'source_schema_version', 'source_season', 'source_neutral_venue',
                        'source_participants_unordered'
                    ))
                  )
                """)).isEqualTo(10);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                    'ix_fixture_observation_provider_time',
                    'ix_fixture_application_observation',
                    'ix_fixture_application_fixture'
                  )
                """)).isEqualTo(3);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname IN (
                    'ck_canonical_fixture_authority_stamp',
                    'ck_fixture_observation_schema_version',
                    'ck_fixture_application_outcome',
                    'ck_fixture_application_reason',
                    'ck_fixture_application_authority_role',
                    'ck_fixture_application_policy_context',
                    'ck_fixture_application_canonical_fixture'
                  )
                """)).isEqualTo(7);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'canonical_fixture'
                  AND column_name = 'participants_unordered'
                  AND is_nullable = 'NO'
                  AND column_default IS NULL
                """)).isEqualTo(1);
        assertThat(singleLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'fixture_observation'
                  AND column_name IN ('source_schema_version', 'source_participants_unordered')
                  AND is_nullable = 'NO'
                  AND column_default IS NULL
                """)).isEqualTo(2);
    }

    private void assertNewConstraintsRejectInconsistentRows(Connection connection) {
        assertThatThrownBy(() -> executeUpdate(connection, """
                UPDATE canonical_fixture
                SET last_authority_observation_id = '%s'
                WHERE id = '%s'
                """.formatted(NORMALIZED_OBSERVATION_ID, FIXTURE_ID)))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO fixture_application_log (
                    id, fixture_observation_id, canonical_fixture_id, outcome, evaluated_at
                ) VALUES (
                    '80000000-0000-0000-0000-000000000001', '%s', '%s',
                    'UNKNOWN_OUTCOME', '2026-08-10T10:10:00Z'
                )
                """.formatted(NORMALIZED_OBSERVATION_ID, FIXTURE_ID)))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO fixture_application_log (
                    id, fixture_observation_id, canonical_fixture_id, outcome,
                    authority_role, evaluated_at
                ) VALUES (
                    '80000000-0000-0000-0000-000000000002', '%s', '%s', 'CREATED',
                    'PRIMARY', '2026-08-10T10:10:00Z'
                )
                """.formatted(NORMALIZED_OBSERVATION_ID, FIXTURE_ID)))
                .isInstanceOf(SQLException.class);
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
}
