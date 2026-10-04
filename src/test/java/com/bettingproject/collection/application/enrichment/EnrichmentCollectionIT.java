package com.bettingproject.collection.application.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.bettingproject.BettingProjectApplication;
import com.bettingproject.collection.application.RawSnapshotReader;
import com.bettingproject.collection.application.budget.BudgetCommands;
import com.bettingproject.collection.application.budget.ProviderBudgetAdministration;
import com.bettingproject.collection.application.budget.ProviderBudgetService;
import com.bettingproject.collection.application.capability.ConfiguredProviderCapabilityRegistry;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.application.enrichment.EnrichmentDispatchPlanner;
import com.bettingproject.collection.application.enrichment.EnrichmentQualityQueryService;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.collection.domain.budget.BudgetModel.Proof;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;
import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityEvidenceReference;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.CapabilityStatus;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepStatus;
import com.bettingproject.enrichment.domain.LineupAssessment;
import com.bettingproject.enrichment.domain.LineupSide;
import com.bettingproject.enrichment.domain.ObservedScalar;
import com.bettingproject.enrichment.domain.ObservedScalarType;
import com.bettingproject.enrichment.domain.ParsedEvents;
import com.bettingproject.enrichment.domain.ParsedMatchDetail;
import com.bettingproject.enrichment.domain.ParsedLineup;
import com.bettingproject.enrichment.domain.ParsedPlayerStatistics;
import com.bettingproject.enrichment.domain.ParsedTeamStatistics;
import com.bettingproject.enrichment.domain.ProviderTeamReference;
import com.bettingproject.operations.application.jobs.JobWorker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises audit-before-parse and network-free replay against PostgreSQL with a synthetic local client. */
@Testcontainers
class EnrichmentCollectionIT {
    private static final Instant NOW = Instant.parse("2030-08-10T12:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String PROVIDER = "highlightly";
    private static final String CONNECTOR_VERSION = "synthetic-highlightly-enrichment-v1";
    private static final String PARSER_VERSION = "synthetic-highlightly-detail-v1";
    private static final String PAYLOAD = "{\"synthetic\":true}";
    private static final ProviderCapabilityKey CALENDAR_KEY = new ProviderCapabilityKey(PROVIDER, "80778", "2030",
            "Regular Season - 1", CapabilityDataType.CALENDAR);
    private static final ProviderCapabilityKey DETAIL_KEY = new ProviderCapabilityKey(PROVIDER, "80778", "2030",
            "Regular Season - 1", CapabilityDataType.MATCH_DETAIL);
    private static final ProviderCapabilityKey LINEUP_KEY = new ProviderCapabilityKey(PROVIDER, "80778", "2030",
            "Regular Season - 1", CapabilityDataType.LINEUP);
    private static final ProviderCapabilityKey EVENTS_KEY = new ProviderCapabilityKey(PROVIDER, "80778", "2030",
            "Regular Season - 1", CapabilityDataType.EVENTS);
    private static final ProviderCapabilityKey TEAM_STATS_KEY = new ProviderCapabilityKey(PROVIDER, "80778", "2030",
            "Regular Season - 1", CapabilityDataType.TEAM_STATS);
    private static final ProviderCapabilityKey PLAYER_STATS_KEY = new ProviderCapabilityKey(PROVIDER, "80778", "2030",
            "Regular Season - 1", CapabilityDataType.PLAYER_STATS);
    private static final ProviderCapabilityKey FD_DETAIL_KEY = new ProviderCapabilityKey("football-data.org", "4001",
            "5001", "REGULAR_SEASON", CapabilityDataType.MATCH_DETAIL);
    private static final MutableClock TEST_CLOCK = new MutableClock(NOW);

    @Container private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");
    private static ConfigurableApplicationContext context;
    private static JdbcClient jdbc;
    private static Clock clock;
    private static final AtomicInteger workerSends = new AtomicInteger();
    private static final AtomicReference<Boolean> workerLineupAbsent = new AtomicReference<>(false);
    private static ProviderBudgetService budget;
    private static EnrichmentCollectionStore attempts;
    private static EnrichmentCollectionTransactions transactions;
    private static RawSnapshotReader snapshots;

    @BeforeAll
    static void start() {
        open();
    }

    private static void open() {
        open("control-api");
    }

    private static void open(String profile) {
        context = new SpringApplicationBuilder(BettingProjectApplication.class, TestClockConfiguration.class,
                TestDispatchConfiguration.class)
                .profiles(profile).web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run("--spring.datasource.url=" + POSTGRESQL.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRESQL.getUsername(),
                        "--spring.datasource.password=" + POSTGRESQL.getPassword(),
                        "--BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER", "--betting.operator.id=test-operator");
        jdbc = context.getBean(JdbcClient.class);
        clock = context.getBean(Clock.class);
        budget = context.getBean(ProviderBudgetService.class);
        attempts = context.getBean(EnrichmentCollectionStore.class);
        transactions = context.getBean(EnrichmentCollectionTransactions.class);
        snapshots = context.getBean(RawSnapshotReader.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void clean() {
        TEST_CLOCK.set(NOW);
        workerSends.set(0);
        workerLineupAbsent.set(false);
        jdbc.sql("TRUNCATE provider_budget_scope, canonical_competition, raw_snapshot, persistent_job, provider_call_audit CASCADE")
                .update();
    }

    @Test
    void oneBudgetedAttemptIsAuditedAndStoredBeforeParsingThenReplayedWithoutTransport() throws Exception {
        UUID budgetWindow = createBudgetWindow();
        FixtureSeed fixture = createCanonicalFixture();
        AdmissionSeed admission = createAdmission(fixture, budgetWindow);
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger parses = new AtomicInteger();
        AtomicReference<EnrichmentCollectionService> serviceReference = new AtomicReference<>();
        var command = new EnrichmentCollectionCommand(admission.admissionId(), EnrichmentPlanStepCode.DETAIL_AT_KICKOFF,
                EnrichmentFamily.MATCH_DETAIL, DETAIL_KEY, budgetWindow);

        EnrichmentProviderClient client = new EnrichmentProviderClient() {
            @Override public String provider() { return PROVIDER; }
            @Override public String connectorVersion() { return CONNECTOR_VERSION; }
            @Override public boolean available() { return true; }
            @Override public boolean supports(EnrichmentFamily family) { return family == EnrichmentFamily.MATCH_DETAIL; }
            @Override public EnrichmentProviderResponse fetch(EnrichmentProviderRequest request) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(request.provider()).isEqualTo(PROVIDER);
                assertThat(request.providerFixtureId()).isEqualTo(fixture.providerFixtureId());
                sends.incrementAndGet();
                if (sends.get() == 1) {
                    // Simulate a duplicate request arriving while the first authorized transport is in flight.
                    var duplicate = serviceReference.get().collect(command);
                    assertThat(duplicate.code()).isEqualTo(EnrichmentCollectionResult.Code.UNCERTAIN);
                    assertThat(duplicate.reasonCode()).isEqualTo("SEND_RESULT_UNKNOWN");
                    assertThat(attempts.findAttempt(duplicate.attemptId()).orElseThrow().state())
                            .isEqualTo(EnrichmentAttemptState.COMMITTED_FOR_SEND);
                }
                return new EnrichmentProviderResponse(NOW, NOW, 200,
                        PAYLOAD.getBytes(StandardCharsets.UTF_8), null, null);
            }
        };
        EnrichmentPayloadParser<ParsedMatchDetail> parser = new EnrichmentPayloadParser<>() {
            @Override public String provider() { return PROVIDER; }
            @Override public EnrichmentFamily family() { return EnrichmentFamily.MATCH_DETAIL; }
            @Override public String version() { return PARSER_VERSION; }
            @Override public ParsedMatchDetail parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
                assertThat(payload).isEqualTo(PAYLOAD.getBytes(StandardCharsets.UTF_8));
                assertThat(scheduledKickoff).isEqualTo(NOW);
                assertThat(receivedAt).isEqualTo(NOW);
                assertThat(jdbc.sql("SELECT count(*) FROM raw_snapshot WHERE provider = :provider AND endpoint = 'enrichment/matches' AND payload_sha256 = :hash")
                        .param("provider", PROVIDER).param("hash", SnapshotHasher.sha256(payload))
                        .query(Long.class).single()).isEqualTo(1);
                assertThat(jdbc.sql("SELECT count(*) FROM provider_call_audit WHERE provider = :provider "
                        + "AND logical_endpoint = 'enrichment/matches' AND http_status = 200 AND received_at IS NOT NULL")
                        .param("provider", PROVIDER).query(Long.class).single()).isEqualTo(1);
                parses.incrementAndGet();
                return new ParsedMatchDetail(fixture.providerFixtureId(), "80778",
                        ObservedScalar.of(ObservedScalarType.INTEGER, "2030"),
                        ObservedScalar.of(ObservedScalarType.TEXT, "Regular Season - 1"),
                        ObservedScalar.missing(), ObservedScalar.missing(), ObservedScalar.missing(), NOW,
                        ObservedScalar.of(ObservedScalarType.TEXT, "Not started"),
                        new ProviderTeamReference("92001", "Synthetic Home", ObservedScalar.missing()),
                        new ProviderTeamReference("92002", "Synthetic Away", ObservedScalar.missing()),
                        ObservedScalar.missing(), ObservedScalar.missing(),
                        EnrichmentObservationState.NOT_PRESENT, EnrichmentObservationState.NOT_PRESENT);
            }
        };

        EnrichmentCollectionService service = service(registry(), client, parser);
        serviceReference.set(service);

        var collected = service.collect(command);
        assertThat(collected.code()).as("reason=" + collected.reasonCode()).isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        assertThat(collected.observationIds()).hasSize(1);
        assertThat(sends).hasValue(1);
        assertThat(parses).hasValue(1);
        assertThat(budget.availability(budgetWindow).available()).isEqualTo(79);
        assertThat(count("provider_call_intent")).isEqualTo(1);
        assertThat(count("provider_call_audit")).isEqualTo(1);
        assertThat(count("enrichment_collection_attempt")).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(2); // one calendar provenance row plus the exact response bytes
        assertThat(count("provider_enrichment_observation")).isEqualTo(1);
        assertThat(count("enrichment_collection_derivation")).isEqualTo(1);
        assertThat(attempts.findAttempt(collected.attemptId()).orElseThrow().state())
                .isEqualTo(EnrichmentAttemptState.RECEIVED);
        UUID storedRawId = collected.rawSnapshotId();
        RawSnapshot raw = snapshots.find(storedRawId).orElseThrow();
        assertThat(raw.payload()).isEqualTo(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        assertThat(raw.sha256()).isEqualTo(SnapshotHasher.sha256(PAYLOAD.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.sql("SELECT logical_competition, logical_season, logical_phase, source_season_reference, source_phase_reference FROM provider_enrichment_observation")
                .query((rs, row) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5))).single())
                .containsExactly("PPL", "2030/2031", "LEAGUE", "2030", "Regular Season - 1");

        UUID findingId = UUID.randomUUID();
        UUID observationId = collected.observationIds().getFirst();
        jdbc.sql("""
                INSERT INTO enrichment_quality_finding(id, enrichment_observation_id, issue_code,
                    entity_scope, provider_entity_id, detected_at)
                VALUES (:id, :observation, 'MISSING_PLAYER_FULL_NAME', 'PLAYER',
                    'synthetic-player-private-id', :at)
                """)
                .param("id", findingId).param("observation", observationId)
                .param("at", NOW.atOffset(ZoneOffset.UTC)).update();
        TEST_CLOCK.set(NOW.plusSeconds(125));
        var dailyQuality = context.getBean(EnrichmentQualityQueryService.class).daily(
                LocalDate.of(2030, 8, 10));
        assertThat(dailyQuality.planPresent()).isTrue();
        assertThat(dailyQuality.fixtures()).hasSize(1);
        var fixtureQuality = dailyQuality.fixtures().getFirst();
        assertThat(fixtureQuality.canonicalFixtureId()).isEqualTo(fixture.canonicalFixtureId());
        assertThat(fixtureQuality.competitionName()).isEqualTo("Synthetic Primeira Liga");
        assertThat(fixtureQuality.season()).isEqualTo("2030/2031");
        assertThat(fixtureQuality.phase()).isEqualTo("LEAGUE");
        assertThat(fixtureQuality.steps()).hasSize(6);
        assertThat(fixtureQuality.availability()).hasSize(5);
        assertThat(fixtureQuality.availability()).filteredOn(item -> item.family() == EnrichmentFamily.MATCH_DETAIL)
                .singleElement().satisfies(item -> {
                    assertThat(item.state()).isEqualTo("AVAILABLE");
                    assertThat(item.observationId()).isEqualTo(observationId);
                    assertThat(item.rawSnapshotId()).isEqualTo(collected.rawSnapshotId());
                    assertThat(item.payloadSha256()).isEqualTo(raw.sha256());
                    assertThat(item.ageSeconds()).isEqualTo(125);
                });
        assertThat(fixtureQuality.availability()).filteredOn(item -> item.family() == EnrichmentFamily.LINEUP)
                .singleElement().satisfies(item -> assertThat(item.state()).isEqualTo("NOT_COLLECTED"));
        assertThat(fixtureQuality.findingCounts()).singleElement().satisfies(item -> {
            assertThat(item.issueCode()).isEqualTo("MISSING_PLAYER_FULL_NAME");
            assertThat(item.entityScope()).isEqualTo("PLAYER");
            assertThat(item.count()).isEqualTo(1);
        });
        assertThat(fixtureQuality.latestAttempts()).singleElement().satisfies(item -> {
            assertThat(item.logicalEndpoint()).isEqualTo("enrichment/matches");
            assertThat(item.state()).isEqualTo("RECEIVED");
            assertThat(item.rawSnapshotId()).isEqualTo(collected.rawSnapshotId());
        });
        String dailyJson = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(context.getBean(com.bettingproject.collection.adapter.web.control.EnrichmentQualityController.class))
                .setControllerAdvice(context.getBean(com.bettingproject.collection.adapter.web.control.CollectionProblemHandler.class))
                .build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/internal/collection/enrichment/daily").param("date", "2030-08-10"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dailyJson).contains("NOT_COLLECTED", "AVAILABLE", "MISSING_PLAYER_FULL_NAME", "rawSnapshotId")
                .doesNotContain("synthetic-player-private-id", PAYLOAD, "representationJson", "requestSha256",
                        "budgetIntentId", "auditId", "rawSnapshotPayload");

        var oldPool = context.getBean(com.zaxxer.hikari.HikariDataSource.class);
        context.close();
        assertThat(oldPool.isClosed()).isTrue();
        open();
        service = service(registry(), client, parser);
        var replayed = service.collect(command);
        assertThat(replayed.code()).isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        assertThat(replayed.attemptId()).isEqualTo(collected.attemptId());
        assertThat(sends).hasValue(1);
        assertThat(parses).hasValue(2);
        assertThat(count("provider_call_intent")).isEqualTo(1);
        assertThat(count("provider_call_audit")).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(2);
        assertThat(count("provider_enrichment_observation")).isEqualTo(1);
        assertThat(count("enrichment_collection_derivation")).isEqualTo(2);
        assertThat(budget.availability(budgetWindow).available()).isEqualTo(79);
        assertThat(service.replay(collected.attemptId()).code()).isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        assertThat(count("enrichment_collection_derivation")).isEqualTo(3);
        assertThat(sends).hasValue(1);
    }

    @Test
    void explicitFinalDetailArmsAndQueuesPostmatchAndPriorityRecheckFromTheStoredObservation() {
        UUID budgetWindow = createBudgetWindow();
        FixtureSeed fixture = createCanonicalFixture();
        AdmissionSeed admission = createAdmission(fixture, budgetWindow);
        EnrichmentProviderClient client = new EnrichmentProviderClient() {
            @Override public String provider() { return PROVIDER; }
            @Override public String connectorVersion() { return CONNECTOR_VERSION; }
            @Override public boolean available() { return true; }
            @Override public boolean supports(EnrichmentFamily family) { return family == EnrichmentFamily.MATCH_DETAIL; }
            @Override public EnrichmentProviderResponse fetch(EnrichmentProviderRequest request) {
                return new EnrichmentProviderResponse(NOW, NOW, 200,
                        PAYLOAD.getBytes(StandardCharsets.UTF_8), null, null);
            }
        };
        EnrichmentPayloadParser<ParsedMatchDetail> parser = new EnrichmentPayloadParser<>() {
            @Override public String provider() { return PROVIDER; }
            @Override public EnrichmentFamily family() { return EnrichmentFamily.MATCH_DETAIL; }
            @Override public String version() { return PARSER_VERSION; }
            @Override public ParsedMatchDetail parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
                return new ParsedMatchDetail(fixture.providerFixtureId(), "80778",
                        ObservedScalar.of(ObservedScalarType.INTEGER, "2030"),
                        ObservedScalar.of(ObservedScalarType.TEXT, "Regular Season - 1"),
                        ObservedScalar.missing(), ObservedScalar.missing(), ObservedScalar.missing(), NOW,
                        ObservedScalar.of(ObservedScalarType.TEXT, "Finished"),
                        new ProviderTeamReference("92001", "Synthetic Home", ObservedScalar.missing()),
                        new ProviderTeamReference("92002", "Synthetic Away", ObservedScalar.missing()),
                        ObservedScalar.missing(), ObservedScalar.missing(),
                        EnrichmentObservationState.NOT_PRESENT, EnrichmentObservationState.NOT_PRESENT);
            }
        };

        var result = service(registry(), client, parser).collect(new EnrichmentCollectionCommand(
                admission.admissionId(), EnrichmentPlanStepCode.DETAIL_AT_KICKOFF,
                EnrichmentFamily.MATCH_DETAIL, DETAIL_KEY, budgetWindow));

        assertThat(result.code()).as("reason=" + result.reasonCode()).isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        assertThat(result.observationIds()).hasSize(1);
        assertThat(jdbc.sql("SELECT step_code, status, trigger_evidence_observation_id, trigger_policy_version "
                + "FROM enrichment_plan_step WHERE admission_id = :admission AND step_code LIKE 'POSTMATCH%' ORDER BY step_code")
                .param("admission", admission.admissionId())
                .query((rs, row) -> List.of(rs.getString(1), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getString(4))).list())
                .containsExactly(
                        List.of("POSTMATCH_AFTER_FINAL", "QUEUED", result.observationIds().getFirst(), EnrichmentFinalStatusPolicy.VERSION),
                        List.of("POSTMATCH_RECHECK_FINAL_PLUS_60", "QUEUED", result.observationIds().getFirst(), EnrichmentFinalStatusPolicy.VERSION));
        assertThat(jdbc.sql("SELECT job_type, next_run_at FROM persistent_job WHERE id IN "
                + "(SELECT job_id FROM enrichment_job_input WHERE admission_id = :admission) ORDER BY job_type")
                .param("admission", admission.admissionId())
                .query((rs, row) -> List.of(rs.getString(1), rs.getObject(2, java.time.OffsetDateTime.class).toInstant())).list())
                .containsExactly(
                        List.of("POSTMATCH_ENRICHMENT", NOW),
                        List.of("POSTMATCH_RECHECK", NOW.plusSeconds(3600)));
        assertThat(jdbc.sql("SELECT family FROM enrichment_job_input_route route JOIN enrichment_job_input input "
                + "ON input.job_id = route.job_id WHERE input.admission_id = :admission ORDER BY family")
                .param("admission", admission.admissionId()).query(String.class).list())
                .containsExactly("MATCH_DETAIL", "MATCH_DETAIL");
    }

