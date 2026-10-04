package com.bettingproject.collection.application.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.bettingproject.BettingProjectApplication;
import com.bettingproject.collection.adapter.persistence.JdbcEnrichmentAdmissionStore;
import com.bettingproject.collection.application.control.EnrichmentAdmissionStore;
import com.bettingproject.collection.application.enrichment.EnrichmentObservationStore;
import com.bettingproject.collection.application.enrichment.EnrichmentObservationWrite;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.enrichment.domain.*;
import com.bettingproject.qualification.domain.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EnrichmentPersistenceIT {
    private static final Instant NOW = Instant.parse("2030-08-10T12:00:00Z");
    private static final String HASH_A = "a".repeat(64);
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    private static ConfigurableApplicationContext context;
    private static JdbcClient jdbc;
    private static PlatformTransactionManager transactionManager;
    private static EnrichmentAdmissionStore admissions;
    private static EnrichmentObservationStore observations;

    @BeforeAll static void start() {
        context = new SpringApplicationBuilder(BettingProjectApplication.class, TestClockConfiguration.class)
                .profiles("control-api").web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run("--spring.datasource.url=" + DB.getJdbcUrl(), "--spring.datasource.username=" + DB.getUsername(),
                        "--spring.datasource.password=" + DB.getPassword(), "--BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                        "--betting.operator.id=test-operator");
        jdbc = context.getBean(JdbcClient.class);
        transactionManager = context.getBean(PlatformTransactionManager.class);
        admissions = context.getBean(EnrichmentAdmissionStore.class);
        observations = context.getBean(EnrichmentObservationStore.class);
    }

    @AfterAll static void stop() { if (context != null) { context.close(); } }

    @Test void concurrentAdmissionTransactionsRespectOnePlanPerUtcDayAndStoreTheWinningRows() throws Exception {
        UUID window = seedWindow();
        Fixture fixture = seedFixture();
        LocalDate day = LocalDate.of(2030, 8, 10).plusDays(Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 10000));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> first = () -> tryInsert(day, window, fixture, "daily-a-" + UUID.randomUUID(), ready, start);
        Callable<Boolean> second = () -> tryInsert(day, window, fixture, "daily-b-" + UUID.randomUUID(), ready, start);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(first); var b = pool.submit(second);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var stored = admissions.findByDate(day).orElseThrow();
        assertThat(stored.admissions()).hasSize(1);
        assertThat(stored.admissions().getFirst().steps()).hasSize(5);
        assertThat(jdbc.sql("SELECT count(*) FROM enrichment_plan_step WHERE admission_id = :id")
                .param("id", stored.admissions().getFirst().id()).query(Long.class).single()).isEqualTo(5);
    }

    @Test void observationAndQualityWritesAreAppendOnlyAndIdempotentOnReplay() {
        Seed seed = seedForObservation();
        String json = "{\"family\":\"MATCH_DETAIL\",\"state\":\"AVAILABLE\"}";
        String hash = SnapshotHasher.sha256(json.getBytes(StandardCharsets.UTF_8));
        var finding = new QualityFinding(UUID.randomUUID(), seed.observationId(), QualityIssueCode.MISSING_PLAYER_FULL_NAME,
                QualityEntityScope.PLAYER, "synthetic-player-1", NOW.plusSeconds(1));
        var write = new EnrichmentObservationWrite(seed.observation(), json, hash, List.of(finding));

        var first = observations.storeAndResolve(write);
        var replay = observations.storeAndResolve(write);
        assertThat(first.inserted()).isTrue();
        assertThat(first.findingsInserted()).isEqualTo(1);
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.inserted()).isFalse();
        assertThat(replay.findingsInserted()).isZero();
        assertThat(count("provider_enrichment_observation", "id", first.id())).isEqualTo(1);
        assertThat(count("enrichment_quality_finding", "enrichment_observation_id", first.id())).isEqualTo(1);
        assertThat(jdbc.sql("SELECT representation_sha256 FROM provider_enrichment_observation WHERE id = :id")
                .param("id", first.id()).query(String.class).single()).isEqualTo(hash);
    }

    @Test void invalidDerivedJsonRollsBackTheObservationTransaction() {
        Seed seed = seedForObservation();
        String invalid = "{not-json";
        var write = new EnrichmentObservationWrite(seed.observation(), invalid,
                SnapshotHasher.sha256(invalid.getBytes(StandardCharsets.UTF_8)), List.of());
        assertThatThrownBy(() -> observations.storeAndResolve(write)).isInstanceOf(RuntimeException.class);
        assertThat(count("provider_enrichment_observation", "id", seed.observationId())).isZero();
    }

    private boolean tryInsert(LocalDate date, UUID window, Fixture fixture, String key,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            try {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Admission race timed out"); }
            }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            admissions.lockIdempotencyKey(key);
            admissions.lockDate(date);
            if (admissions.findByDate(date).isPresent()) { return false; }
            Instant kickoff = NOW.plusSeconds(3600);
            var steps = List.of(
                    new EnrichmentPlanStep(UUID.randomUUID(), EnrichmentPlanStepCode.LINEUP_T_MINUS_30, "LINEUP", kickoff.minusSeconds(1800), "LINEUP_NOT_COMPLETE"),
                    new EnrichmentPlanStep(UUID.randomUUID(), EnrichmentPlanStepCode.LINEUP_T_MINUS_15, "LINEUP", kickoff.minusSeconds(900), "LINEUP_NOT_COMPLETE"),
                    new EnrichmentPlanStep(UUID.randomUUID(), EnrichmentPlanStepCode.DETAIL_AT_KICKOFF, "DETAIL", kickoff, "SCHEDULED_KICKOFF_WINDOW"),
                    new EnrichmentPlanStep(UUID.randomUUID(), EnrichmentPlanStepCode.DETAIL_PLUS_45, "DETAIL", kickoff.plusSeconds(2700), "SCHEDULED_KICKOFF_WINDOW"),
                    new EnrichmentPlanStep(UUID.randomUUID(), EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL, "POSTMATCH", null, "EXPLICIT_FINAL_STATUS"));
            var item = new DailyEnrichmentAdmission(UUID.randomUUID(), fixture.id(), 1, false, kickoff, 10, steps);
            var plan = new DailyEnrichmentPlan(UUID.randomUUID(), key, HASH_A, window, date, HASH_A, 10,
                    NOW, NOW, List.of(item));
            return admissions.insert(plan);
        });
    }

    private Seed seedForObservation() {
        UUID window = seedWindow();
        Fixture fixture = seedFixture();
        String key = "observation-plan-" + UUID.randomUUID();
        LocalDate day = LocalDate.of(2030, 8, 10).plusDays(Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 10000));
        UUID admissionId = UUID.randomUUID(); UUID intentId = UUID.randomUUID(); UUID snapshotId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        Instant kickoff = NOW.plusSeconds(3600);
        var steps = List.of(new EnrichmentPlanStep(UUID.randomUUID(), EnrichmentPlanStepCode.DETAIL_AT_KICKOFF,
                "DETAIL", kickoff, "SCHEDULED_KICKOFF_WINDOW"));
        var item = new DailyEnrichmentAdmission(admissionId, fixture.id(), 1, false, kickoff, 1, steps);
        var plan = new DailyEnrichmentPlan(UUID.randomUUID(), key, HASH_A, window, day, HASH_A, 1, NOW, NOW, List.of(item));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            admissions.lockIdempotencyKey(key); admissions.lockDate(day); assertThat(admissions.insert(plan)).isTrue();
            String raw = "{\"seed\":\"" + snapshotId + "\"}";
            String rawHash = SnapshotHasher.sha256(raw.getBytes(StandardCharsets.UTF_8));
            jdbc.sql("""
                    INSERT INTO provider_call_intent(id,window_id,idempotency_key,logical_endpoint,request_sha256,state,version,
                        created_at,updated_at,committed_at)
                    VALUES (:id,:window,:key,'fixture/detail',:hash,'COMMITTED_FOR_SEND',2,:now,:now,:now)
                    """)
                    .param("id", intentId).param("window", window).param("key", "intent-" + UUID.randomUUID())
                    .param("hash", HASH_A).param("now", NOW.atOffset(ZoneOffset.UTC)).update();
            jdbc.sql("""
                    INSERT INTO raw_snapshot(id,provider,endpoint,requested_at,received_at,http_status,payload_sha256,
                        payload_compression,payload,connector_version)
                    VALUES (:id,'highlightly','fixture/detail',:requested,:received,200,:hash,'identity',:payload,'synthetic-v1')
                    """)
                    .param("id", snapshotId).param("requested", NOW.atOffset(ZoneOffset.UTC))
                    .param("received", NOW.plusSeconds(1).atOffset(ZoneOffset.UTC)).param("hash", rawHash)
                    .param("payload", raw.getBytes(StandardCharsets.UTF_8)).update();
        });
        var observation = new ProviderEnrichmentObservation(observationId, admissionId, fixture.id(), intentId, snapshotId,
                "highlightly", "match-1", "PPL", "2030/2031", "LEAGUE", "2030", "round-1",
                EnrichmentFamily.MATCH_DETAIL, EnrichmentObservationState.AVAILABLE,
                SnapshotHasher.sha256(("{\"seed\":\"" + snapshotId + "\"}").getBytes(StandardCharsets.UTF_8)),
                "highlightly-match-detail-v1", NOW, NOW.plusSeconds(1), null);
        return new Seed(observationId, observation);
    }

    private UUID seedWindow() {
        UUID scope = UUID.randomUUID(); UUID window = UUID.randomUUID();
        jdbc.sql("INSERT INTO provider_budget_scope(id,provider,account_ref,created_at) VALUES (:id,'highlightly',:account,:now)")
                .param("id", scope).param("account", "synthetic-" + UUID.randomUUID())
                .param("now", NOW.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("""
                INSERT INTO provider_budget_window(id,scope_id,starts_at,ends_at,capacity,project_limit,reserve,
                    shared_initial,project_initial,state,quota_inconsistent,version,created_at,updated_at,proof_logical_id,proof_sha256)
                VALUES (:id,:scope,:starts,:ends,100,80,20,0,0,'ACTIVE',false,1,:now,:now,'synthetic',:hash)
                """)
                .param("id", window).param("scope", scope).param("starts", NOW.minusSeconds(3600).atOffset(ZoneOffset.UTC))
                .param("ends", NOW.plusSeconds(86400).atOffset(ZoneOffset.UTC)).param("now", NOW.atOffset(ZoneOffset.UTC))
                .param("hash", HASH_A).update();
        return window;
    }

    private Fixture seedFixture() {
        UUID competition = UUID.randomUUID(), home = UUID.randomUUID(), away = UUID.randomUUID(), season = UUID.randomUUID();
        UUID fixture = UUID.randomUUID();
        jdbc.sql("INSERT INTO canonical_competition(id,canonical_name,country_code,competition_type,created_at,updated_at) VALUES (:id,:name,'XX','LEAGUE',:now,:now)")
                .param("id", competition).param("name", "Synthetic " + competition).param("now", NOW.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO canonical_team(id,canonical_name,country_code,created_at,updated_at) VALUES (:id,:name,'XX',:now,:now)")
                .param("id", home).param("name", "Synthetic Home " + home).param("now", NOW.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO canonical_team(id,canonical_name,country_code,created_at,updated_at) VALUES (:id,:name,'XX',:now,:now)")
                .param("id", away).param("name", "Synthetic Away " + away).param("now", NOW.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO canonical_season(id,competition_id,season_label,created_at,updated_at) VALUES (:id,:competition,'2030/2031',:now,:now)")
                .param("id", season).param("competition", competition).param("now", NOW.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("""
                INSERT INTO canonical_fixture(id,competition_id,home_team_id,away_team_id,kickoff_at,status,created_at,updated_at,
                    season_id,phase,neutral_venue,participants_unordered)
                VALUES (:id,:competition,:home,:away,:kickoff,'SCHEDULED',:now,:now,:season,'LEAGUE',NULL,false)
                """)
                .param("id", fixture).param("competition", competition).param("home", home).param("away", away)
                .param("kickoff", NOW.plusSeconds(3600).atOffset(ZoneOffset.UTC)).param("now", NOW.atOffset(ZoneOffset.UTC))
                .param("season", season).update();
        return new Fixture(fixture);
    }

    private long count(String table, String column, UUID id) {
        // Table/column are constants selected by this test, never caller input.
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = :id")
                .param("id", id).query(Long.class).single();
    }

    record Fixture(UUID id) { }
    record Seed(UUID observationId, ProviderEnrichmentObservation observation) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean @Primary Clock fixedEnrichmentClock() { return Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC); }
    }
}
