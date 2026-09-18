package com.bettingproject.collection.application.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.bettingproject.collection.adapter.persistence.JdbcCalendarCollectionStore;
import com.bettingproject.collection.adapter.persistence.JdbcSnapshotStore;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CalendarCollectionStoreIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");
    private static final String HASH = "a".repeat(64);
    private JdbcClient jdbc;
    private JdbcCalendarCollectionStore store;
    private TransactionTemplate transaction;
    private UUID window;
    private UUID intent;

    @BeforeEach
    void prepare() {
        String schema = "calendar_store_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        String url = POSTGRESQL.getJdbcUrl() + (POSTGRESQL.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        var dataSource = new DriverManagerDataSource(url, POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        jdbc = JdbcClient.create(dataSource);
        store = new JdbcCalendarCollectionStore(jdbc, new JdbcSnapshotStore(jdbc));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        UUID scope = UUID.randomUUID();
        window = UUID.randomUUID();
        intent = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO provider_budget_scope (id, provider, account_ref, created_at)
                VALUES (:id, 'synthetic', 'account', '2026-09-18T08:00:00Z')
                """).param("id", scope).update();
        jdbc.sql("""
                INSERT INTO provider_budget_window (
                    id, scope_id, starts_at, ends_at, capacity, project_limit, reserve, shared_initial, project_initial,
                    state, quota_inconsistent, version, created_at, updated_at, proof_logical_id, proof_sha256
                ) VALUES (:id, :scope, '2026-09-18T08:00:00Z', '2026-09-19T08:00:00Z', 100, 80, 20, 0, 0,
                    'ACTIVE', false, 1, '2026-09-18T08:00:00Z', '2026-09-18T08:00:00Z', 'synthetic-proof', :hash)
                """).param("id", window).param("scope", scope).param("hash", HASH).update();
        jdbc.sql("""
                INSERT INTO provider_call_intent (
                    id, window_id, idempotency_key, logical_endpoint, request_sha256, state,
                    version, created_at, updated_at
                ) VALUES (:id, :window, :key, 'calendar/matches', :hash, 'RESERVED', 1,
                    '2026-09-18T08:00:00Z', '2026-09-18T08:00:00Z')
                """).param("id", intent).param("window", window).param("key", intent.toString())
                .param("hash", HASH).update();
    }

    @Test
    void keepsOnePageAuditAndOutboxThenRecordsRawEvidenceAndAppendOnlyDerivations() {
        CalendarCollectionRecord collection = collection();
        transaction.executeWithoutResult(status -> {
            assertThat(store.createAndResolve(collection).inserted()).isTrue();
            assertThat(store.createAndResolve(collection).inserted()).isFalse();
        });
        CalendarPageRecord page = transaction.execute(status -> store.preparePage(
                collection.id(), intent, 1, 0, 100, "synthetic-v1", NOW));
        CalendarPageRecord repeated = transaction.execute(status -> store.preparePage(
                collection.id(), intent, 1, 0, 100, "synthetic-v1", NOW.plusSeconds(1)));
        assertThat(repeated).isEqualTo(page);
        assertThat(count("calendar_collection_page")).isEqualTo(1);
        assertThat(count("provider_call_audit")).isEqualTo(1);
        assertThat(count("outbox_message")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload_json::text FROM outbox_message").query(String.class).single())
                .contains(page.id().toString(), intent.toString()).doesNotContain("account", "requestSha256");
        RawSnapshot raw = RawSnapshot.capture("synthetic", "calendar/matches", NOW.plusSeconds(2),
                "{\"synthetic\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8), "synthetic-v1");
        transaction.executeWithoutResult(status -> {
            store.recordResponse(page.id(), raw, NOW.plusSeconds(1), NOW.plusSeconds(2), 200, 99L, "RECEIVED");
            store.recordResponse(page.id(), raw, NOW.plusSeconds(1), NOW.plusSeconds(2), 200, 99L, "RECEIVED");
        });
        CalendarPageRecord received = store.findPage(page.id()).orElseThrow();
        assertThat(received.rawSha256()).isEqualTo(raw.sha256());
        assertThat(store.readRaw(received.rawSnapshotId()).orElseThrow().payload()).isEqualTo(raw.payload());
        assertThat(jdbc.sql("SELECT status FROM outbox_message").query(String.class).single()).isEqualTo("DELIVERED");
        assertThat(jdbc.sql("SELECT latency_ms FROM provider_call_audit").query(Long.class).single()).isEqualTo(1000L);
        assertThat(jdbc.sql("SELECT attempt_count FROM outbox_message").query(Integer.class).single()).isEqualTo(1);
        transaction.executeWithoutResult(status -> {
            store.appendDerivation(new CalendarDerivationRecord(UUID.randomUUID(), page.id(), "parser-v1",
                    "INCOMPATIBLE", null, raw.sha256(), NOW.plusSeconds(3)));
            store.appendDerivation(new CalendarDerivationRecord(UUID.randomUUID(), page.id(), "parser-v2",
                    "PARSED", received.rawSnapshotId(), raw.sha256(), NOW.plusSeconds(4)));
            store.finish(collection.id(), "COMPLETED", null, NOW.plusSeconds(5));
        });
        assertThat(store.derivations(page.id())).hasSize(2);
        assertThat(store.findCollection(collection.id()).orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(count("raw_snapshot")).isEqualTo(1);
    }

    @Test
    void responseFailureRollsBackRawPageAndAuditInsteadOfPublishingFalseSuccess() {
        CalendarPageRecord page = preparePage();
        jdbc.sql("DELETE FROM outbox_message").update();
        RawSnapshot raw = RawSnapshot.capture("synthetic", "calendar/matches", NOW.plusSeconds(1),
                new byte[] {1, 2, 3}, "synthetic-v1");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> store.recordResponse(
                page.id(), raw, NOW, NOW.plusSeconds(1), 200, null, "RECEIVED")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.findPage(page.id()).orElseThrow().responseCode()).isEqualTo("PENDING");
        assertThat(count("raw_snapshot")).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM provider_call_audit WHERE received_at IS NOT NULL")
                .query(Long.class).single()).isZero();
    }

    @Test
    void refusesCollisionAndForeignProvenanceAndNeverPersistsSecretEchoBytes() {
        CalendarPageRecord page = preparePage();
        assertThatThrownBy(() -> transaction.execute(status -> store.preparePage(
                page.collectionId(), intent, 1, 10, 100, "synthetic-v1", NOW)))
                .isInstanceOf(IllegalStateException.class);
        RawSnapshot other = RawSnapshot.capture("other-synthetic", "calendar/matches", NOW.plusSeconds(1),
                new byte[] {1}, "synthetic-v1");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> store.recordResponse(
                page.id(), other, NOW, NOW.plusSeconds(1), 200, null, "RECEIVED")))
                .isInstanceOf(IllegalStateException.class);
        transaction.executeWithoutResult(status -> store.recordResponse(
                page.id(), null, NOW, NOW.plusSeconds(1), 200, null, "SECRET_ECHO"));
        assertThat(count("raw_snapshot")).isZero();
        assertThat(store.findPage(page.id()).orElseThrow().responseCode()).isEqualTo("SECRET_ECHO");
        assertThat(jdbc.sql("SELECT status FROM outbox_message").query(String.class).single()).isEqualTo("FAILED");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> store.recordResponse(
                page.id(), null, NOW, NOW.plusSeconds(1), 201, null, "SECRET_ECHO")))
                .isInstanceOf(IllegalStateException.class);
    }

    private CalendarPageRecord preparePage() {
        return transaction.execute(status -> {
            CalendarCollectionRecord collection = collection();
            store.createAndResolve(collection);
            return store.preparePage(collection.id(), intent, 1, 0, 100, "synthetic-v1", NOW);
        });
    }

    private CalendarCollectionRecord collection() {
        return new CalendarCollectionRecord(UUID.randomUUID(), window,
                new ProviderCapabilityKey("synthetic", "competition", "2026/2027", "LEAGUE", CapabilityDataType.CALENDAR),
                LocalDate.of(2026, 9, 18), 2026, HASH, HASH, "RUNNING", null, NOW, NOW);
    }

    private long count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }
}
