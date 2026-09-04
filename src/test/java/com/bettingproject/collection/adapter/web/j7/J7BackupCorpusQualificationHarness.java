package com.bettingproject.collection.adapter.web.j7;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import com.bettingproject.collection.adapter.persistence.JdbcJ7ImportStore;
import com.bettingproject.collection.application.imports.J7ImportCommand;
import com.bettingproject.collection.application.imports.J7ImportPurgeService;
import com.bettingproject.collection.application.imports.J7ImportResult;
import com.bettingproject.collection.application.imports.J7ImportRetentionPolicy;
import com.bettingproject.collection.application.imports.J7ImportService;
import com.bettingproject.collection.application.imports.StoredJ7Import;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit, test-only corpus preparation for the INT-001 encrypted backup/restore proof.
 *
 * <p>The class deliberately does not match the default Surefire or Failsafe naming patterns. It
 * must be selected by its exact name and guarded action token. It creates no database, container,
 * listener, route or remote connection and never deletes pre-existing data.</p>
 */
@Tag("manual-qualification")
final class J7BackupCorpusQualificationHarness {

    static final String ACTION_PROPERTY = "int001.backup.corpus.action";
    static final String DATABASE_PROPERTY = "int001.backup.corpus.database";
    static final String USERNAME_PROPERTY = "int001.backup.corpus.username";
    static final String REQUIRED_ACTION = "PREPARE_INT001_SYNTHETIC_BACKUP_CORPUS";
    static final String PASSWORD_ENVIRONMENT = "BETTING_DB_PASSWORD";

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5433;
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final Instant EXPIRED_GENERATED_AT =
            Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant EXPIRED_DECIDED_AT =
            Instant.parse("2026-07-01T00:01:00Z");
    private static final Instant EXPIRED_RECEIVED_AT =
            Instant.parse("2026-07-01T00:02:00Z");
    private static final Instant QUALIFICATION_NOW =
            Instant.parse("2026-08-02T00:03:00Z");
    private static final Instant CURRENT_GENERATED_AT =
            Instant.parse("2026-09-02T00:00:00Z");
    private static final Instant CURRENT_DECIDED_AT =
            Instant.parse("2026-09-02T00:01:00Z");
    private static final Instant CURRENT_RECEIVED_AT =
            Instant.parse("2026-09-03T00:04:00Z");
    private static final String CERTIFICATE_SHA_256 = "f".repeat(64);

    private static final UUID CURRENT_EXPORT_ID =
            UUID.fromString("71000000-0000-4000-8000-000000000001");
    private static final UUID PURGED_EXPORT_ID =
            UUID.fromString("71000000-0000-4000-8000-000000000002");
    private static final UUID PENDING_EXPORT_ID =
            UUID.fromString("71000000-0000-4000-8000-000000000003");

    @Test
    void preparesTheBoundedSyntheticBackupCorpus() {
        QualificationTarget target = QualificationTarget.from(
                systemProperties(), System.getenv());
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                target.jdbcUrl(), target.username(), target.password());
        JdbcClient jdbcClient = JdbcClient.create(dataSource);

        assertFreshStandardPostgreSql17(jdbcClient, target.database());
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load()
                .migrate();
        assertExpectedMigrations(jdbcClient);

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        JdbcJ7ImportStore store = new JdbcJ7ImportStore(jdbcClient);
        StrictJ7ImportParser parser = new StrictJ7ImportParser();

        J7ImportService expiredService = service(store, EXPIRED_RECEIVED_AT);
        StoredJ7Import purgedReceipt = imported(
                transaction,
                expiredService,
                command(
                        parser,
                        PURGED_EXPORT_ID,
                        EXPIRED_GENERATED_AT,
                        EXPIRED_DECIDED_AT));
        StoredJ7Import pendingReceipt = imported(
                transaction,
                expiredService,
                command(
                        parser,
                        PENDING_EXPORT_ID,
                        EXPIRED_GENERATED_AT,
                        EXPIRED_DECIDED_AT));

        int delivered = transaction.execute(status -> markExactOutboxDelivered(
                jdbcClient, purgedReceipt, QUALIFICATION_NOW));
        assertThat(delivered).isEqualTo(1);

        J7ImportPurgeService purgeService = new J7ImportPurgeService(
                store, Clock.fixed(QUALIFICATION_NOW, ZoneOffset.UTC));
        int purged = transaction.execute(status -> purgeService.purgeExpiredPayloads(10));
        assertThat(purged).isEqualTo(1);

