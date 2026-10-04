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
class V014MigrationIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String HASH = "a".repeat(64);
    private static final UUID WINDOW = UUID.fromString("82000000-0000-0000-0000-000000000001");
    private static final UUID FIXTURE = UUID.fromString("83000000-0000-0000-0000-000000000001");
    private static final UUID PLAN = UUID.fromString("84000000-0000-0000-0000-000000000001");
    private static final UUID ADMISSION = UUID.fromString("85000000-0000-0000-0000-000000000001");
    private static final UUID STEP = UUID.fromString("86000000-0000-0000-0000-000000000001");

    @Test
    void installsFreshAndUpgradesPopulatedV013WithNewDispatchRowsOnly() throws Exception {
        migrate("enr014_fresh", null);
        try (Connection connection = connection("enr014_fresh")) {
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_job_input")).isEqualTo("0");
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_job_input_route")).isEqualTo("0");
            assertThat(value(connection, "SELECT count(*)::text FROM flyway_schema_history WHERE version='014' AND success")).isEqualTo("1");
        }

        migrate("enr014_upgrade", "013");
        try (Connection connection = connection("enr014_upgrade")) { seedV013(connection); }
        migrate("enr014_upgrade", null);
        try (Connection connection = connection("enr014_upgrade")) {
            assertThat(value(connection, "SELECT count(*)::text FROM persistent_job WHERE job_key='historical-before-enr014' AND NOT managed_collection"))
                    .isEqualTo("1");
            assertThat(value(connection, "SELECT status FROM enrichment_plan_step WHERE id='" + STEP + "'"))
                    .isEqualTo("PLANNED");
            assertThat(value(connection, "SELECT result_code IS NULL AND trigger_policy_version IS NULL FROM enrichment_plan_step WHERE id='" + STEP + "'"))
                    .isEqualTo("t");

            UUID job = UUID.fromString("87000000-0000-0000-0000-000000000001");
            execute(connection, """
                    INSERT INTO persistent_job(id,job_key,job_type,status,attempts,next_run_at,created_at,updated_at,
                        managed_collection,command_sha256,scheduled_at,max_attempts)
                    VALUES ('%s','enrichment-step:%s','PREMATCH_ENRICHMENT','PENDING',0,'2031-08-10T14:55Z',
                        '2031-08-10T08:00Z','2031-08-10T08:00Z',true,'%s','2031-08-10T14:55Z',3);
                    INSERT INTO enrichment_job_input(job_id,job_type,admission_id,step_id,budget_window_id,
                        registry_sha256,route_sha256,created_at)
                    VALUES ('%s','PREMATCH_ENRICHMENT','%s','%s','%s','%s','%s','2031-08-10T08:00Z');
                    INSERT INTO enrichment_job_input_route(job_id,family,provider,provider_competition_id,
                        source_season,source_phase,data_type,parser_version)
                    VALUES ('%s','MATCH_DETAIL','highlightly','80778','2031','regular','MATCH_DETAIL','highlightly-match-detail-v1');
                    UPDATE enrichment_plan_step SET status='QUEUED' WHERE id='%s';
                    """.formatted(job, STEP, HASH, job, ADMISSION, STEP, WINDOW, HASH, HASH, job, STEP));
            assertThat(value(connection, "SELECT job_type FROM persistent_job WHERE id='" + job + "'"))
                    .isEqualTo("PREMATCH_ENRICHMENT");
            assertThat(value(connection, "SELECT count(*)::text FROM enrichment_job_input_route WHERE job_id='" + job + "'"))
                    .isEqualTo("1");
            assertThat(value(connection, "SELECT status FROM enrichment_plan_step WHERE id='" + STEP + "'"))
                    .isEqualTo("QUEUED");

            assertThatThrownBy(() -> execute(connection, "UPDATE enrichment_plan_step SET status='UNKNOWN' WHERE id='" + STEP + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO persistent_job(id,job_key,job_type,status,attempts,next_run_at,created_at,updated_at,
                        managed_collection,command_sha256,scheduled_at,max_attempts)
                    VALUES ('87000000-0000-0000-0000-000000000002','invalid-enrichment-job','UNKNOWN','PENDING',0,
                        '2031-08-10T14:55Z','2031-08-10T08:00Z','2031-08-10T08:00Z',true,'%s',
                        '2031-08-10T14:55Z',3)
                    """.formatted(HASH))).isInstanceOf(SQLException.class);
        }
    }

    private static void seedV013(Connection connection) throws Exception {
        execute(connection, """
                INSERT INTO provider_budget_scope(id,provider,account_ref,created_at)
                VALUES ('81000000-0000-0000-0000-000000000001','highlightly','synthetic-enr014','2031-08-10T08:00Z');
                INSERT INTO provider_budget_window(id,scope_id,starts_at,ends_at,capacity,project_limit,reserve,
                    shared_initial,project_initial,cadence_limit,cadence_period_ms,state,quota_inconsistent,version,
                    created_at,updated_at,proof_logical_id,proof_sha256)
                VALUES ('%s','81000000-0000-0000-0000-000000000001','2031-08-10T08:00Z','2031-08-11T08:00Z',
                    NULL,80,0,0,0,30,3600000,'ACTIVE',false,1,'2031-08-10T08:00Z','2031-08-10T08:00Z',
                    'synthetic-enr014','%s');
                INSERT INTO canonical_competition(id,canonical_name,country_code,competition_type,created_at,updated_at)
                VALUES ('81100000-0000-0000-0000-000000000001','Synthetic ENR014','XX','LEAGUE','2031-08-10T08:00Z','2031-08-10T08:00Z');
                INSERT INTO canonical_team(id,canonical_name,country_code,created_at,updated_at)
                VALUES ('81200000-0000-0000-0000-000000000001','ENR014 Home','XX','2031-08-10T08:00Z','2031-08-10T08:00Z'),
                       ('81200000-0000-0000-0000-000000000002','ENR014 Away','XX','2031-08-10T08:00Z','2031-08-10T08:00Z');
                INSERT INTO canonical_season(id,competition_id,season_label,created_at,updated_at)
                VALUES ('81300000-0000-0000-0000-000000000001','81100000-0000-0000-0000-000000000001',
                    '2031/2032','2031-08-10T08:00Z','2031-08-10T08:00Z');
                INSERT INTO canonical_fixture(id,competition_id,home_team_id,away_team_id,kickoff_at,status,created_at,
                    updated_at,season_id,phase,neutral_venue,participants_unordered)
                VALUES ('%s','81100000-0000-0000-0000-000000000001','81200000-0000-0000-0000-000000000001',
                    '81200000-0000-0000-0000-000000000002','2031-08-10T15:00Z','SCHEDULED','2031-08-10T08:00Z',
                    '2031-08-10T08:00Z','81300000-0000-0000-0000-000000000001','LEAGUE',NULL,false);
                INSERT INTO enrichment_daily_plan(id,idempotency_key,command_sha256,budget_window_id,competition_date,
                    registry_sha256,estimated_calls_per_fixture,selected_fixture_count,estimated_calls,evaluated_at,created_at)
                VALUES ('%s','synthetic-enr014-plan','%s','%s','2031-08-10','%s',1,1,1,'2031-08-10T08:00Z','2031-08-10T08:00Z');
                INSERT INTO enrichment_daily_admission(id,plan_id,canonical_fixture_id,admission_order,priority,kickoff_at,
                    estimated_calls,created_at)
                VALUES ('%s','%s','%s',1,true,'2031-08-10T15:00Z',1,'2031-08-10T08:00Z');
                INSERT INTO enrichment_plan_step(id,admission_id,step_code,family_scope,scheduled_at,condition_code,
                    status,created_at)
                VALUES ('%s','%s','DETAIL_AT_KICKOFF','DETAIL','2031-08-10T15:00Z','SCHEDULED_KICKOFF_WINDOW',
                    'PLANNED','2031-08-10T08:00Z');
                INSERT INTO persistent_job(id,job_key,job_type,status,attempts,created_at,updated_at)
                VALUES ('81400000-0000-0000-0000-000000000001','historical-before-enr014','CALENDAR_DISCOVERY',
                    'PENDING',0,'2031-08-10T08:00Z','2031-08-10T08:00Z');
                """.formatted(WINDOW, HASH, FIXTURE, PLAN, HASH, WINDOW, HASH, ADMISSION, PLAN, FIXTURE, STEP, ADMISSION));
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
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