    @Test
    void workerStopsTheSecondLineupCheckpointAfterACompletePrematchObservation() {
        Instant base = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        TEST_CLOCK.set(base);
        UUID budgetWindow = createBudgetWindow(base);
        Instant kickoff = base.plusSeconds(1800);
        FixtureSeed fixture = createCanonicalFixture(kickoff, base);
        AdmissionSeed admission = createAdmission(fixture, budgetWindow, kickoff, false, base);
        context.getBean(EnrichmentDispatchPlanner.class).planAdmission(admission.admissionId());

        assertThat(jdbc.sql("SELECT step_code, status FROM enrichment_plan_step WHERE admission_id = :admission "
                + "AND step_code LIKE 'LINEUP%' ORDER BY step_code")
                .param("admission", admission.admissionId())
                .query((rs, row) -> List.of(rs.getString(1), rs.getString(2))).list())
                .containsExactly(List.of("LINEUP_T_MINUS_15", "QUEUED"), List.of("LINEUP_T_MINUS_30", "QUEUED"));

        context.close();
        try {
            open("batch-worker");
            JobWorker worker = context.getBean(JobWorker.class);
            assertThat(worker.tick()).isTrue();
            assertThat(workerSends).hasValue(1);
            assertThat(stepStatus(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_30))
                    .isEqualTo(EnrichmentPlanStepStatus.COMPLETED);
            assertThat(stepStatus(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_15))
                    .isEqualTo(EnrichmentPlanStepStatus.QUEUED);

            TEST_CLOCK.set(kickoff.minusSeconds(900));
            jdbc.sql("UPDATE persistent_job SET next_run_at = clock_timestamp() WHERE job_key = :key")
                    .param("key", "enrichment-step:" + stepId(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_15))
                    .update();
            assertThat(worker.tick()).isTrue();
            assertThat(workerSends).hasValue(1);
            assertThat(stepStatus(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_15))
                    .isEqualTo(EnrichmentPlanStepStatus.SKIPPED);
            assertThat(jdbc.sql("SELECT count(*) FROM enrichment_collection_attempt WHERE admission_id = :admission")
                    .param("admission", admission.admissionId()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("SELECT count(*) FROM provider_enrichment_observation WHERE admission_id = :admission "
                    + "AND family = 'LINEUP' AND observation_state = 'AVAILABLE' "
                    + "AND representation_json -> 'assessment' ->> 'status' = 'COMPLETE'")
                    .param("admission", admission.admissionId()).query(Long.class).single()).isEqualTo(1);
        }
        finally {
            context.close();
            open();
        }
    }

    @Test
    void absentFirstLineupDoesNotBlockTheSecondCheckpoint() {
        Instant base = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        TEST_CLOCK.set(base);
        UUID budgetWindow = createBudgetWindow(base);
        Instant kickoff = base.plusSeconds(1800);
        FixtureSeed fixture = createCanonicalFixture(kickoff, base);
        AdmissionSeed admission = createAdmission(fixture, budgetWindow, kickoff, false, base);
        context.getBean(EnrichmentDispatchPlanner.class).planAdmission(admission.admissionId());

        context.close();
        try {
            open("batch-worker");
            workerLineupAbsent.set(true);
            JobWorker worker = context.getBean(JobWorker.class);
            assertThat(worker.tick()).isTrue();
            assertThat(workerSends).hasValue(1);
            assertThat(stepStatus(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_30))
                    .isEqualTo(EnrichmentPlanStepStatus.COMPLETED);
            assertThat(jdbc.sql("SELECT observation_state FROM provider_enrichment_observation "
                    + "WHERE admission_id = :admission AND family = 'LINEUP'")
                    .param("admission", admission.admissionId()).query(String.class).single()).isEqualTo("EMPTY");

            workerLineupAbsent.set(false);
            TEST_CLOCK.set(kickoff.minusSeconds(900));
            jdbc.sql("UPDATE persistent_job SET next_run_at = clock_timestamp() WHERE job_key = :key")
                    .param("key", "enrichment-step:" + stepId(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_15))
                    .update();
            assertThat(worker.tick()).isTrue();
            assertThat(workerSends).hasValue(2);
            assertThat(stepStatus(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_15))
                    .isEqualTo(EnrichmentPlanStepStatus.COMPLETED);
            assertThat(jdbc.sql("SELECT observation_state FROM provider_enrichment_observation "
                    + "WHERE admission_id = :admission AND family = 'LINEUP' ORDER BY received_at")
                    .param("admission", admission.admissionId()).query(String.class).list())
                    .containsExactly("EMPTY", "AVAILABLE");
        }
        finally {
            workerLineupAbsent.set(false);
            context.close();
            open();
        }
    }

    @Test
    void lineupResponseReceivedAtKickoffIsStoredAsLateAndCannotSatisfyPrematchCompletion() throws IOException {
        UUID budgetWindow = createBudgetWindow();
        Instant kickoff = NOW.plusSeconds(60);
        FixtureSeed fixture = createCanonicalFixture(kickoff, NOW);
        AdmissionSeed admission = createAdmission(fixture, budgetWindow, kickoff, false, NOW);
        byte[] body = fixture("highlightly-lineup-complete.synthetic.json");
        AtomicInteger sends = new AtomicInteger();
        EnrichmentProviderClient client = new EnrichmentProviderClient() {
            @Override public String provider() { return PROVIDER; }
            @Override public String connectorVersion() { return CONNECTOR_VERSION; }
            @Override public boolean available() { return true; }
            @Override public boolean supports(EnrichmentFamily family) { return family == EnrichmentFamily.LINEUP; }
            @Override public EnrichmentProviderResponse fetch(EnrichmentProviderRequest request) {
                sends.incrementAndGet();
                TEST_CLOCK.set(kickoff);
                return new EnrichmentProviderResponse(NOW, kickoff, 200, body, null, null);
            }
        };
        var parser = new com.bettingproject.collection.adapter.replay.enrichment.StrictHighlightlyLineupParser();

        EnrichmentCollectionResult result = service(registry(), client, parser).collect(new EnrichmentCollectionCommand(
                admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_30,
                EnrichmentFamily.LINEUP, LINEUP_KEY, budgetWindow));

        assertThat(result.code()).isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        assertThat(sends).hasValue(1);
        assertThat(jdbc.sql("SELECT representation_json #>> '{assessment,status}' "
                + "FROM provider_enrichment_observation WHERE id = :id")
                .param("id", result.observationIds().getFirst()).query(String.class).single())
                .isEqualTo("COMPLETE_LATE");
        assertThat(context.getBean(EnrichmentPlanExecutionStore.class)
                .hasPrematchCompleteLineup(admission.admissionId(), kickoff)).isFalse();
    }

    @Test
    void directLineupCollectionAtKickoffIsRefusedBeforeBudgetReservationOrTransport() {
        UUID budgetWindow = createBudgetWindow();
        FixtureSeed fixture = createCanonicalFixture(NOW, NOW);
        AdmissionSeed admission = createAdmission(fixture, budgetWindow, NOW, false, NOW);
        AtomicInteger sends = new AtomicInteger();
        EnrichmentProviderClient client = new EnrichmentProviderClient() {
            @Override public String provider() { return PROVIDER; }
            @Override public String connectorVersion() { return CONNECTOR_VERSION; }
            @Override public boolean available() { return true; }
            @Override public boolean supports(EnrichmentFamily family) { return family == EnrichmentFamily.LINEUP; }
            @Override public EnrichmentProviderResponse fetch(EnrichmentProviderRequest request) {
                sends.incrementAndGet();
                return new EnrichmentProviderResponse(NOW, NOW, 200, new byte[] {'{', '}'}, null, null);
            }
        };
        var parser = new com.bettingproject.collection.adapter.replay.enrichment.StrictHighlightlyLineupParser();

        EnrichmentCollectionResult result = service(registry(), client, parser).collect(new EnrichmentCollectionCommand(
                admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_30,
                EnrichmentFamily.LINEUP, LINEUP_KEY, budgetWindow));

        assertThat(result.code()).isEqualTo(EnrichmentCollectionResult.Code.REFUSED);
        assertThat(result.reasonCode()).isEqualTo("LINEUP_PREMATCH_WINDOW_CLOSED");
        assertThat(sends).hasValue(0);
        assertThat(jdbc.sql("SELECT count(*) FROM provider_call_intent WHERE window_id = :window")
                .param("window", budgetWindow).query(Long.class).single()).isZero();
    }

    @Test
    void persistsPlayerQuarantinesAndHistoricalRegressionWithoutReplacingEarlierEvidence() throws Exception {
        UUID budgetWindow = createBudgetWindow();
        Instant kickoff = NOW.plusSeconds(1800);
        FixtureSeed fixture = createCanonicalFixture(kickoff, NOW);
        AdmissionSeed admission = createAdmission(fixture, budgetWindow, kickoff, true, NOW);
        byte[] lineupBody = fixture("highlightly-lineup-complete.synthetic.json");
        byte[] teamStatsBody = fixture("highlightly-statistics-reduced.synthetic.json");
        byte[] eventsBody = new String(fixture("highlightly-events.synthetic.json"), StandardCharsets.UTF_8)
                .replace("\"type\": \"Goal\"", "\"type\": \"Yellow Card\"")
                .replace("\"playerId\": 1109", "\"playerId\": 1199")
                .getBytes(StandardCharsets.UTF_8);
        byte[] playerStatsBody = new String(fixture("highlightly-box-score.synthetic.json"), StandardCharsets.UTF_8)
                .replace("\"id\": 1109", "\"id\": 1199")
                .replace("\"fullName\": \"Alpha Forward\"", "\"fullName\": \"Alpha 9\"")
                .replace("          \"cardsYellow\": 0,\n", "")
                .getBytes(StandardCharsets.UTF_8);
        AtomicInteger teamStatsCalls = new AtomicInteger();
        EnrichmentProviderClient client = new EnrichmentProviderClient() {
            @Override public String provider() { return PROVIDER; }
            @Override public String connectorVersion() { return CONNECTOR_VERSION; }
            @Override public boolean available() { return true; }
            @Override public boolean supports(EnrichmentFamily family) { return true; }
            @Override public EnrichmentProviderResponse fetch(EnrichmentProviderRequest request) {
                byte[] body = switch (request.family()) {
                    case LINEUP -> lineupBody;
                    case MATCH_DETAIL -> PAYLOAD.getBytes(StandardCharsets.UTF_8);
                    case EVENTS -> eventsBody;
                    case PLAYER_STATS -> playerStatsBody;
                    case TEAM_STATS -> teamStatsCalls.incrementAndGet() == 1
                            ? teamStatsBody : "[]".getBytes(StandardCharsets.UTF_8);
                };
                Instant responseAt = TEST_CLOCK.instant();
                return new EnrichmentProviderResponse(responseAt, responseAt, 200, body, null, null);
            }
        };
        EnrichmentPayloadParser<ParsedLineup> lineupParser =
                new com.bettingproject.collection.adapter.replay.enrichment.StrictHighlightlyLineupParser();
        EnrichmentPayloadParser<ParsedMatchDetail> detailParser = finalDetailParser(
                fixture.providerFixtureId(), "80778", "2030", "Regular Season - 1", kickoff, 2, 0);
        EnrichmentPayloadParser<ParsedEvents> eventsParser =
                new com.bettingproject.collection.adapter.replay.enrichment.StrictHighlightlyEventsParser();
        EnrichmentPayloadParser<ParsedPlayerStatistics> playerStatsParser =
                new com.bettingproject.collection.adapter.replay.enrichment.StrictHighlightlyPlayerStatisticsParser();
        EnrichmentPayloadParser<ParsedTeamStatistics> teamStatsParser =
                new com.bettingproject.collection.adapter.replay.enrichment.StrictHighlightlyTeamStatisticsParser();
        var service = service(registry(), client, List.<EnrichmentPayloadParser<?>>of(
                lineupParser, detailParser, eventsParser, playerStatsParser, teamStatsParser));

        TEST_CLOCK.set(NOW);
        assertThat(service.collect(command(admission, EnrichmentPlanStepCode.LINEUP_T_MINUS_30,
                EnrichmentFamily.LINEUP, LINEUP_KEY, budgetWindow)).code())
                .isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        TEST_CLOCK.set(kickoff);
        assertThat(service.collect(command(admission, EnrichmentPlanStepCode.DETAIL_AT_KICKOFF,
                EnrichmentFamily.MATCH_DETAIL, DETAIL_KEY, budgetWindow)).code())
                .isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);

        TEST_CLOCK.set(kickoff.plusSeconds(1));
        assertThat(service.collect(command(admission, EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL,
                EnrichmentFamily.EVENTS, EVENTS_KEY, budgetWindow)).code())
                .isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        TEST_CLOCK.set(kickoff.plusSeconds(2));
        assertThat(service.collect(command(admission, EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL,
                EnrichmentFamily.PLAYER_STATS, PLAYER_STATS_KEY, budgetWindow)).code())
                .isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        TEST_CLOCK.set(kickoff.plusSeconds(3));
        assertThat(service.collect(command(admission, EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL,
                EnrichmentFamily.TEAM_STATS, TEAM_STATS_KEY, budgetWindow)).code())
                .isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);