        J7ImportCommand current = command(
                parser,
                CURRENT_EXPORT_ID,
                CURRENT_GENERATED_AT,
                CURRENT_DECIDED_AT);
        J7ImportService currentService = service(store, CURRENT_RECEIVED_AT);
        StoredJ7Import currentReceipt = imported(
                transaction, currentService, current);
        J7ImportResult duplicate = transaction.execute(
                status -> currentService.receive(current, CERTIFICATE_SHA_256));
        assertThat(duplicate).isInstanceOf(J7ImportResult.Duplicate.class);

        assertCorpus(jdbcClient, store, currentReceipt, purgedReceipt, pendingReceipt);
        writeSafeEvidence();
    }

    private J7ImportService service(JdbcJ7ImportStore store, Instant receivedAt) {
        return new J7ImportService(
                store,
                new J7ImportRetentionPolicy(RETENTION),
                Clock.fixed(receivedAt, ZoneOffset.UTC));
    }

    private StoredJ7Import imported(
            TransactionTemplate transaction,
            J7ImportService service,
            J7ImportCommand command) {
        J7ImportResult result = transaction.execute(
                status -> service.receive(command, CERTIFICATE_SHA_256));
        assertThat(result).isInstanceOf(J7ImportResult.Imported.class);
        return ((J7ImportResult.Imported) result).receipt();
    }

    private J7ImportCommand command(
            StrictJ7ImportParser parser,
            UUID exportId,
            Instant generatedAt,
            Instant decidedAt) {
        ObjectNode root = J7ImportTestArtifact.valid().root();
        ObjectNode manifest = (ObjectNode) root.get("manifest");
        manifest.put("exportId", exportId.toString());
        manifest.put("generatedAt", generatedAt.toString());
        ((ObjectNode) manifest.get("validation")).put("decidedAt", decidedAt.toString());
        ArrayNode sources = (ArrayNode) manifest.get("sources");
        ((ObjectNode) sources.get(0)).put("receivedAt", generatedAt.toString());
        manifest.put(
                "sourceSetSha256",
                J7ImportTestArtifact.sha256(J7ImportTestArtifact.writeCompact(sources)));
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.fromRoot(root);
        assertThat(new String(artifact.body(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("SYNTHETIC_FIXTURE", "SYNTHETIC_SOURCE");
        return parser.parse(artifact.headers(), artifact.body());
    }

    private int markExactOutboxDelivered(
            JdbcClient jdbcClient,
            StoredJ7Import receipt,
            Instant deliveredAt) {
        return jdbcClient.sql("""
                UPDATE outbox_message
                SET status = 'DELIVERED',
                    delivered_at = :deliveredAt,
                    updated_at = :deliveredAt
                WHERE aggregate_type = 'j7_import_receipt'
                  AND aggregate_id = :importId
                  AND destination = 'J7_IMPORT_ACCEPTED'
                  AND idempotency_key = :idempotencyKey
                  AND status = 'PENDING'
                """)
                .param("deliveredAt", deliveredAt.atOffset(ZoneOffset.UTC))
                .param("importId", receipt.id())
                .param("idempotencyKey", "j7-import-accepted:" + receipt.id())
                .update();
    }

    private void assertCorpus(
            JdbcClient jdbcClient,
            JdbcJ7ImportStore store,
            StoredJ7Import current,
            StoredJ7Import purged,
            StoredJ7Import pending) {
        assertThat(count(jdbcClient, "j7_import_receipt")).isEqualTo(3);
        assertThat(count(jdbcClient, "j7_import_payload")).isEqualTo(2);
        assertThat(count(jdbcClient, "j7_import_audit")).isEqualTo(5);
        assertThat(count(jdbcClient, "j7_import_payload_tombstone")).isEqualTo(1);
        assertThat(jdbcClient.sql("""
                SELECT count(*)
                FROM outbox_message
                WHERE destination = 'J7_IMPORT_ACCEPTED'
                """).query(Long.class).single()).isEqualTo(3);
        assertThat(jdbcClient.sql("""
                SELECT count(*)
                FROM j7_import_audit
                WHERE event_type = 'DUPLICATE'
                """).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("""
                SELECT count(*)
                FROM j7_import_audit
                WHERE event_type = 'PAYLOAD_PURGED'
                """).query(Long.class).single()).isEqualTo(1);
        assertThat(outboxStatus(jdbcClient, current)).isEqualTo("PENDING");
        assertThat(outboxStatus(jdbcClient, purged)).isEqualTo("DELIVERED");
        assertThat(outboxStatus(jdbcClient, pending)).isEqualTo("PENDING");
        assertThat(store.findPayload(current.id())).isPresent();
        assertThat(store.findPayload(purged.id())).isEmpty();
        assertThat(store.findPayload(pending.id())).isPresent();
        assertThat(store.findByExportId(purged.exportId()).orElseThrow().payloadPurgedAt())
                .contains(QUALIFICATION_NOW);
        assertThat(pending.payloadExpiresAt()).isBefore(QUALIFICATION_NOW);
    }

    private String outboxStatus(JdbcClient jdbcClient, StoredJ7Import receipt) {
        return jdbcClient.sql("""
                SELECT status
                FROM outbox_message
                WHERE destination = 'J7_IMPORT_ACCEPTED'
                  AND aggregate_id = :importId
                """)
                .param("importId", receipt.id())
                .query(String.class)
                .single();
    }

    private long count(JdbcClient jdbcClient, String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private void assertFreshStandardPostgreSql17(JdbcClient jdbcClient, String database) {
        Integer serverVersion = jdbcClient.sql(
                "SELECT current_setting('server_version_num')::integer")
                .query(Integer.class)
                .single();
        assertThat(serverVersion).isBetween(170000, 179999);
        assertThat(jdbcClient.sql("SELECT current_database()")
                .query(String.class)
                .single()).isEqualTo(database);
        Instant databaseNow = jdbcClient.sql("SELECT clock_timestamp()")
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
        assertThat(databaseNow).isAfterOrEqualTo(CURRENT_RECEIVED_AT);
        assertThat(jdbcClient.sql(FRESH_STANDARD_DATABASE_SQL)
                .query(Boolean.class)
                .single()).isTrue();
    }

    private void assertExpectedMigrations(JdbcClient jdbcClient) {
        assertThat(jdbcClient.sql("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success
                """).query(Long.class).single()).isEqualTo(8);
        assertThat(jdbcClient.sql("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success
                  AND script IN (
                    'V006__j7_import_inbox.sql',
                    'V007__j7_import_purge_integrity.sql',
                    'V008__j7_import_upgrade_evidence_time_integrity.sql'
                  )
                """).query(Long.class).single()).isEqualTo(3);
    }

    private void writeSafeEvidence() {
        System.out.println("INT001_J7_CORPUS_RESULT=PREPARED");
        System.out.println("INT001_J7_CORPUS_SOURCE=SYNTHETIC_ONLY");
        System.out.println("INT001_J7_CORPUS_POSTGRES_MAJOR=17");
        System.out.println("INT001_J7_CORPUS_REQUIRED_MIGRATION=V008");
        System.out.println("INT001_J7_CORPUS_RECEIPT_COUNT=3");
        System.out.println("INT001_J7_CORPUS_PAYLOAD_COUNT=2");
        System.out.println("INT001_J7_CORPUS_AUDIT_COUNT=5");
        System.out.println("INT001_J7_CORPUS_OUTBOX_COUNT=3");
        System.out.println("INT001_J7_CORPUS_TOMBSTONE_COUNT=1");
        System.out.println("INT001_J7_CORPUS_PROVIDER_CALLS=0");
        System.out.println("INT001_J7_CORPUS_LOCAL_LAB_CALLS=0");
        System.out.println("INT001_J7_CORPUS_REMOTE_CALLS=0");
    }

    private static Map<String, String> systemProperties() {
        Properties properties = System.getProperties();
        return properties.stringPropertyNames().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        name -> name,
                        properties::getProperty));
    }

    static final class QualificationTarget {

        private static final Pattern DATABASE = Pattern.compile(
                "int001_j7_source_[a-z0-9_]{1,40}");
        private static final Pattern USERNAME = Pattern.compile(
                "[A-Za-z_][A-Za-z0-9_$.-]{0,62}");
        private static final Set<String> EXPECTED_PROPERTIES = Set.of(
                ACTION_PROPERTY, DATABASE_PROPERTY, USERNAME_PROPERTY);
        private static final Set<String> REJECTED_AMBIENT_SELECTORS = Set.of(
                "PGPASSWORD", "PGHOSTADDR", "PGSERVICE", "PGSERVICEFILE",
                "PGHOST", "PGPORT", "PGDATABASE", "PGUSER");

        private final String database;
        private final String username;
        private final String password;

        private QualificationTarget(String database, String username, String password) {
            this.database = database;
            this.username = username;
            this.password = password;
        }

        static QualificationTarget from(
                Map<String, String> properties,
                Map<String, String> environment) {
            Set<String> unknown = new TreeSet<>();
            properties.keySet().stream()
                    .filter(name -> name.startsWith("int001.backup.corpus."))
                    .filter(name -> !EXPECTED_PROPERTIES.contains(name))
                    .forEach(unknown::add);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException(
                        "INT001_CORPUS_UNKNOWN_PROPERTY:" + String.join(",", unknown));
            }
            String action = require(properties, ACTION_PROPERTY);
            if (!REQUIRED_ACTION.equals(action)) {
                throw new IllegalArgumentException("INT001_CORPUS_ACTION_NOT_CONFIRMED");
            }
            String database = require(properties, DATABASE_PROPERTY);
            if (!DATABASE.matcher(database).matches()) {
                throw new IllegalArgumentException("INT001_CORPUS_DATABASE_INVALID");
            }
            String username = require(properties, USERNAME_PROPERTY);
            if (!USERNAME.matcher(username).matches()) {
                throw new IllegalArgumentException("INT001_CORPUS_USERNAME_INVALID");
            }
            for (String selector : REJECTED_AMBIENT_SELECTORS) {
                if (!isBlank(environment.get(selector))) {
                    throw new IllegalArgumentException(
                            "INT001_CORPUS_AMBIENT_POSTGRES_SELECTOR_REJECTED:" + selector);
                }
            }
            String password = environment.get(PASSWORD_ENVIRONMENT);
            if (isBlank(password) || password.length() > 1024
                    || password.indexOf('\0') >= 0
                    || password.indexOf('\r') >= 0
                    || password.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(
                        "INT001_CORPUS_PASSWORD_ENVIRONMENT_INVALID");
            }
            return new QualificationTarget(database, username, password);
        }

        private static String require(Map<String, String> properties, String name) {
            String value = properties.get(name);
            if (isBlank(value)) {
                throw new IllegalArgumentException("INT001_CORPUS_PROPERTY_MISSING:" + name);
            }
            return value;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        String database() {
            return database;
        }

        String username() {
            return username;
        }

        String password() {
            return password;
        }

        String jdbcUrl() {
            return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + database;
        }

        @Override
        public String toString() {
            return "QualificationTarget[database=" + database
                    + ",username=" + username + ",password=<redacted>]";
        }
    }

    private static final String FRESH_STANDARD_DATABASE_SQL = """
            SELECT
                NOT EXISTS (
                    SELECT 1
                    FROM pg_namespace namespace
                    WHERE namespace.nspname NOT IN ('pg_catalog', 'information_schema', 'public')
                      AND namespace.nspname NOT LIKE 'pg_toast%'
                      AND namespace.nspname NOT LIKE 'pg_temp_%'
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public'
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM pg_proc routine
                    JOIN pg_namespace namespace ON namespace.oid = routine.pronamespace
                    WHERE namespace.nspname = 'public'
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM pg_type data_type
                    JOIN pg_namespace namespace ON namespace.oid = data_type.typnamespace
                    WHERE namespace.nspname = 'public'
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM pg_collation collation
                    JOIN pg_namespace namespace ON namespace.oid = collation.collnamespace
                    WHERE namespace.nspname = 'public'
                )
                AND NOT EXISTS (SELECT 1 FROM pg_largeobject_metadata)
                AND NOT EXISTS (SELECT 1 FROM pg_publication)
                AND NOT EXISTS (
                    SELECT 1
                    FROM pg_subscription subscription
                    WHERE subscription.subdbid = (
                        SELECT database.oid
                        FROM pg_database database
                        WHERE database.datname = current_database()
                    )
                )
                AND NOT EXISTS (SELECT 1 FROM pg_event_trigger)
                AND NOT EXISTS (SELECT 1 FROM pg_foreign_data_wrapper)
                AND NOT EXISTS (SELECT 1 FROM pg_foreign_server)
                AND NOT EXISTS (SELECT 1 FROM pg_user_mappings)
                AND NOT EXISTS (SELECT 1 FROM pg_default_acl)
                AND NOT EXISTS (SELECT 1 FROM pg_seclabel)
                AND NOT EXISTS (
                    SELECT 1
                    FROM pg_language language
                    WHERE language.lanname NOT IN ('internal', 'c', 'sql', 'plpgsql')
                )
                AND NOT EXISTS (SELECT 1 FROM pg_cast WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_conversion WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_operator WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_opclass WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_opfamily WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_am WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_transform WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_ts_dict WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_ts_parser WHERE oid >= 16384)
                AND NOT EXISTS (SELECT 1 FROM pg_ts_template WHERE oid >= 16384)
                AND NOT EXISTS (
                    SELECT 1 FROM pg_extension WHERE extname <> 'plpgsql'
                )
            """;
}
