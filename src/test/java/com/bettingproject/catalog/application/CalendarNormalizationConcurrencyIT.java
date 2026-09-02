package com.bettingproject.catalog.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER"
        })
@ActiveProfiles("batch-worker")
@Import(CalendarNormalizationIT.SyntheticCalendarAuthorityConfiguration.class)
class CalendarNormalizationConcurrencyIT {

    private static final String SEASON = "2026/2027";
    private static final String PHASE = "REGULAR_SEASON";
    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private CatalogCommandService commandService;

    @Autowired
    private CalendarNormalizationService normalizationService;

    @Autowired
    private ProviderMappingRepository mappingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        jdbcClient.sql("""
                UPDATE canonical_fixture
                SET last_authority_observation_id = NULL,
                    last_authority_observed_at = NULL,
                    last_authority_provider = NULL,
                    last_authority_policy_version = NULL
                """).update();
        for (String table : new String[] {
                "provider_mapping_decision_anomaly",
                "normalization_anomaly_event",
                "provider_mapping_decision",
                "control_command_receipt",
                "fixture_application_log",
                "normalization_anomaly",
                "fixture_observation",
                "provider_mapping",
                "canonical_fixture",
                "canonical_season",
                "canonical_team",
                "canonical_competition",
                "raw_snapshot"
        }) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }

    @Test
    void concurrentReplayOfTheSameSnapshotKeepsOneLogicalObservationAndCanonicalEffect() throws Exception {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        RawSnapshot snapshot = snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z");

        List<NormalizationResult> results = runConcurrently(
                () -> normalizationService.normalize(snapshot),
                () -> normalizationService.normalize(snapshot));

        assertThat(results).extracting(NormalizationResult::snapshotInserted)
                .containsExactlyInAnyOrder(true, false);
        assertThat(results.stream().mapToInt(NormalizationResult::fixturesCreated).sum()).isEqualTo(1);
        assertThat(results.stream().mapToInt(NormalizationResult::fixturesUnchanged).sum()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("canonical_season")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(fixtureMappings()).isEqualTo(1);
        assertThat(applicationOutcomeCount("CREATED")).isEqualTo(1);
        assertThat(applicationOutcomeCount("UNCHANGED")).isEqualTo(1);
    }

    @Test
    void concurrentOldAndNewObservationsAlwaysConvergeOnTheNewerAuthority() throws Exception {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        RawSnapshot oldSnapshot = snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z");
        RawSnapshot newSnapshot = snapshot(
                "/fixtures/cat001/calendar-v2-highlightly-corrected.json",
                "highlightly",
                "calendar/upl",
                "2026-08-12T10:00:01Z");

        List<NormalizationResult> results = runConcurrently(
                () -> normalizationService.normalize(oldSnapshot),
                () -> normalizationService.normalize(newSnapshot));

        assertThat(results.stream().mapToInt(NormalizationResult::fixturesCreated).sum()).isEqualTo(1);
        assertThat(results.stream().mapToInt(result ->
                result.fixturesUpdated() + result.fixturesBlocked()).sum()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(2);
        assertThat(count("canonical_season")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(2);
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(fixtureMappings()).isEqualTo(1);
        assertThat(singleInstant("SELECT kickoff_at FROM canonical_fixture"))
                .hasToString("2026-08-16T12:00:00Z");
        assertThat(singleString("SELECT status FROM canonical_fixture")).isEqualTo("POSTPONED");
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-12T10:00:00Z");
        assertThat(singleString("SELECT last_authority_provider FROM canonical_fixture"))
                .isEqualTo("highlightly");
    }

    @Test
    void concurrentPrimaryProvidersConvergeOnOneFixtureWithTwoMappings() throws Exception {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        bindSyntheticPrimaryPeer(canonical);
        RawSnapshot highlightly = snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z");
        byte[] peerPayload = replaceInPayload(
                fixture("/fixtures/cat001/calendar-v2-football-data.json"),
                "\"provider\": \"football-data\"",
                "\"provider\": \"synthetic-primary-peer\"");
        RawSnapshot peer = snapshot(
                peerPayload,
                "synthetic-primary-peer",
                "competitions/upl/matches",
                "2026-08-11T10:05:01Z");

        List<NormalizationResult> results = runConcurrently(
                () -> normalizationService.normalize(highlightly),
                () -> normalizationService.normalize(peer));

        assertThat(results.stream().mapToInt(NormalizationResult::fixturesCreated).sum()).isEqualTo(1);
        assertThat(results.stream().mapToInt(result ->
                result.fixturesUnchanged() + result.fixturesBlocked()).sum()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(2);
        assertThat(count("canonical_season")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(2);
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(fixtureMappings()).isEqualTo(2);
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-11T10:05:00Z");
        assertThat(singleString("SELECT last_authority_provider FROM canonical_fixture"))
                .isEqualTo("synthetic-primary-peer");
        assertThat(jdbcClient.sql("""
                SELECT COUNT(DISTINCT canonical_entity_id)
                FROM provider_mapping
                WHERE entity_type = 'FIXTURE'
                """).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void concurrentCatalogGetOrCreateReturnsOneCompetitionAndOneTeamIdentity() throws Exception {
        List<UUID> competitionIds = runConcurrently(
                () -> commandService.registerCompetition(
                        "Concurrent League", "FRA", CompetitionType.DOMESTIC_LEAGUE),
                () -> commandService.registerCompetition(
                        "Concurrent League", "FRA", CompetitionType.DOMESTIC_LEAGUE));
        List<UUID> teamIds = runConcurrently(
                () -> commandService.registerTeam("Concurrent FC", "FRA"),
                () -> commandService.registerTeam("Concurrent FC", "FRA"));

        assertThat(competitionIds.get(0)).isEqualTo(competitionIds.get(1));
        assertThat(teamIds.get(0)).isEqualTo(teamIds.get(1));
        assertThat(count("canonical_competition")).isEqualTo(1);
        assertThat(count("canonical_team")).isEqualTo(1);
    }

    @Test
    void concurrentIncompatibleMappingInsertsKeepOneWinnerWithoutOverwrite() throws Exception {
        UUID confirmedTarget = commandService.registerTeam("Confirmed Concurrent FC", "FRA");
        Instant now = Instant.parse("2026-09-01T18:00:00Z");
        ProviderMapping confirmed = ProviderMapping.confirmed(
                "concurrent-provider",
                ProviderEntityType.TEAM,
                "concurrent-team-42",
                confirmedTarget,
                SEASON,
                PHASE,
                now);
        ProviderMapping ambiguous = ProviderMapping.ambiguous(
                "concurrent-provider",
                ProviderEntityType.TEAM,
                "concurrent-team-42",
                SEASON,
                PHASE,
                0.5,
                now);

        List<StoredProviderMapping> results = runConcurrently(
                () -> insertMappingInSeparateTransaction(confirmed),
                () -> insertMappingInSeparateTransaction(ambiguous));

        assertThat(results).extracting(StoredProviderMapping::inserted)
                .containsExactlyInAnyOrder(true, false);
        assertThat(results.get(0).mapping()).isEqualTo(results.get(1).mapping());
        ProviderMapping winner = results.get(0).mapping();
        assertThat(winner).isIn(confirmed, ambiguous);
        assertThat(jdbcClient.sql("""
                SELECT COUNT(*)
                FROM provider_mapping
                WHERE provider = 'concurrent-provider'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'concurrent-team-42'
                  AND season = :season
                  AND phase = :phase
                """)
                .param("season", SEASON)
                .param("phase", PHASE)
                .query(Long.class)
                .single()).isEqualTo(1);
        assertThat(singleString("""
                SELECT mapping_status
                FROM provider_mapping
                WHERE provider = 'concurrent-provider'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'concurrent-team-42'
                """)).isEqualTo(winner.status().name());
        UUID storedTarget = jdbcClient.sql("""
                SELECT canonical_entity_id
                FROM provider_mapping
                WHERE provider = 'concurrent-provider'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'concurrent-team-42'
                """)
                .query(UUID.class)
                .optional()
                .orElse(null);
        assertThat(storedTarget).isEqualTo(winner.status() == MappingStatus.CONFIRMED
                ? confirmedTarget
                : null);
    }

    private CanonicalIds registerCanonicalEntities() {
        UUID competitionId = commandService.registerCompetition(
                "Ukrainian Premier League", "UKR", CompetitionType.DOMESTIC_LEAGUE);
        UUID homeTeamId = commandService.registerTeam("FC Kryvbas Kryvyi Rih", "UKR");
        UUID awayTeamId = commandService.registerTeam("FC Livyi Bereh Kyiv", "UKR");
        return new CanonicalIds(competitionId, homeTeamId, awayTeamId);
    }

    private void bindHighlightly(CanonicalIds canonical) {
        insertConfirmedMapping(
                "highlightly", ProviderEntityType.COMPETITION, "hly-upl",
                canonical.competitionId(), SEASON, PHASE);
        insertConfirmedMapping(
                "highlightly", ProviderEntityType.TEAM, "hly-kryvbas",
                canonical.homeTeamId(), "", "");
        insertConfirmedMapping(
                "highlightly", ProviderEntityType.TEAM, "hly-livyi-bereh",
                canonical.awayTeamId(), "", "");
    }

    private void bindSyntheticPrimaryPeer(CanonicalIds canonical) {
        insertConfirmedMapping(
                "synthetic-primary-peer", ProviderEntityType.COMPETITION, "fd-upl",
                canonical.competitionId(), SEASON, PHASE);
        insertConfirmedMapping(
                "synthetic-primary-peer", ProviderEntityType.TEAM, "fd-kryvbas-77",
                canonical.homeTeamId(), "", "");
        insertConfirmedMapping(
                "synthetic-primary-peer", ProviderEntityType.TEAM, "fd-livyi-88",
                canonical.awayTeamId(), "", "");
    }

    private void insertConfirmedMapping(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            UUID canonicalEntityId,
            String season,
            String phase) {
        mappingRepository.insertIfAbsentAndResolve(ProviderMapping.confirmed(
                provider,
                entityType,
                providerEntityId,
                canonicalEntityId,
                season,
                phase,
                Instant.parse("2026-09-01T18:00:00Z")));
    }

    private RawSnapshot snapshot(String path, String provider, String endpoint, String receivedAt) throws IOException {
        return snapshot(fixture(path), provider, endpoint, receivedAt);
    }

    private RawSnapshot snapshot(byte[] payload, String provider, String endpoint, String receivedAt) {
        return RawSnapshot.capture(
                provider,
                endpoint,
                Instant.parse(receivedAt),
                payload,
                "cal01-replay-2");
    }

    private byte[] fixture(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test fixture: " + path);
            }
            return input.readAllBytes();
        }
    }

    private byte[] replaceInPayload(byte[] payload, String expected, String replacement) {
        String json = new String(payload, StandardCharsets.UTF_8);
        if (!json.contains(expected)) {
            throw new IllegalArgumentException("Fixture does not contain expected text: " + expected);
        }
        return json.replace(expected, replacement).getBytes(StandardCharsets.UTF_8);
    }

    private <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> firstFuture = executor.submit(awaitBarrierThen(barrier, first));
            Future<T> secondFuture = executor.submit(awaitBarrierThen(barrier, second));
            return List.of(
                    firstFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    secondFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private <T> Callable<T> awaitBarrierThen(CyclicBarrier barrier, Callable<T> task) {
        return () -> {
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return task.call();
        };
    }

    private StoredProviderMapping insertMappingInSeparateTransaction(ProviderMapping mapping) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> mappingRepository.insertIfAbsentAndResolve(mapping));
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private long fixtureMappings() {
        return jdbcClient.sql("SELECT COUNT(*) FROM provider_mapping WHERE entity_type = 'FIXTURE'")
                .query(Long.class)
                .single();
    }

    private long applicationOutcomeCount(String outcome) {
        return jdbcClient.sql("SELECT COUNT(*) FROM fixture_application_log WHERE outcome = :outcome")
                .param("outcome", outcome)
                .query(Long.class)
                .single();
    }

    private String singleString(String sql) {
        return jdbcClient.sql(sql)
                .query(String.class)
                .single();
    }

    private Instant singleInstant(String sql) {
        return jdbcClient.sql(sql)
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }

    private record CanonicalIds(UUID competitionId, UUID homeTeamId, UUID awayTeamId) {
    }
}
