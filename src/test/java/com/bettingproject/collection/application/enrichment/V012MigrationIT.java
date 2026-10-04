package com.bettingproject.collection.application.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class V012MigrationIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String HASH = "a".repeat(64);
    private static final UUID WINDOW = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FIXTURE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID INTENT = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Test void freshInstallAndPopulatedV011UpgradeAddEmptyAppendOnlyEnrichmentStores() throws Exception {
        migrate("enr012_fresh", null);
        try (var c = connection("enr012_fresh")) {
            for (String table : List.of("enrichment_daily_plan", "enrichment_daily_admission", "enrichment_plan_step",
                    "provider_enrichment_observation", "enrichment_quality_finding")) {
                assertThat(value(c, "SELECT count(*)::text FROM " + table)).as(table).isEqualTo("0");
            }
        }

        migrate("enr012_upgrade", "011");
        try (var c = connection("enr012_upgrade")) { seedV011(c); }
        migrate("enr012_upgrade", null);
        try (var c = connection("enr012_upgrade")) {
            assertThat(value(c, "SELECT count(*)::text FROM persistent_job WHERE job_key = 'historical-calendar' AND NOT managed_collection")).isEqualTo("1");
            assertThat(value(c, "SELECT count(*)::text FROM provider_budget_window WHERE id = '" + WINDOW + "'")).isEqualTo("1");
            assertThat(value(c, "SELECT count(*)::text FROM provider_enrichment_observation")).isEqualTo("0");
            assertThat(value(c, "SELECT count(*)::text FROM enrichment_daily_plan")).isEqualTo("0");

            seedEnrichment(c);
            assertThat(value(c, "SELECT count(*)::text FROM enrichment_plan_step")).isEqualTo("5");
            assertThat(value(c, "SELECT count(*)::text FROM enrichment_quality_finding")).isEqualTo("1");
            assertThat(value(c, "SELECT count(*)::text FROM enrichment_daily_plan WHERE selected_fixture_count <= 7")).isEqualTo("1");
            assertThatThrownBy(() -> execute(c, "INSERT INTO enrichment_daily_plan (id,idempotency_key,command_sha256,budget_window_id,competition_date,registry_sha256,estimated_calls_per_fixture,selected_fixture_count,estimated_calls,evaluated_at,created_at) VALUES (gen_random_uuid(),'other','"
                    + HASH + "','" + WINDOW + "','2030-08-10','" + HASH + "',10,1,10,'2030-08-10T12:00Z','2030-08-10T12:00Z')"))
                    .isInstanceOf(SQLException.class);
            assertThat(value(c, "SELECT encode(payload, 'hex') FROM raw_snapshot WHERE id = '" + SNAPSHOT + "'")).isEqualTo("7b7d");
            assertThat(value(c, "SELECT count(*)::text FROM flyway_schema_history WHERE version = '012' AND success")).isEqualTo("1");
        }
    }

    private static void seedV011(Connection c) throws Exception {
        execute(c, """
                INSERT INTO provider_budget_scope(id,provider,account_ref,created_at)
                VALUES ('10000000-0000-0000-0000-000000000001','highlightly','synthetic-account','2030-08-10T08:00Z');
                INSERT INTO provider_budget_window(id,scope_id,starts_at,ends_at,capacity,project_limit,reserve,shared_initial,project_initial,state,quota_inconsistent,version,created_at,updated_at,proof_logical_id,proof_sha256)
                VALUES ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','2030-08-10T08:00Z','2030-08-11T08:00Z',100,80,20,0,0,'ACTIVE',false,1,'2030-08-10T08:00Z','2030-08-10T08:00Z','synthetic-budget','%s');
                INSERT INTO canonical_competition(id,canonical_name,country_code,competition_type,created_at,updated_at)
                VALUES ('31000000-0000-0000-0000-000000000001','Synthetic League','XX','LEAGUE','2030-08-10T08:00Z','2030-08-10T08:00Z');
                INSERT INTO canonical_team(id,canonical_name,country_code,created_at,updated_at)
                VALUES ('32000000-0000-0000-0000-000000000001','Synthetic Home','XX','2030-08-10T08:00Z','2030-08-10T08:00Z'),
                       ('33000000-0000-0000-0000-000000000001','Synthetic Away','XX','2030-08-10T08:00Z','2030-08-10T08:00Z');
                INSERT INTO canonical_season(id,competition_id,season_label,created_at,updated_at)
                VALUES ('34000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000001','2030/2031','2030-08-10T08:00Z','2030-08-10T08:00Z');
                INSERT INTO canonical_fixture(id,competition_id,home_team_id,away_team_id,kickoff_at,status,created_at,updated_at,season_id,phase,neutral_venue,participants_unordered)
                VALUES ('30000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000001','32000000-0000-0000-0000-000000000001','33000000-0000-0000-0000-000000000001','2030-08-10T15:00Z','SCHEDULED','2030-08-10T08:00Z','2030-08-10T08:00Z','34000000-0000-0000-0000-000000000001','LEAGUE',NULL,false);
                INSERT INTO persistent_job(id,job_key,job_type,status,attempts,next_run_at,created_at,updated_at)
                VALUES ('35000000-0000-0000-0000-000000000001','historical-calendar','CALENDAR_DISCOVERY','PENDING',0,'2030-08-10T08:00Z','2030-08-10T08:00Z','2030-08-10T08:00Z');
                INSERT INTO provider_call_intent(id,window_id,idempotency_key,logical_endpoint,request_sha256,state,version,created_at,updated_at,committed_at)
                VALUES ('40000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','synthetic-enrichment-intent','fixture/detail','%s','COMMITTED_FOR_SEND',2,'2030-08-10T12:00Z','2030-08-10T12:00Z','2030-08-10T12:00Z');
                INSERT INTO raw_snapshot(id,provider,endpoint,requested_at,received_at,http_status,payload_sha256,payload_compression,payload,connector_version)
                VALUES ('50000000-0000-0000-0000-000000000001','highlightly','fixture/detail','2030-08-10T12:00Z','2030-08-10T12:00:01Z',200,'%s','identity',decode('7b7d','hex'),'synthetic-v1');
                """.formatted(HASH, HASH, HASH));
    }

    private static void seedEnrichment(Connection c) throws Exception {
        UUID plan = UUID.fromString("60000000-0000-0000-0000-000000000001");
        UUID admission = UUID.fromString("61000000-0000-0000-0000-000000000001");
        UUID observation = UUID.fromString("62000000-0000-0000-0000-000000000001");
        execute(c, """
                INSERT INTO enrichment_daily_plan VALUES ('%s','synthetic-daily-20300810','%s','%s','2030-08-10','%s',10,1,10,'2030-08-10T12:00Z','2030-08-10T12:00Z');
                INSERT INTO enrichment_daily_admission VALUES ('%s','%s','%s',1,true,'2030-08-10T15:00Z',10,'2030-08-10T12:00Z');
                INSERT INTO enrichment_plan_step (id,admission_id,step_code,family_scope,scheduled_at,condition_code,
                    trigger_observed_at,trigger_evidence_observation_id,status,created_at) VALUES
                    ('63000000-0000-0000-0000-000000000001','%s','LINEUP_T_MINUS_30','LINEUP','2030-08-10T14:30Z','LINEUP_NOT_COMPLETE',NULL,NULL,'PLANNED','2030-08-10T12:00Z'),
                    ('63000000-0000-0000-0000-000000000002','%s','LINEUP_T_MINUS_15','LINEUP','2030-08-10T14:45Z','LINEUP_NOT_COMPLETE',NULL,NULL,'PLANNED','2030-08-10T12:00Z'),
                    ('63000000-0000-0000-0000-000000000003','%s','DETAIL_AT_KICKOFF','DETAIL','2030-08-10T15:00Z','SCHEDULED_KICKOFF_WINDOW',NULL,NULL,'PLANNED','2030-08-10T12:00Z'),
                    ('63000000-0000-0000-0000-000000000004','%s','DETAIL_PLUS_45','DETAIL','2030-08-10T15:45Z','SCHEDULED_KICKOFF_WINDOW',NULL,NULL,'PLANNED','2030-08-10T12:00Z'),
                    ('63000000-0000-0000-0000-000000000005','%s','POSTMATCH_AFTER_FINAL','POSTMATCH',NULL,'EXPLICIT_FINAL_STATUS',NULL,NULL,'PLANNED','2030-08-10T12:00Z');
                INSERT INTO provider_enrichment_observation(id,admission_id,canonical_fixture_id,budget_intent_id,raw_snapshot_id,
                    provider,provider_fixture_id,logical_competition,logical_season,logical_phase,source_season_reference,
                    source_phase_reference,family,observation_state,payload_sha256,representation_sha256,representation_json,
                    parser_version,requested_at,received_at,created_at)
                VALUES ('%s','%s','%s','%s','%s','highlightly','match-1','PPL','2030/2031','LEAGUE','2030','round-1',
                    'MATCH_DETAIL','AVAILABLE','%s','%s','{"state":"AVAILABLE"}'::jsonb,'highlightly-match-detail-v1',
                    '2030-08-10T12:00Z','2030-08-10T12:00:01Z','2030-08-10T12:00:01Z');
                INSERT INTO enrichment_quality_finding VALUES
                    ('64000000-0000-0000-0000-000000000001','%s','MISSING_PLAYER_FULL_NAME','PLAYER','player-1','2030-08-10T12:00:01Z');
                INSERT INTO enrichment_quality_finding VALUES
                    ('64000000-0000-0000-0000-000000000002','%s','MISSING_PLAYER_FULL_NAME','PLAYER','player-1','2030-08-10T12:00:01Z') ON CONFLICT DO NOTHING;
                """.formatted(plan, HASH, WINDOW, HASH, admission, plan, FIXTURE, admission, admission, admission,
                        admission, admission, observation, admission, FIXTURE, INTENT, SNAPSHOT, HASH, HASH, observation, observation));
    }

    private void migrate(String schema, String target) {
        var config = Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()).schemas(schema).defaultSchema(schema);
        if (target != null) { config.target(MigrationVersion.fromVersion(target)); }
        config.load().migrate();
    }
    private Connection connection(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        connection.setSchema(schema); return connection;
    }
    private static void execute(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement()) { statement.execute(sql); }
    }
    private static String value(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue(); return rows.getString(1);
        }
    }
}
