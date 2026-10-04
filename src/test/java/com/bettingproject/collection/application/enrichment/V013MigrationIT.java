package com.bettingproject.collection.application.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class V013MigrationIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String HASH = "a".repeat(64);
    private static final UUID SCOPE = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID WINDOW = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID PLAN = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID ADMISSION = UUID.fromString("74000000-0000-0000-0000-000000000001");
    private static final UUID STEP = UUID.fromString("75000000-0000-0000-0000-000000000001");
    private static final UUID FIXTURE = UUID.fromString("76000000-0000-0000-0000-000000000001");

    @Test
    void installsFreshAndUpgradesPopulatedV012WithoutChangingExistingRows() throws Exception {
        migrate("enr013_fresh", null);
        try (Connection connection = connection("enr013_fresh")) {
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_collection_attempt")).isEqualTo("0");
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_collection_derivation")).isEqualTo("0");
            assertThat(value(connection, "SELECT count(*)::text FROM flyway_schema_history WHERE version='013' AND success")).isEqualTo("1");
        }

        migrate("enr013_upgrade", "012");
        try (Connection connection = connection("enr013_upgrade")) {
            seedV012(connection);
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_plan_step WHERE id='" + STEP + "'")).isEqualTo("1");
        }
        migrate("enr013_upgrade", null);
        try (Connection connection = connection("enr013_upgrade")) {
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_daily_admission WHERE id='" + ADMISSION + "'")).isEqualTo("1");
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_plan_step WHERE id='" + STEP + "'")).isEqualTo("1");
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_collection_attempt")).isEqualTo("0");
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_collection_derivation")).isEqualTo("0");
            insertReservedAttempt(connection);
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_collection_attempt WHERE id='78000000-0000-0000-0000-000000000001' AND state='RESERVED' AND raw_snapshot_id IS NULL")).isEqualTo("1");
            assertThatThrownBy(() -> execute(connection, "UPDATE enrichment_collection_attempt SET state='COMMITTED_FOR_SEND', reason_code='bad' WHERE id='78000000-0000-0000-0000-000000000001'"))
                    .isInstanceOf(SQLException.class);
            assertThat(value(connection, "SELECT count(*)::text FROM persistent_job WHERE job_key='historical-enr013' AND NOT managed_collection")).isEqualTo("1");
        }
    }

    private static void seedV012(Connection connection) throws Exception {
        execute(connection, """
                INSERT INTO provider_budget_scope(id,provider,account_ref,created_at)
                VALUES ('%s','highlightly','synthetic-enr013','2031-08-10T08:00Z');
                INSERT INTO provider_budget_window(id,scope_id,starts_at,ends_at,capacity,project_limit,reserve,
                    shared_initial,project_initial,cadence_limit,cadence_period_ms,state,quota_inconsistent,version,
                    created_at,updated_at,proof_logical_id,proof_sha256)
                VALUES ('%s','%s','2031-08-10T08:00Z','2031-08-11T08:00Z',NULL,80,0,0,0,30,3600000,
                    'ACTIVE',false,1,'2031-08-10T08:00Z','2031-08-10T08:00Z','synthetic-window','%s');
                INSERT INTO canonical_competition(id,canonical_name,country_code,competition_type,created_at,updated_at)
                VALUES ('77000000-0000-0000-0000-000000000001','ENR013 League','XX','LEAGUE','2031-08-10T08:00Z','2031-08-10T08:00Z');
                INSERT INTO canonical_team(id,canonical_name,country_code,created_at,updated_at)
                VALUES ('77000000-0000-0000-0000-000000000002','ENR013 Home','XX','2031-08-10T08:00Z','2031-08-10T08:00Z'),
                       ('77000000-0000-0000-0000-000000000003','ENR013 Away','XX','2031-08-10T08:00Z','2031-08-10T08:00Z');
                INSERT INTO canonical_season(id,competition_id,season_label,created_at,updated_at)
                VALUES ('77000000-0000-0000-0000-000000000004','77000000-0000-0000-0000-000000000001','2031/2032','2031-08-10T08:00Z','2031-08-10T08:00Z');
                INSERT INTO canonical_fixture(id,competition_id,home_team_id,away_team_id,kickoff_at,status,created_at,updated_at,season_id,phase,neutral_venue,participants_unordered)
                VALUES ('%s','77000000-0000-0000-0000-000000000001','77000000-0000-0000-0000-000000000002','77000000-0000-0000-0000-000000000003','2031-08-10T15:00Z','SCHEDULED','2031-08-10T08:00Z','2031-08-10T08:00Z','77000000-0000-0000-0000-000000000004','LEAGUE',NULL,false);
                INSERT INTO enrichment_daily_plan(id,idempotency_key,command_sha256,budget_window_id,competition_date,registry_sha256,
                    estimated_calls_per_fixture,selected_fixture_count,estimated_calls,evaluated_at,created_at)
                VALUES ('%s','synthetic-enr013-plan','%s','%s','2031-08-10','%s',1,1,1,'2031-08-10T08:00Z','2031-08-10T08:00Z');
                INSERT INTO enrichment_daily_admission(id,plan_id,canonical_fixture_id,admission_order,priority,kickoff_at,estimated_calls,created_at)
                VALUES ('%s','%s','%s',1,false,'2031-08-10T15:00Z',1,'2031-08-10T08:00Z');
                INSERT INTO enrichment_plan_step(id,admission_id,step_code,family_scope,scheduled_at,condition_code,status,created_at)
                VALUES ('%s','%s','DETAIL_AT_KICKOFF','DETAIL','2031-08-10T15:00Z','SCHEDULED_KICKOFF_WINDOW','PLANNED','2031-08-10T08:00Z');
                INSERT INTO persistent_job(id,job_key,job_type,status,attempts,created_at,updated_at)
                VALUES ('77000000-0000-0000-0000-000000000005','historical-enr013','CALENDAR_DISCOVERY','PENDING',0,'2031-08-10T08:00Z','2031-08-10T08:00Z');
                """.formatted(SCOPE, WINDOW, SCOPE, HASH, FIXTURE, PLAN, HASH, WINDOW, HASH, ADMISSION, PLAN,
                FIXTURE, STEP, ADMISSION));
    }

    private static void insertReservedAttempt(Connection connection) throws Exception {
        UUID intent = UUID.fromString("78000000-0000-0000-0000-000000000002");
        UUID audit = UUID.fromString("78000000-0000-0000-0000-000000000003");
        execute(connection, """
                INSERT INTO provider_call_intent(id,window_id,idempotency_key,logical_endpoint,request_sha256,state,version,created_at,updated_at)
                VALUES ('%s','%s','enr013-reserved-intent','enrichment/matches','%s','RESERVED',1,'2031-08-10T09:00Z','2031-08-10T09:00Z');
                INSERT INTO provider_call_audit(id,provider,logical_endpoint,requested_at,connector_version,created_at)
                VALUES ('%s','highlightly','enrichment/matches','2031-08-10T09:00Z','highlightly-enrichment-http-v1','2031-08-10T09:00Z');
                INSERT INTO enrichment_collection_attempt(id,admission_id,step_id,step_code,canonical_fixture_id,budget_window_id,
                    budget_intent_id,audit_id,provider,provider_competition_id,source_season,source_phase,data_type,
                    logical_competition,logical_season,logical_phase,provider_fixture_id,family,logical_endpoint,
                    connector_version,parser_version,request_sha256,state,kickoff_at,requested_at,created_at,updated_at)
                VALUES ('78000000-0000-0000-0000-000000000001','%s','%s','DETAIL_AT_KICKOFF','%s','%s','%s','%s',
                    'highlightly','80778','2031','regular','MATCH_DETAIL','PPL','2031/2032','LEAGUE','12345','MATCH_DETAIL',
                    'enrichment/matches','highlightly-enrichment-http-v1','highlightly-match-detail-v1','%s','RESERVED',
                    '2031-08-10T15:00Z','2031-08-10T09:00Z','2031-08-10T09:00Z','2031-08-10T09:00Z');
                """.formatted(intent, WINDOW, HASH, audit, ADMISSION, STEP, FIXTURE, WINDOW, intent, audit, HASH));
    }

    private static void migrate(String schema, String target) {
        var config = Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
                .schemas(schema).defaultSchema(schema);
        if (target != null) { config.target(MigrationVersion.fromVersion(target)); }
        config.load().migrate();
    }
    private static Connection connection(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        connection.setSchema(schema);
        return connection;
    }
    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static String value(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue(); return rows.getString(1);
        }
    }
}