        TEST_CLOCK.set(kickoff.plusSeconds(3601));
        assertThat(service.collect(command(admission, EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60,
                EnrichmentFamily.TEAM_STATS, TEAM_STATS_KEY, budgetWindow)).code())
                .isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);

        assertThat(jdbc.sql("SELECT DISTINCT issue_code FROM enrichment_quality_finding "
                + "WHERE enrichment_observation_id IN (SELECT id FROM provider_enrichment_observation "
                + "WHERE canonical_fixture_id = :fixture)")
                .param("fixture", fixture.canonicalFixtureId()).query(String.class).list())
                .contains("MISSING_PLAYER_FULL_NAME", "ZERO_MINUTE_EXPECTED_METRICS",
                        "INVALID_SECOND_YELLOW_VALUE", "MISSING_PLAYER_YELLOW_CARD",
                        "CROSS_ENDPOINT_PLAYER_ID_MISMATCH", "EMPTY_RESPONSE_WITH_HISTORICAL_REGRESSION");
        assertThat(jdbc.sql("SELECT observation_state, count(*) FROM provider_enrichment_observation "
                + "WHERE canonical_fixture_id = :fixture AND family = 'TEAM_STATS' GROUP BY observation_state ORDER BY observation_state")
                .param("fixture", fixture.canonicalFixtureId())
                .query((rs, row) -> List.of(rs.getString(1), rs.getLong(2))).list())
                .containsExactly(List.of("AVAILABLE", 1L), List.of("EMPTY", 1L));
        assertThat(jdbc.sql("SELECT representation_json #>> '{teams,0,providerTeamId}' "
                + "FROM provider_enrichment_observation WHERE canonical_fixture_id = :fixture "
                + "AND family = 'TEAM_STATS' AND observation_state = 'AVAILABLE'")
                .param("fixture", fixture.canonicalFixtureId()).query(String.class).single()).isEqualTo("1002");
        assertThat(jdbc.sql("SELECT count(*) FROM enrichment_quality_finding finding "
                + "JOIN provider_enrichment_observation observation ON observation.id = finding.enrichment_observation_id "
                + "WHERE observation.canonical_fixture_id = :fixture "
                + "AND finding.issue_code = 'EMPTY_RESPONSE_WITH_HISTORICAL_REGRESSION'")
                .param("fixture", fixture.canonicalFixtureId()).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void finalScoreConflictAcrossProvidersIsStoredWithoutReplacingThePrimaryObservation() throws Exception {
        UUID highlightlyWindow = createBudgetWindow();
        UUID footballDataWindow = createCadenceBudgetWindow("football-data.org");
        Instant kickoff = NOW.plusSeconds(60);
        FixtureSeed fixture = createCanonicalFixture(kickoff, NOW);
        String footballDataFixtureId = "800001";
        jdbc.sql("""
                INSERT INTO provider_mapping(id, provider, entity_type, provider_entity_id, canonical_entity_id,
                    season, phase, confidence, mapping_status, created_at, updated_at, version)
                VALUES (:id, 'football-data.org', 'FIXTURE', :providerFixture, :fixture,
                    '5001', 'REGULAR_SEASON', 1.0000, 'CONFIRMED', :now, :now, 1)
                """)
                .param("id", UUID.randomUUID()).param("providerFixture", footballDataFixtureId)
                .param("fixture", fixture.canonicalFixtureId()).param("now", NOW.atOffset(ZoneOffset.UTC)).update();
        AdmissionSeed admission = createAdmission(fixture, highlightlyWindow, kickoff, true, NOW);
        byte[] highlightlyBody = new String(fixture("highlightly-detail-prematch.synthetic.json"), StandardCharsets.UTF_8)
                .replace("\"id\": 900001", "\"id\": " + fixture.providerFixtureId())
                .replace("\"id\": 2001", "\"id\": 80778")
                .replace("\"season\": 2026", "\"season\": 2030")
                .replace("\"round\": \"Final\"", "\"round\": \"Regular Season - 1\"")
                .replace("2026-01-15T19:00:00Z", kickoff.toString())
                .replace("\"description\": \"Not started\"", "\"description\": \"Finished\"")
                .replace("\"current\": null", "\"current\": {\"home\": 2, \"away\": 0}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] footballDataBody = new String(fixture("football-data-detail.synthetic.json"), StandardCharsets.UTF_8)
                .replace("\"id\": 800001", "\"id\": " + footballDataFixtureId)
                .replace("2026-01-15T19:00:00Z", kickoff.toString())
                .replace("\"status\": \"TIMED\"", "\"status\": \"FINISHED\"")
                .replace("\"fullTime\": {\"home\": null, \"away\": null}",
                        "\"fullTime\": {\"home\": 1, \"away\": 0}")
                .getBytes(StandardCharsets.UTF_8);
        EnrichmentProviderClient highlightly = syntheticDetailClient(PROVIDER, highlightlyBody);
        EnrichmentProviderClient footballData = syntheticDetailClient("football-data.org", footballDataBody);
        EnrichmentPayloadParser<ParsedMatchDetail> highlightlyParser =
                new com.bettingproject.collection.adapter.replay.enrichment.StrictHighlightlyDetailParser();
        EnrichmentPayloadParser<ParsedMatchDetail> footballDataParser =
                new com.bettingproject.collection.adapter.replay.enrichment.StrictFootballDataDetailParser();
        var service = service(registry(), List.of(highlightly, footballData),
                List.<EnrichmentPayloadParser<?>>of(highlightlyParser, footballDataParser));

        TEST_CLOCK.set(kickoff);
        var primary = service.collect(command(admission, EnrichmentPlanStepCode.DETAIL_AT_KICKOFF,
                EnrichmentFamily.MATCH_DETAIL, DETAIL_KEY, highlightlyWindow));
        assertThat(primary.code()).isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);
        TEST_CLOCK.set(kickoff.plusSeconds(1));
        var control = service.collect(command(admission, EnrichmentPlanStepCode.DETAIL_AT_KICKOFF,
                EnrichmentFamily.MATCH_DETAIL, FD_DETAIL_KEY, footballDataWindow));
        assertThat(control.code()).as("reason=" + control.reasonCode())
                .isEqualTo(EnrichmentCollectionResult.Code.REPLAYED);

        assertThat(jdbc.sql("SELECT issue_code FROM enrichment_quality_finding finding "
                + "JOIN provider_enrichment_observation observation ON observation.id = finding.enrichment_observation_id "
                + "WHERE observation.id = :observation")
                .param("observation", control.observationIds().getFirst()).query(String.class).list())
                .containsExactly("FINAL_SCORE_CONFLICT");
        assertThat(jdbc.sql("SELECT representation_json #>> '{sourceHomeScore,value}' "
                + "FROM provider_enrichment_observation WHERE id = :id")
                .param("id", primary.observationIds().getFirst()).query(String.class).single()).isEqualTo("2");
        assertThat(jdbc.sql("SELECT representation_json #>> '{sourceHomeScore,value}' "
                + "FROM provider_enrichment_observation WHERE id = :id")
                .param("id", control.observationIds().getFirst()).query(String.class).single()).isEqualTo("1");
        assertThat(jdbc.sql("SELECT last_authority_provider FROM canonical_fixture WHERE id = :id")
                .param("id", fixture.canonicalFixtureId()).query(String.class).single()).isEqualTo(PROVIDER);
    }

    @Test
    void workerMarksElapsedLineupWindowsMissedWithoutBudgetReservationOrProviderCall() {
        Instant base = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        TEST_CLOCK.set(base);
        UUID budgetWindow = createBudgetWindow(base);
        Instant kickoff = base.plusSeconds(1800);
        FixtureSeed fixture = createCanonicalFixture(kickoff, base);
        AdmissionSeed admission = createAdmission(fixture, budgetWindow, kickoff, false, base);
        context.getBean(EnrichmentDispatchPlanner.class).planAdmission(admission.admissionId());

        context.close();
        try {
            open("batch-worker");
            TEST_CLOCK.set(kickoff.plusSeconds(1));
            for (EnrichmentPlanStepCode code : List.of(
                    EnrichmentPlanStepCode.LINEUP_T_MINUS_30, EnrichmentPlanStepCode.LINEUP_T_MINUS_15)) {
                jdbc.sql("UPDATE persistent_job SET next_run_at = clock_timestamp() WHERE job_key = :key")
                        .param("key", "enrichment-step:" + stepId(admission.admissionId(), code)).update();
            }

            JobWorker worker = context.getBean(JobWorker.class);
            assertThat(worker.tick()).isTrue();
            assertThat(worker.tick()).isTrue();
            assertThat(stepStatus(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_30))
                    .isEqualTo(EnrichmentPlanStepStatus.MISSED_WINDOW);
            assertThat(stepStatus(admission.admissionId(), EnrichmentPlanStepCode.LINEUP_T_MINUS_15))
                    .isEqualTo(EnrichmentPlanStepStatus.MISSED_WINDOW);
            assertThat(jdbc.sql("SELECT count(*) FROM enrichment_collection_attempt WHERE admission_id = :admission")
                    .param("admission", admission.admissionId()).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM provider_call_intent WHERE window_id = :window")
                    .param("window", budgetWindow).query(Long.class).single()).isZero();
            assertThat(workerSends).hasValue(0);
        }
        finally {
            context.close();
            open();
        }
    }

    private EnrichmentPlanStepStatus stepStatus(UUID admissionId, EnrichmentPlanStepCode code) {
        String status = jdbc.sql("SELECT status FROM enrichment_plan_step WHERE admission_id = :admission AND step_code = :code")
                .param("admission", admissionId).param("code", code.name()).query(String.class).single();
        return EnrichmentPlanStepStatus.valueOf(status);
    }

    private UUID stepId(UUID admissionId, EnrichmentPlanStepCode code) {
        return jdbc.sql("SELECT id FROM enrichment_plan_step WHERE admission_id = :admission AND step_code = :code")
                .param("admission", admissionId).param("code", code.name()).query(UUID.class).single();
    }

    private EnrichmentCollectionService service(ProviderCapabilityRegistry registry,
            EnrichmentProviderClient client, EnrichmentPayloadParser<?> parser) {
        return service(registry, client, List.of(parser));
    }

    private EnrichmentCollectionService service(ProviderCapabilityRegistry registry,
            EnrichmentProviderClient client, List<EnrichmentPayloadParser<?>> parsers) {
        return service(registry, List.of(client), parsers);
    }

    private EnrichmentCollectionService service(ProviderCapabilityRegistry registry,
            List<EnrichmentProviderClient> clients, List<EnrichmentPayloadParser<?>> parsers) {
        var derivations = new EnrichmentDerivationService(attempts, snapshots, transactions, parsers,
                new EnrichmentFinalStatusPolicy(), context.getBean(EnrichmentQualityEvidenceReader.class), clock);
        return new EnrichmentCollectionService(registry, attempts, transactions, derivations,
                clients, parsers, clock);
    }

    private static EnrichmentPayloadParser<ParsedMatchDetail> finalDetailParser(String fixtureId,
            String competitionId, String sourceSeason, String sourcePhase, Instant kickoff,
            int homeScore, int awayScore) {
        return new EnrichmentPayloadParser<>() {
            @Override public String provider() { return PROVIDER; }
            @Override public EnrichmentFamily family() { return EnrichmentFamily.MATCH_DETAIL; }
            @Override public String version() { return "synthetic-enrichment-final-detail-v1"; }
            @Override public ParsedMatchDetail parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
                return new ParsedMatchDetail(fixtureId, competitionId,
                        ObservedScalar.of(ObservedScalarType.INTEGER, sourceSeason),
                        ObservedScalar.of(ObservedScalarType.TEXT, sourcePhase),
                        ObservedScalar.missing(), ObservedScalar.missing(), ObservedScalar.missing(), kickoff,
                        ObservedScalar.of(ObservedScalarType.TEXT, "Finished"),
                        new ProviderTeamReference("92001", "Synthetic Home", ObservedScalar.missing()),
                        new ProviderTeamReference("92002", "Synthetic Away", ObservedScalar.missing()),
                        ObservedScalar.of(ObservedScalarType.INTEGER, Integer.toString(homeScore)),
                        ObservedScalar.of(ObservedScalarType.INTEGER, Integer.toString(awayScore)),
                        EnrichmentObservationState.NOT_PRESENT, EnrichmentObservationState.NOT_PRESENT);
            }
        };
    }

    private EnrichmentProviderClient syntheticDetailClient(String provider, byte[] body) {
        return new EnrichmentProviderClient() {
            @Override public String provider() { return provider; }
            @Override public String connectorVersion() { return CONNECTOR_VERSION; }
            @Override public boolean available() { return true; }
            @Override public boolean supports(EnrichmentFamily family) { return family == EnrichmentFamily.MATCH_DETAIL; }
            @Override public EnrichmentProviderResponse fetch(EnrichmentProviderRequest request) {
                Instant responseAt = TEST_CLOCK.instant();
                return new EnrichmentProviderResponse(responseAt, responseAt, 200, body, null, null);
            }
        };
    }

    private static EnrichmentCollectionCommand command(AdmissionSeed admission, EnrichmentPlanStepCode step,
            EnrichmentFamily family, ProviderCapabilityKey capability, UUID budgetWindow) {
        return new EnrichmentCollectionCommand(admission.admissionId(), step, family, capability, budgetWindow);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream input = EnrichmentCollectionIT.class.getResourceAsStream("/fixtures/enr001/" + name)) {
            if (input == null) { throw new IllegalStateException("Missing synthetic enrichment fixture"); }
            return input.readAllBytes();
        }
    }

    private UUID createBudgetWindow() {
        return createBudgetWindow(NOW);
    }

    private UUID createBudgetWindow(Instant referenceTime) {
        UUID window = UUID.randomUUID();
        var command = new BudgetCommands.Initialize(window, PROVIDER, "synthetic-enrichment-" + UUID.randomUUID(),
                referenceTime.minusSeconds(3600), referenceTime.plusSeconds(86_400), 100L, 80, 20,
                0, 0, null, null, 100L, referenceTime.minusSeconds(1), referenceTime.plusSeconds(86_400),
                new Proof("enr-002-synthetic-budget", HASH), "synthetic integration test");
        assertThat(context.getBean(ProviderBudgetAdministration.class).initialize(command).code())
                .isEqualTo(ResultCode.OK);
        return window;
    }

    private UUID createCadenceBudgetWindow(String provider) {
        UUID window = UUID.randomUUID();
        assertThat(context.getBean(ProviderBudgetAdministration.class).initialize(new BudgetCommands.Initialize(
                window, provider, "synthetic-enrichment-cadence-" + UUID.randomUUID(), NOW.minusSeconds(3600),
                NOW.plusSeconds(86_400), null, 100, 0, 0, 0, 100,
                java.time.Duration.ofMinutes(1), null, null, null,
                new Proof("enr-002-synthetic-cadence", HASH), "synthetic cadence test")).code())
                .isEqualTo(ResultCode.OK);
        return window;
    }

    private FixtureSeed createCanonicalFixture() {
        return createCanonicalFixture(NOW);
    }

    private FixtureSeed createCanonicalFixture(Instant kickoff) {
        return createCanonicalFixture(kickoff, NOW);
    }

    private FixtureSeed createCanonicalFixture(Instant kickoff, Instant observedAt) {
        String providerFixtureId = Long.toString(Math.max(1L, UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE));
        UUID competition = UUID.randomUUID();
        UUID home = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        UUID season = UUID.randomUUID();
        UUID fixture = UUID.randomUUID();
        UUID rawCalendar = UUID.randomUUID();
        UUID authorityObservation = UUID.randomUUID();
        byte[] provenance = "{\"syntheticCalendar\":true}".getBytes(StandardCharsets.UTF_8);
        String provenanceHash = SnapshotHasher.sha256(provenance);

        jdbc.sql("INSERT INTO canonical_competition(id, canonical_name, country_code, competition_type, created_at, updated_at) VALUES (:id, 'Synthetic Primeira Liga', 'PT', 'DOMESTIC_LEAGUE', :now, :now)")
                .param("id", competition).param("now", observedAt.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO canonical_team(id, canonical_name, country_code, created_at, updated_at) VALUES (:id, :name, NULL, :now, :now)")
                .param("id", home).param("name", "Synthetic Home " + home).param("now", observedAt.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO canonical_team(id, canonical_name, country_code, created_at, updated_at) VALUES (:id, :name, NULL, :now, :now)")
                .param("id", away).param("name", "Synthetic Away " + away).param("now", observedAt.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO canonical_season(id, competition_id, season_label, created_at, updated_at) VALUES (:id, :competition, '2030/2031', :now, :now)")
                .param("id", season).param("competition", competition).param("now", observedAt.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("""
                INSERT INTO canonical_fixture(id, competition_id, home_team_id, away_team_id, kickoff_at,
                    status, created_at, updated_at, season_id, phase, neutral_venue, participants_unordered)
                VALUES (:id, :competition, :home, :away, :kickoff, 'SCHEDULED', :now, :now,
                    :season, 'LEAGUE', NULL, false)
                """)
                .param("id", fixture).param("competition", competition).param("home", home).param("away", away)
                .param("kickoff", kickoff.atOffset(ZoneOffset.UTC)).param("now", observedAt.atOffset(ZoneOffset.UTC))
                .param("season", season).update();
        jdbc.sql("""
                INSERT INTO raw_snapshot(id, provider, endpoint, requested_at, received_at, http_status,
                    payload_sha256, payload_compression, payload, connector_version)
                VALUES (:id, :provider, 'calendar/discovery', :now, :now, 200, :hash, 'identity', :payload, 'synthetic-calendar-v1')
                """)
                .param("id", rawCalendar).param("provider", PROVIDER).param("now", observedAt.atOffset(ZoneOffset.UTC))
                .param("hash", provenanceHash).param("payload", provenance).update();
        jdbc.sql("""
                INSERT INTO fixture_observation(id, raw_snapshot_id, canonical_fixture_id, provider,
                    provider_fixture_id, provider_competition_id, provider_home_team_id, provider_away_team_id,
                    source_kickoff_at, source_status, source_phase, normalization_status, reason_code,
                    observed_at, created_at, source_schema_version, source_season, source_neutral_venue,
                    source_participants_unordered)
                VALUES (:id, :raw, :fixture, :provider, :fixtureRef, '80778', '92001', '92002',
                    :kickoff, 'SCHEDULED', 'Regular Season - 1', 'NORMALIZED', NULL,
                    :now, :now, 'cal01-fixture-v3', '2030', NULL, false)
                """)
                .param("id", authorityObservation).param("raw", rawCalendar).param("fixture", fixture)
                .param("provider", PROVIDER).param("fixtureRef", providerFixtureId)
                .param("kickoff", kickoff.atOffset(ZoneOffset.UTC)).param("now", observedAt.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("""
                UPDATE canonical_fixture SET last_authority_observation_id = :observation,
                    last_authority_observed_at = :now, last_authority_provider = :provider,
                    last_authority_policy_version = :policy WHERE id = :fixture
                """)
                .param("observation", authorityObservation).param("now", observedAt.atOffset(ZoneOffset.UTC))
                .param("provider", PROVIDER).param("policy", HASH).param("fixture", fixture).update();
        jdbc.sql("""
                INSERT INTO provider_mapping(id, provider, entity_type, provider_entity_id, canonical_entity_id,
                    season, phase, confidence, mapping_status, created_at, updated_at, version)
                VALUES (:id, :provider, 'FIXTURE', :fixtureRef, :fixture, '2030', 'Regular Season - 1',
                    1.0000, 'CONFIRMED', :now, :now, 1)
                """)
                .param("id", UUID.randomUUID()).param("provider", PROVIDER).param("fixtureRef", providerFixtureId)
                .param("fixture", fixture)
                .param("now", observedAt.atOffset(ZoneOffset.UTC)).update();
        return new FixtureSeed(fixture, providerFixtureId);
    }

    private AdmissionSeed createAdmission(FixtureSeed fixture, UUID budgetWindow) {
        return createAdmission(fixture, budgetWindow, NOW, true);
    }

    private AdmissionSeed createAdmission(FixtureSeed fixture, UUID budgetWindow, Instant kickoff, boolean priority) {
        return createAdmission(fixture, budgetWindow, kickoff, priority, NOW);
    }

    private AdmissionSeed createAdmission(FixtureSeed fixture, UUID budgetWindow, Instant kickoff,
            boolean priority, Instant createdAt) {
        UUID plan = UUID.randomUUID();
        UUID admission = UUID.randomUUID();
        String key = "enr-002-synthetic-plan-" + UUID.randomUUID();
        LocalDate day = kickoff.atZone(ZoneOffset.UTC).toLocalDate();
        jdbc.sql("""
                INSERT INTO enrichment_daily_plan(id, idempotency_key, command_sha256, budget_window_id,
                    competition_date, registry_sha256, estimated_calls_per_fixture, selected_fixture_count,
                    estimated_calls, evaluated_at, created_at)
                VALUES (:id, :key, :hash, :window, :day, :registry, 1, 1, 1, :now, :now)
                """)
                .param("id", plan).param("key", key).param("hash", HASH).param("window", budgetWindow)
                .param("day", day).param("registry", HASH).param("now", createdAt.atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("""
                INSERT INTO enrichment_daily_admission(id, plan_id, canonical_fixture_id, admission_order,
                    priority, kickoff_at, estimated_calls, created_at)
                VALUES (:id, :plan, :fixture, 1, :priority, :kickoff, 1, :now)
                """)
                .param("id", admission).param("plan", plan).param("fixture", fixture.canonicalFixtureId())
                .param("priority", priority).param("kickoff", kickoff.atOffset(ZoneOffset.UTC))
                .param("now", createdAt.atOffset(ZoneOffset.UTC)).update();
        List<StepSeed> plannedSteps = new java.util.ArrayList<>(List.of(
                new StepSeed(EnrichmentPlanStepCode.LINEUP_T_MINUS_30, "LINEUP", kickoff.minusSeconds(1800), "LINEUP_NOT_COMPLETE"),
                new StepSeed(EnrichmentPlanStepCode.LINEUP_T_MINUS_15, "LINEUP", kickoff.minusSeconds(900), "LINEUP_NOT_COMPLETE"),
                new StepSeed(EnrichmentPlanStepCode.DETAIL_AT_KICKOFF, "DETAIL", kickoff, "SCHEDULED_KICKOFF_WINDOW"),
                new StepSeed(EnrichmentPlanStepCode.DETAIL_PLUS_45, "DETAIL", kickoff.plusSeconds(2700), "SCHEDULED_KICKOFF_WINDOW"),
                new StepSeed(EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL, "POSTMATCH", null, "EXPLICIT_FINAL_STATUS")));
        if (priority) {
            plannedSteps.add(new StepSeed(EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60,
                    "POSTMATCH_RECHECK", null, "PRIORITY_AND_FINAL_STATUS"));
        }
        UUID detailStepId = null;
        for (StepSeed planned : plannedSteps) {
            UUID stepId = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO enrichment_plan_step(id, admission_id, step_code, family_scope, scheduled_at,
                        condition_code, trigger_observed_at, trigger_evidence_observation_id, status, created_at)
                    VALUES (:id, :admission, :code, :family, :scheduled, :condition, NULL, NULL, 'PLANNED', :created)
                    """)
                    .param("id", stepId).param("admission", admission).param("code", planned.code().name())
                    .param("family", planned.familyScope()).param("scheduled", planned.scheduledAt() == null
                            ? null : planned.scheduledAt().atOffset(ZoneOffset.UTC))
                    .param("condition", planned.conditionCode()).param("created", createdAt.atOffset(ZoneOffset.UTC)).update();
            if (planned.code() == EnrichmentPlanStepCode.DETAIL_AT_KICKOFF) { detailStepId = stepId; }
        }
        return new AdmissionSeed(admission, detailStepId);
    }

    private static ProviderCapabilityRegistry registry() {
        var entries = List.of(capability(CALENDAR_KEY, CapabilityDataType.CALENDAR),
                capability(DETAIL_KEY, CapabilityDataType.MATCH_DETAIL), capability(LINEUP_KEY, CapabilityDataType.LINEUP),
                capability(EVENTS_KEY, CapabilityDataType.EVENTS), capability(TEAM_STATS_KEY, CapabilityDataType.TEAM_STATS),
                capability(PLAYER_STATS_KEY, CapabilityDataType.PLAYER_STATS),
                capability(FD_DETAIL_KEY, CapabilityDataType.MATCH_DETAIL, CapabilityStatus.CONTROL,
                        CapabilityAuthorityRole.CONTROL));
        return new ConfiguredProviderCapabilityRegistry("synthetic-enr-002-v1", HASH, entries);
    }

    private static ProviderCapability capability(ProviderCapabilityKey key, CapabilityDataType dataType) {
        return capability(key, dataType, CapabilityStatus.PRIMARY, CapabilityAuthorityRole.PRIMARY);
    }

    private static ProviderCapability capability(ProviderCapabilityKey key, CapabilityDataType dataType,
            CapabilityStatus status, CapabilityAuthorityRole role) {
        return new ProviderCapability(key,
                new CapabilityRouteKey("PPL", "2030/2031", "LEAGUE", dataType),
                status, role, true,
                List.of(new CapabilityEvidenceReference("synthetic-enr-002-test", NOW, HASH)));
    }

    private static long count(String table) {
        // Table names are private test constants, not external input.
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private record FixtureSeed(UUID canonicalFixtureId, String providerFixtureId) { }
    private record AdmissionSeed(UUID admissionId, UUID stepId) { }
    private record StepSeed(EnrichmentPlanStepCode code, String familyScope, Instant scheduledAt, String conditionCode) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean @Primary Clock syntheticEnrichmentClock() { return TEST_CLOCK; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDispatchConfiguration {
        @Bean @Primary ProviderCapabilityRegistry syntheticDispatchRegistry() {
            return registry();
        }

        @Bean @Order(Ordered.HIGHEST_PRECEDENCE)
        EnrichmentProviderClient syntheticDispatchClient() {
            return new EnrichmentProviderClient() {
                @Override public String provider() { return PROVIDER; }
                @Override public String connectorVersion() { return CONNECTOR_VERSION; }
                @Override public boolean available() { return true; }
                @Override public boolean supports(EnrichmentFamily family) {
                    return family == EnrichmentFamily.MATCH_DETAIL || family == EnrichmentFamily.LINEUP;
                }
                @Override public EnrichmentProviderResponse fetch(EnrichmentProviderRequest request) {
                    workerSends.incrementAndGet();
                    Instant responseAt = TEST_CLOCK.instant();
                    String body = request.family() == EnrichmentFamily.LINEUP && workerLineupAbsent.get()
                            ? "{\"syntheticAbsent\":true}" : PAYLOAD;
                    return new EnrichmentProviderResponse(responseAt, responseAt, 200,
                            body.getBytes(StandardCharsets.UTF_8), null, null);
                }
            };
        }

        @Bean @Order(Ordered.HIGHEST_PRECEDENCE)
        EnrichmentPayloadParser<ParsedLineup> syntheticLineupParser() {
            return new EnrichmentPayloadParser<>() {
                @Override public String provider() { return PROVIDER; }
                @Override public EnrichmentFamily family() { return EnrichmentFamily.LINEUP; }
                @Override public String version() { return "synthetic-worker-lineup-v1"; }
                @Override public ParsedLineup parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
                    if (new String(payload, StandardCharsets.UTF_8).contains("syntheticAbsent")) {
                        LineupSide home = new LineupSide(true, List.of());
                        LineupSide away = new LineupSide(true, List.of());
                        LineupAssessment assessment = LineupAssessment.assess(home, away, scheduledKickoff, receivedAt);
                        return new ParsedLineup(EnrichmentObservationState.EMPTY, "92001", home, "92002", away,
                                assessment, receivedAt);
                    }
                    List<String> homeIds = java.util.stream.IntStream.range(0, 11)
                            .mapToObj(index -> "home-player-" + index).toList();
                    List<String> awayIds = java.util.stream.IntStream.range(0, 11)
                            .mapToObj(index -> "away-player-" + index).toList();
                    LineupSide home = new LineupSide(true, homeIds);
                    LineupSide away = new LineupSide(true, awayIds);
                    LineupAssessment assessment = LineupAssessment.assess(home, away, scheduledKickoff, receivedAt);
                    return new ParsedLineup(EnrichmentObservationState.AVAILABLE, "92001", home, "92002", away,
                            assessment, receivedAt);
                }
            };
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant current;
        private MutableClock(Instant initial) { current = initial; }
        private void set(Instant value) { current = value; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) { throw new IllegalArgumentException("test clock is UTC only"); }
            return this;
        }
        @Override public Instant instant() { return current; }
    }
}
