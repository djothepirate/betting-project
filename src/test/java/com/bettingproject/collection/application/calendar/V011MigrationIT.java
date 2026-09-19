package com.bettingproject.collection.application.calendar;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class V011MigrationIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String HASH = "a".repeat(64);
    private static final UUID JOB = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Test void installsFreshWithoutSchedulingOrActivatingAnything() throws Exception {
        migrate("jobs_fresh", null);
        try (var connection = connection("jobs_fresh")) {
            for (String table : List.of("persistent_job", "outbox_message", "calendar_job_input", "collection_job_event",
                    "collection_job_effect", "provider_budget_window")) {
                assertThat(value(connection, "SELECT count(*)::text FROM " + table)).isEqualTo("0");
            }
            assertThat(value(connection, "SELECT count(*)::text FROM pg_indexes WHERE schemaname = current_schema() "
                    + "AND indexname IN ('ix_collection_job_due','ix_collection_job_expired','ix_collection_job_history')")).isEqualTo("3");
        }
    }

    @Test void upgradesPopulatedV010WithoutInventingClaimsOrLosingCalendarEvidence() throws Exception {
        migrate("jobs_upgrade", "010");
        Map<String, String> before = new LinkedHashMap<>();
        try (var connection = connection("jobs_upgrade")) {
            seed(connection);
            for (String table : List.of("provider_budget_scope", "provider_budget_window", "provider_call_intent", "raw_snapshot",
                    "provider_call_audit", "calendar_collection", "calendar_collection_page", "calendar_collection_derivation", "outbox_message")) {
                before.put(table, value(connection, "SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM " + table + " t"));
            }
            before.put("persistent_job", value(connection, "SELECT to_jsonb(t)::text FROM persistent_job t"));
        }
        migrate("jobs_upgrade", null);
        try (var connection = connection("jobs_upgrade")) {
            for (var entry : before.entrySet()) {
                String sql = entry.getKey().equals("persistent_job")
                        ? "SELECT (to_jsonb(t) - ARRAY['managed_collection','command_sha256','scheduled_at','max_attempts','execution_version','lease_token','lease_until'])::text FROM persistent_job t"
                        : "SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM " + entry.getKey() + " t";
                assertThat(value(connection, sql)).as(entry.getKey()).isEqualTo(entry.getValue());
            }
            assertThat(value(connection, "SELECT count(*)::text FROM persistent_job WHERE NOT managed_collection AND execution_version = 1 AND lease_token IS NULL")).isEqualTo("1");
            for (String table : List.of("collection_job_event", "collection_job_effect", "calendar_job_input")) {
                assertThat(value(connection, "SELECT count(*)::text FROM " + table)).isEqualTo("0");
            }
            assertThat(value(connection, "SELECT count(*)::text FROM flyway_schema_history WHERE version = '011' AND success")).isEqualTo("1");
        }
    }

    @Test void rejectsPartialClaimsUnknownTypesInvalidBoundsAndMismatchedInputType() throws Exception {
        migrate("jobs_constraints", "010");
        try (var connection = connection("jobs_constraints")) { seed(connection); }
        migrate("jobs_constraints", null);
        try (var connection = connection("jobs_constraints")) {
            execute(connection, "UPDATE persistent_job SET managed_collection = TRUE, command_sha256 = '" + HASH
                    + "', scheduled_at = created_at, max_attempts = 3");
            for (String assignment : List.of("status = 'RUNNING'", "lease_token = gen_random_uuid()", "max_attempts = 0",
                    "max_attempts = 11", "max_attempts = NULL", "execution_version = 0", "attempts = 4",
                    "job_type = 'PREMATCH_ENRICHMENT'", "command_sha256 = 'bad'", "scheduled_at = NULL")) {
                assertThatThrownBy(() -> execute(connection, "UPDATE persistent_job SET " + assignment)).as(assignment).isInstanceOf(SQLException.class);
            }
            execute(connection, """
                    INSERT INTO calendar_job_input(job_id, job_type, window_id, provider, provider_competition_id,
                        source_season, source_phase, collection_date, season_start_year, registry_sha256, parser_version)
                    VALUES ('%s', 'CALENDAR_DISCOVERY', '20000000-0000-0000-0000-000000000001', 'synthetic',
                        'competition', '2030', 'phase', '2030-08-10', 2030, '%s', 'synthetic-v1')
                    """.formatted(JOB, HASH));
            assertThatThrownBy(() -> execute(connection, "UPDATE calendar_job_input SET source_phase = NULL")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "UPDATE persistent_job SET job_type = 'REPLAY_NORMALIZATION'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO collection_job_effect VALUES ('%s', 'page', 'contains unsafe details', clock_timestamp())
                    """.formatted(JOB))).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "UPDATE calendar_collection_page SET response_code = 'SEND_UNCERTAIN', raw_snapshot_id = NULL, raw_sha256 = NULL, received_at = NULL, http_status = NULL"))
                    .isInstanceOf(SQLException.class); // An already interpreted proof must not lose its provenance.
            assertThat(value(connection, "SELECT count(*)::text FROM calendar_collection_page WHERE response_code = 'RECEIVED'")).isEqualTo("1");
        }
    }

    private void seed(Connection c) throws Exception {
        execute(c, """
                INSERT INTO provider_budget_scope VALUES ('10000000-0000-0000-0000-000000000001','synthetic','synthetic-account','2030-08-10T08:00Z');
                INSERT INTO provider_budget_window (id,scope_id,starts_at,ends_at,capacity,project_limit,reserve,shared_initial,
                    project_initial,state,quota_inconsistent,version,created_at,updated_at,proof_logical_id,proof_sha256)
                VALUES ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',
                    '2030-08-10T08:00Z','2030-08-11T08:00Z',100,80,20,0,0,'ACTIVE',false,1,'2030-08-10T08:00Z','2030-08-10T08:00Z','synthetic','%s');
                INSERT INTO provider_call_intent(id,window_id,idempotency_key,logical_endpoint,request_sha256,state,version,created_at,updated_at,committed_at)
                VALUES ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','intent','calendar/matches','%s',
                    'COMMITTED_FOR_SEND',2,'2030-08-10T08:00Z','2030-08-10T08:01Z','2030-08-10T08:01Z');
                INSERT INTO raw_snapshot(id,provider,endpoint,received_at,payload_sha256,payload_compression,payload,connector_version)
                VALUES ('40000000-0000-0000-0000-000000000001','synthetic','calendar/matches','2030-08-10T08:02Z','%s','identity',decode('7b7d','hex'),'synthetic-v1');
                INSERT INTO provider_call_audit(id,provider,logical_endpoint,requested_at,connector_version)
                VALUES ('50000000-0000-0000-0000-000000000001','synthetic','calendar/matches','2030-08-10T08:01Z','synthetic-v1');
                INSERT INTO persistent_job(id,job_key,job_type,status,attempts,next_run_at,created_at,updated_at)
                VALUES ('%s','historical-job','CALENDAR_DISCOVERY','PENDING',0,'2030-08-10T08:00Z','2030-08-10T08:00Z','2030-08-10T08:00Z');
                INSERT INTO outbox_message(id,idempotency_key,aggregate_type,aggregate_id,destination,payload_json,status,created_at,updated_at)
                VALUES ('60000000-0000-0000-0000-000000000001','historical','synthetic','%s','J7_IMPORT_ACCEPTED','{}','PENDING','2030-08-10T08:00Z','2030-08-10T08:00Z');
                INSERT INTO calendar_collection(id,window_id,provider,provider_competition_id,source_season,source_phase,data_type,
                    collection_date,season_start_year,command_sha256,registry_sha256,status,created_at,updated_at)
                VALUES ('80000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','synthetic','competition','2030','phase',
                    'CALENDAR','2030-08-10',2030,'%s','%s','RUNNING','2030-08-10T08:00Z','2030-08-10T08:00Z');
                INSERT INTO calendar_collection_page(id,collection_id,window_id,intent_id,audit_id,page_number,page_offset,page_limit,connector_version,
                    raw_snapshot_id,raw_sha256,requested_at,received_at,http_status,response_code)
                VALUES ('90000000-0000-0000-0000-000000000001','80000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',
                    '30000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001',1,0,100,'synthetic-v1',
                    '40000000-0000-0000-0000-000000000001','%s','2030-08-10T08:01Z','2030-08-10T08:02Z',200,'RECEIVED');
                INSERT INTO calendar_collection_derivation(id,page_id,parser_version,outcome,raw_sha256,evaluated_at)
                VALUES ('a0000000-0000-0000-0000-000000000001','90000000-0000-0000-0000-000000000001','synthetic-v1','INCOMPATIBLE','%s','2030-08-10T08:02Z');
                """.formatted(HASH, HASH, HASH, JOB, JOB, HASH, HASH, HASH, HASH));
    }
    private void migrate(String schema, String target) {
        var configuration = Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()).schemas(schema).defaultSchema(schema);
        if (target != null) { configuration.target(MigrationVersion.fromVersion(target)); }
        configuration.load().migrate();
    }
    private Connection connection(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        connection.setSchema(schema); return connection;
    }
    private static void execute(Connection c, String sql) throws SQLException { try (var s = c.createStatement()) { s.execute(sql); } }
    private static String value(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var rows = s.executeQuery(sql)) { assertThat(rows.next()).isTrue(); return rows.getString(1); }
    }
}
