package com.bettingproject.catalog.application;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                "betting.operator.id=test-operator"
        })
@ActiveProfiles("control-api")
@Import(StoredSnapshotReplayIT.AuthorityConfiguration.class)
class StoredSnapshotReplayIT {

    private static final String PROVIDER = "highlightly";
    private static final String PROVIDER_COMPETITION_ID = "hly-upl";
    private static final String PROVIDER_HOME_TEAM_ID = "hly-kryvbas";
    private static final String PROVIDER_AWAY_TEAM_ID = "hly-livyi-bereh";
    private static final String SEASON = "2026/2027";
    private static final String PHASE = "REGULAR_SEASON";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private CatalogCommandService catalogCommandService;

    @Autowired
    private ProviderMappingRepository mappingRepository;

    @Autowired
    private CalendarNormalizationService normalizationService;

    @Autowired
    private NormalizationReplayRequestService requestService;

    @Autowired
    private NormalizationReplayExecutionService executionService;

    @Autowired
    private NormalizationReplayRequestRepository requestRepository;

    @Autowired
    private NormalizationReplayAttemptJournal attemptJournal;

    @Autowired
    private MappingDecisionService mappingDecisionService;

    @Autowired
    private ControlCommandReceiptStore receiptStore;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
                "normalization_replay_attempt_anomaly_event",
                "normalization_replay_attempt_application",
                "normalization_replay_attempt",
                "normalization_replay_request",
                "provider_mapping_decision_anomaly",
                "provider_mapping_decision",
                "control_command_receipt",
                "normalization_anomaly_event",
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
    void requestBySnapshotIdExecutesAfterCommitAndCorrelatesItsApplication()
            throws IOException {
        StoredFixture stored = prepareNormalizedFixture();

        NormalizationReplayRequestResult result = requestService.request(
                new NormalizationReplayCommand(
                        new NormalizationReplaySelector.BySnapshotId(stored.snapshotId()),
                        "replay-by-snapshot-id"));

        assertThat(result).isInstanceOf(NormalizationReplayRequestResult.Created.class);
        UUID requestId = ((NormalizationReplayRequestResult.Created) result).request().id();
        NormalizationReplayRequest completed = requestRepository.findById(requestId).orElseThrow();
        List<NormalizationReplayAttempt> attempts = attemptJournal.findByRequestId(requestId);

        assertThat(completed.status()).isEqualTo(NormalizationReplayStatus.COMPLETED);
        assertThat(completed.attemptCount()).isEqualTo(1);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().outcome())
                .isEqualTo(NormalizationReplayAttemptOutcome.COMPLETED);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(count("normalization_replay_attempt_application")).isEqualTo(1);
        assertThat(singleUuid("""
                SELECT fixture_application_log_id
                FROM normalization_replay_attempt_application
                """)).isEqualTo(singleUuid("""
                SELECT id
                FROM fixture_application_log
                WHERE outcome = 'UNCHANGED'
                """));
    }

    @Test
    void requestByUniqueShaIsIdempotentAndTerminalReplayIsNotAttemptedAgain()
            throws IOException {
        StoredFixture stored = prepareNormalizedFixture();
        NormalizationReplayCommand command = new NormalizationReplayCommand(
                new NormalizationReplaySelector.ByPayloadSha256(stored.payloadSha256()),
                "replay-by-unique-sha");

        NormalizationReplayRequestResult first = requestService.request(command);
        NormalizationReplayRequestResult repeated = requestService.request(command);

        assertThat(first).isInstanceOf(NormalizationReplayRequestResult.Created.class);
        assertThat(repeated).isInstanceOf(NormalizationReplayRequestResult.AlreadyCreated.class);
        UUID requestId = ((NormalizationReplayRequestResult.Created) first).request().id();
        NormalizationReplayRequest completed = requestRepository.findById(requestId).orElseThrow();
        assertThat(((NormalizationReplayRequestResult.AlreadyCreated) repeated).request().id())
                .isEqualTo(requestId);
        assertThat(completed.selectorType())
                .isEqualTo(NormalizationReplaySelectorType.PAYLOAD_SHA256);
        assertThat(completed.status()).isEqualTo(NormalizationReplayStatus.COMPLETED);
        assertThat(count("control_command_receipt")).isEqualTo(1);
        assertThat(count("normalization_replay_request")).isEqualTo(1);
        assertThat(count("normalization_replay_attempt")).isEqualTo(1);

        NormalizationReplayExecutionResult terminal = executionService.resume(
                requestId,
                completed.version());

        assertThat(terminal).isInstanceOf(
                NormalizationReplayExecutionResult.AlreadyTerminal.class);
        assertThat(count("normalization_replay_attempt")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
    }

    @Test
    void globallyAmbiguousShaCreatesNeitherReceiptNorRequest() {
        byte[] payload = "{\"synthetic\":true}".getBytes();
        String payloadSha256 = SnapshotHasher.sha256(payload);
        insertRawSnapshot(UUID.randomUUID(), "synthetic-a", "calendar/a", payloadSha256, payload);
        insertRawSnapshot(UUID.randomUUID(), "synthetic-b", "calendar/b", payloadSha256, payload);

        NormalizationReplayRequestResult result = requestService.request(
                new NormalizationReplayCommand(
                        new NormalizationReplaySelector.ByPayloadSha256(payloadSha256),
                        "replay-ambiguous-sha"));

        assertThat(result).isEqualTo(
                new NormalizationReplayRequestResult.AmbiguousPayloadSha256(payloadSha256));
        assertThat(count("control_command_receipt")).isZero();
        assertThat(count("normalization_replay_request")).isZero();
        assertThat(count("normalization_replay_attempt")).isZero();
    }

    @Test
    void committedPendingRequestCanBeResumedWithinTheSameApplicationContext()
            throws IOException {
        StoredFixture stored = prepareNormalizedFixture();
        UUID requestId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-01T19:00:00Z");
        ControlCommandReceipt receipt = new ControlCommandReceipt(
                receiptId,
                "replay-pending-before-stop",
                ControlCommandType.REPLAY_REQUEST,
                "1".repeat(64),
                requestId,
                createdAt);
        NormalizationReplayRequest request = NormalizationReplayRequest.pending(
                requestId,
                receiptId,
                stored.snapshotId(),
                stored.payloadSha256(),
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                stored.snapshotId().toString(),
                createdAt);

        transactionTemplate.executeWithoutResult(status -> {
            receiptStore.insert(receipt);
            requestRepository.insert(request);
        });

        NormalizationReplayRequest pending = requestRepository.findById(requestId).orElseThrow();
        assertThat(pending.status()).isEqualTo(NormalizationReplayStatus.PENDING);
        assertThat(count("normalization_replay_attempt")).isZero();

        assertThat(executionService.resume(null, pending.version()))
                .isEqualTo(new NormalizationReplayExecutionResult.Invalid(
                        "INVALID_REPLAY_RESUME"));
        assertThat(executionService.resume(requestId, 0))
                .isEqualTo(new NormalizationReplayExecutionResult.Invalid(
                        "INVALID_REPLAY_RESUME"));
        assertThat(count("normalization_replay_attempt")).isZero();

        NormalizationReplayExecutionResult resumed = executionService.resume(
                requestId,
                pending.version());

        assertThat(resumed).isInstanceOf(NormalizationReplayExecutionResult.Completed.class);
        NormalizationReplayRequest completed = requestRepository.findById(requestId).orElseThrow();
        assertThat(completed.status()).isEqualTo(NormalizationReplayStatus.COMPLETED);
        assertThat(completed.attemptCount()).isEqualTo(1);
        assertThat(count("normalization_replay_attempt")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
    }

    @Test
    void alteredStoredPayloadFailsTerminallyWithoutAnotherCanonicalEffect()
            throws IOException {
        StoredFixture stored = prepareNormalizedFixture();
        long applicationsBefore = count("fixture_application_log");
        byte[] alteredPayload = "{\"altered\":true}".getBytes();
        jdbcClient.sql("""
                UPDATE raw_snapshot
                SET payload = :payload
                WHERE id = :snapshotId
                """)
                .param("payload", alteredPayload)
                .param("snapshotId", stored.snapshotId())
                .update();

        NormalizationReplayRequestResult result = requestService.request(
                new NormalizationReplayCommand(
                        new NormalizationReplaySelector.BySnapshotId(stored.snapshotId()),
                        "replay-altered-payload"));

        UUID requestId = ((NormalizationReplayRequestResult.Created) result).request().id();
        NormalizationReplayRequest failed = requestRepository.findById(requestId).orElseThrow();
        List<NormalizationReplayAttempt> attempts = attemptJournal.findByRequestId(requestId);
        assertThat(failed.status()).isEqualTo(NormalizationReplayStatus.FAILED_TERMINAL);
        assertThat(failed.lastErrorCode()).isEqualTo("PAYLOAD_HASH_MISMATCH");
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().outcome())
                .isEqualTo(NormalizationReplayAttemptOutcome.FAILED_TERMINAL);
        assertThat(attempts.getFirst().actualPayloadSha256())
                .isEqualTo(SnapshotHasher.sha256(alteredPayload));
        assertThat(count("fixture_application_log")).isEqualTo(applicationsBefore);
        assertThat(count("normalization_replay_attempt_application")).isZero();
        assertThat(count("normalization_replay_attempt_anomaly_event")).isZero();
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
    }

    @Test
    void blockedReplayCorrelatesItsApplicationAndAnomalyEvent() throws IOException {
        UUID competitionId = catalogCommandService.registerCompetition(
                "Ukrainian Premier League", "UKR", CompetitionType.DOMESTIC_LEAGUE);
        UUID awayTeamId = catalogCommandService.registerTeam(
                "FC Livyi Bereh Kyiv", "UKR");
        insertMapping(ProviderMapping.confirmed(
                PROVIDER,
                ProviderEntityType.COMPETITION,
                PROVIDER_COMPETITION_ID,
                competitionId,
                SEASON,
                PHASE,
                Instant.parse("2026-09-01T10:00:00Z")));
        insertMapping(ProviderMapping.confirmed(
                PROVIDER,
                ProviderEntityType.TEAM,
                PROVIDER_AWAY_TEAM_ID,
                awayTeamId,
                "",
                "",
                Instant.parse("2026-09-01T10:00:00Z")));
        RawSnapshot snapshot = RawSnapshot.capture(
                PROVIDER,
                "calendar/replay-blocked",
                Instant.parse("2026-08-11T10:10:01Z"),
                fixture("/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json"),
                "cal01-replay-2");
        NormalizationResult first = normalizationService.normalize(snapshot);

        NormalizationReplayRequestResult replay = requestService.request(
                new NormalizationReplayCommand(
                        new NormalizationReplaySelector.BySnapshotId(first.snapshotId()),
                        "replay-blocked-snapshot"));

        UUID requestId = ((NormalizationReplayRequestResult.Created) replay).request().id();
        assertThat(requestRepository.findById(requestId).orElseThrow().status())
                .isEqualTo(NormalizationReplayStatus.COMPLETED);
        assertThat(first.fixturesBlocked()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(count("normalization_anomaly")).isEqualTo(1);
        assertThat(count("normalization_anomaly_event")).isEqualTo(2);
        assertThat(count("normalization_replay_attempt_application")).isEqualTo(1);
        assertThat(count("normalization_replay_attempt_anomaly_event")).isEqualTo(1);
        assertThat(singleString("""
                SELECT event.event_type
                FROM normalization_replay_attempt_anomaly_event AS replay_event
                JOIN normalization_anomaly_event AS event
                  ON event.id = replay_event.normalization_anomaly_event_id
                """)).isEqualTo("OBSERVED");
    }

    @Test
    void rejectedMappingCreatesAndExecutesDurableReplayAfterDecisionCommit()
            throws IOException {
        BlockedFixture blocked = prepareBlockedHirnykFixture(false);
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "hly-hirnyk",
                "",
                "");

        MappingDecisionResult result = mappingDecisionService.decide(
                MappingDecisionCommand.reject(
                        key,
                        0L,
                        "mapping-reject-with-durable-replay",
                        "Provider identity rejected after manual review"));

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        MappingDecisionResult.Applied applied = (MappingDecisionResult.Applied) result;
        assertThat(applied.correlatedAnomalyIds())
                .containsExactly(blocked.missingAnomalyId());
        assertThat(applied.replayRequestIds()).hasSize(1);
        UUID replayRequestId = applied.replayRequestIds().getFirst();
        NormalizationReplayRequest request = requestRepository.findById(replayRequestId)
                .orElseThrow();

        assertThat(request.status()).isEqualTo(NormalizationReplayStatus.COMPLETED);
        assertThat(request.origin()).isEqualTo(NormalizationReplayOrigin.MAPPING_DECISION);
        assertThat(request.providerMappingDecisionId()).isEqualTo(applied.decision().id());
        assertThat(singleString("""
                SELECT idempotency_key
                FROM control_command_receipt
                WHERE id = (
                    SELECT control_command_receipt_id
                    FROM normalization_replay_request
                    WHERE id = '%s'
                )
                """.formatted(replayRequestId))).isEqualTo(
                        "mapping-decision:%s:snapshot:%s".formatted(
                                applied.decision().id(),
                                blocked.snapshotId()));
        assertThat(singleString("""
                SELECT mapping_status
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                  AND season = ''
                  AND phase = ''
                """)).isEqualTo("REJECTED");
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(blocked.missingAnomalyId()))).isEqualTo("RESOLVED");
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE anomaly_code = 'REJECTED_MAPPING'
                """)).isEqualTo("OPEN");
        assertThat(count("canonical_fixture")).isZero();
        assertThat(count("provider_mapping")).isEqualTo(3);
        assertThat(count("normalization_replay_request")).isEqualTo(1);
        assertThat(count("normalization_replay_attempt")).isEqualTo(1);
    }

    @Test
    void confirmedMappingCreatesAndExecutesReplayThatNormalizesTheFixture()
            throws IOException {
        BlockedFixture blocked = prepareBlockedHirnykFixture(true);
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "hly-hirnyk",
                "",
                "");

        MappingDecisionResult result = mappingDecisionService.decide(
                MappingDecisionCommand.confirm(
                        key,
                        blocked.homeTeamId(),
                        0L,
                        "mapping-confirm-with-durable-replay",
                        "Exact historical alias confirmed after manual review"));

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        MappingDecisionResult.Applied applied = (MappingDecisionResult.Applied) result;
        assertThat(applied.correlatedAnomalyIds())
                .containsExactly(blocked.missingAnomalyId());
        assertThat(applied.replayRequestIds()).hasSize(1);
        NormalizationReplayRequest request = requestRepository
                .findById(applied.replayRequestIds().getFirst())
                .orElseThrow();
        assertThat(request.status()).isEqualTo(NormalizationReplayStatus.COMPLETED);
        assertThat(request.providerMappingDecisionId()).isEqualTo(applied.decision().id());
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(blocked.missingAnomalyId()))).isEqualTo("RESOLVED");
        assertThat(count("normalization_anomaly")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("normalization_replay_attempt")).isEqualTo(1);
        assertThat(count("normalization_replay_attempt_application")).isEqualTo(1);
        assertThat(singleString("""
                SELECT mapping_status
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                  AND season = ''
                  AND phase = ''
                """)).isEqualTo("CONFIRMED");
        assertThat(singleUuid("""
                SELECT canonical_entity_id
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                  AND season = ''
                  AND phase = ''
                """)).isEqualTo(blocked.homeTeamId());
    }

    @Test
    void simultaneousResumesClaimOneLogicalAttemptOnly() throws Exception {
        StoredFixture stored = prepareNormalizedFixture();
        NormalizationReplayRequest pending = insertPendingRequest(
                stored,
                "replay-concurrent-resume");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<NormalizationReplayExecutionResult> first = executor.submit(
                    () -> resumeAfterBarrier(pending, ready, start));
            Future<NormalizationReplayExecutionResult> second = executor.submit(
                    () -> resumeAfterBarrier(pending, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<NormalizationReplayExecutionResult> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream()
                    .filter(NormalizationReplayExecutionResult.Completed.class::isInstance)
                    .count()).isEqualTo(1);
            assertThat(results.stream()
                    .filter(result -> result instanceof NormalizationReplayExecutionResult.NotClaimed
                            || result instanceof NormalizationReplayExecutionResult.AlreadyTerminal)
                    .count()).isEqualTo(1);
        }

        NormalizationReplayRequest completed = requestRepository.findById(pending.id())
                .orElseThrow();
        assertThat(completed.status()).isEqualTo(NormalizationReplayStatus.COMPLETED);
        assertThat(completed.attemptCount()).isEqualTo(1);
        assertThat(count("normalization_replay_attempt")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(count("normalization_replay_attempt_application")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
    }

    @Test
    void failedNormalizationIsDurablyRetryableAndCompletesAfterResume()
            throws IOException {
        StoredFixture stored = prepareNormalizedFixture();
        NormalizationReplayRequest pending = insertPendingRequest(
                stored,
                "replay-savepoint-retry");
        installRejectingApplicationTrigger();

        NormalizationReplayExecutionResult first;
        try {
            first = executionService.resume(pending.id(), pending.version());
        }
        finally {
            removeRejectingApplicationTrigger();
        }

        assertThat(first).isInstanceOf(NormalizationReplayExecutionResult.Failed.class);
        NormalizationReplayRequest retryable = requestRepository.findById(pending.id())
                .orElseThrow();
        assertThat(retryable.status())
                .isEqualTo(NormalizationReplayStatus.FAILED_RETRYABLE);
        assertThat(retryable.lastErrorCode()).isEqualTo("NORMALIZATION_FAILED");
        assertThat(count("normalization_replay_attempt")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(1);
        assertThat(count("normalization_replay_attempt_application")).isZero();
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);

        NormalizationReplayExecutionResult automaticRetry =
                executionService.executeImmediately(pending.id());

        assertThat(automaticRetry)
                .isInstanceOf(NormalizationReplayExecutionResult.NotClaimed.class);
        assertThat(requestRepository.findById(pending.id()).orElseThrow().status())
                .isEqualTo(NormalizationReplayStatus.FAILED_RETRYABLE);
        assertThat(count("normalization_replay_attempt")).isEqualTo(1);

        NormalizationReplayExecutionResult resumed = executionService.resume(
                pending.id(),
                retryable.version());

        assertThat(resumed).isInstanceOf(NormalizationReplayExecutionResult.Completed.class);
        assertThat(requestRepository.findById(pending.id()).orElseThrow().status())
                .isEqualTo(NormalizationReplayStatus.COMPLETED);
        assertThat(attemptJournal.findByRequestId(pending.id()))
                .extracting(NormalizationReplayAttempt::outcome)
                .containsExactly(
                        NormalizationReplayAttemptOutcome.FAILED_RETRYABLE,
                        NormalizationReplayAttemptOutcome.COMPLETED);
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(count("normalization_replay_attempt_application")).isEqualTo(1);
    }

    private StoredFixture prepareNormalizedFixture() throws IOException {
        UUID competitionId = catalogCommandService.registerCompetition(
                "Ukrainian Premier League", "UKR", CompetitionType.DOMESTIC_LEAGUE);
        UUID homeTeamId = catalogCommandService.registerTeam(
                "FC Kryvbas Kryvyi Rih", "UKR");
        UUID awayTeamId = catalogCommandService.registerTeam(
                "FC Livyi Bereh Kyiv", "UKR");
        Instant mappingTime = Instant.parse("2026-09-01T10:00:00Z");
        insertMapping(ProviderMapping.confirmed(
                PROVIDER,
                ProviderEntityType.COMPETITION,
                PROVIDER_COMPETITION_ID,
                competitionId,
                SEASON,
                PHASE,
                mappingTime));
        insertMapping(ProviderMapping.confirmed(
                PROVIDER,
                ProviderEntityType.TEAM,
                PROVIDER_HOME_TEAM_ID,
                homeTeamId,
                "",
                "",
                mappingTime));
        insertMapping(ProviderMapping.confirmed(
                PROVIDER,
                ProviderEntityType.TEAM,
                PROVIDER_AWAY_TEAM_ID,
                awayTeamId,
                "",
                "",
                mappingTime));
        RawSnapshot snapshot = RawSnapshot.capture(
                PROVIDER,
                "calendar/replay-success",
                Instant.parse("2026-08-11T10:00:01Z"),
                fixture("/fixtures/cat001/calendar-v2-highlightly.json"),
                "cal01-replay-2");
        NormalizationResult result = normalizationService.normalize(snapshot);
        assertThat(result.fixturesCreated()).isEqualTo(1);
        return new StoredFixture(result.snapshotId(), result.snapshotSha256());
    }

    private BlockedFixture prepareBlockedHirnykFixture(boolean createHomeTeam)
            throws IOException {
        UUID competitionId = catalogCommandService.registerCompetition(
                "Ukrainian Premier League", "UKR", CompetitionType.DOMESTIC_LEAGUE);
        UUID awayTeamId = catalogCommandService.registerTeam(
                "FC Livyi Bereh Kyiv", "UKR");
        UUID homeTeamId = createHomeTeam
                ? catalogCommandService.registerTeam("FC Kryvbas Kryvyi Rih", "UKR")
                : UUID.randomUUID();
        Instant mappingTime = Instant.parse("2026-09-01T10:00:00Z");
        insertMapping(ProviderMapping.confirmed(
                PROVIDER,
                ProviderEntityType.COMPETITION,
                PROVIDER_COMPETITION_ID,
                competitionId,
                SEASON,
                PHASE,
                mappingTime));
        insertMapping(ProviderMapping.confirmed(
                PROVIDER,
                ProviderEntityType.TEAM,
                PROVIDER_AWAY_TEAM_ID,
                awayTeamId,
                "",
                "",
                mappingTime));
        RawSnapshot snapshot = RawSnapshot.capture(
                PROVIDER,
                "calendar/mapping-decision-replay",
                Instant.parse("2026-08-11T10:10:01Z"),
                fixture("/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json"),
                "cal01-replay-2");
        NormalizationResult result = normalizationService.normalize(snapshot);
        assertThat(result.fixturesBlocked()).isEqualTo(1);
        UUID missingAnomalyId = singleUuid("""
                SELECT id
                FROM normalization_anomaly
                WHERE anomaly_code = 'MISSING_MAPPING'
                """);
        return new BlockedFixture(result.snapshotId(), missingAnomalyId, homeTeamId);
    }

    private NormalizationReplayRequest insertPendingRequest(
            StoredFixture stored,
            String idempotencyKey) {
        UUID requestId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-01T19:00:00Z");
        ControlCommandReceipt receipt = new ControlCommandReceipt(
                receiptId,
                idempotencyKey,
                ControlCommandType.REPLAY_REQUEST,
                SnapshotHasher.sha256(requestId.toString().getBytes()),
                requestId,
                createdAt);
        NormalizationReplayRequest request = NormalizationReplayRequest.pending(
                requestId,
                receiptId,
                stored.snapshotId(),
                stored.payloadSha256(),
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                stored.snapshotId().toString(),
                createdAt);
        transactionTemplate.executeWithoutResult(status -> {
            receiptStore.insert(receipt);
            requestRepository.insert(request);
        });
        return requestRepository.findById(requestId).orElseThrow();
    }

    private NormalizationReplayExecutionResult resumeAfterBarrier(
            NormalizationReplayRequest pending,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent replay start barrier timed out");
        }
        return executionService.resume(pending.id(), pending.version());
    }

    private void installRejectingApplicationTrigger() {
        jdbcClient.sql("""
                CREATE OR REPLACE FUNCTION cat002_test_reject_replay_application()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'CAT-002 forced replay application failure';
                END;
                $$
                """).update();
        jdbcClient.sql("""
                CREATE TRIGGER cat002_test_reject_replay_application
                BEFORE INSERT ON fixture_application_log
                FOR EACH ROW
                EXECUTE FUNCTION cat002_test_reject_replay_application()
                """).update();
    }

    private void removeRejectingApplicationTrigger() {
        jdbcClient.sql("""
                DROP TRIGGER IF EXISTS cat002_test_reject_replay_application
                ON fixture_application_log
                """).update();
        jdbcClient.sql("DROP FUNCTION IF EXISTS cat002_test_reject_replay_application()")
                .update();
    }

    private void insertMapping(ProviderMapping candidate) {
        StoredProviderMapping stored = mappingRepository.insertIfAbsentAndResolve(candidate);
        assertThat(stored.mapping().status()).isEqualTo(candidate.status());
        assertThat(stored.mapping().canonicalEntityId()).isEqualTo(candidate.canonicalEntityId());
    }

    private void insertRawSnapshot(
            UUID id,
            String provider,
            String endpoint,
            String payloadSha256,
            byte[] payload) {
        OffsetDateTime receivedAt = Instant.parse("2026-09-01T18:00:00Z")
                .atOffset(ZoneOffset.UTC);
        jdbcClient.sql("""
                INSERT INTO raw_snapshot (
                    id, provider, endpoint, received_at, payload_sha256,
                    payload_compression, payload, connector_version
                ) VALUES (
                    :id, :provider, :endpoint, :receivedAt, :payloadSha256,
                    'identity', :payload, 'cat002-lot6-test'
                )
                """)
                .param("id", id)
                .param("provider", provider)
                .param("endpoint", endpoint)
                .param("receivedAt", receivedAt)
                .param("payloadSha256", payloadSha256)
                .param("payload", payload)
                .update();
    }

    private byte[] fixture(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test fixture: " + path);
            }
            return input.readAllBytes();
        }
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private String singleString(String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }

    private UUID singleUuid(String sql) {
        return jdbcClient.sql(sql).query(UUID.class).single();
    }

    private record StoredFixture(UUID snapshotId, String payloadSha256) {
    }

    private record BlockedFixture(
            UUID snapshotId,
            UUID missingAnomalyId,
            UUID homeTeamId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthorityConfiguration {

        @Bean
        @Primary
        CalendarAuthorityPolicy storedReplayAuthorityPolicy() {
            return new ConfiguredCalendarAuthorityPolicy(
                    "cat-002-lot6-test-policy-v1",
                    List.of(new CalendarAuthorityAssignment(
                            new CalendarAuthorityKey(
                                    PROVIDER,
                                    PROVIDER_COMPETITION_ID,
                                    SEASON,
                                    PHASE,
                                    CalendarAuthorityDataType.CALENDAR),
                            CalendarAuthorityRole.PRIMARY)));
        }
    }
}
