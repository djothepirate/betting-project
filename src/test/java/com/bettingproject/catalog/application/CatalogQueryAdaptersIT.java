package com.bettingproject.catalog.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingQueryFilter;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayQueryFilter;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.ProviderEntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER"
        })
@ActiveProfiles("control-api")
class CatalogQueryAdaptersIT {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private AnomalyQueryService anomalyQueryService;

    @Autowired
    private MappingQueryService mappingQueryService;

    @Autowired
    private ReplayQueryService replayQueryService;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM normalization_replay_attempt_application").update();
        jdbcClient.sql("DELETE FROM normalization_replay_attempt_anomaly_event").update();
        jdbcClient.sql("DELETE FROM normalization_replay_attempt").update();
        jdbcClient.sql("DELETE FROM normalization_replay_request").update();
        jdbcClient.sql("DELETE FROM provider_mapping_decision_anomaly").update();
        jdbcClient.sql("DELETE FROM normalization_anomaly_event").update();
        jdbcClient.sql("DELETE FROM fixture_application_log").update();
        jdbcClient.sql("DELETE FROM provider_mapping_decision").update();
        jdbcClient.sql("DELETE FROM control_command_receipt").update();
        jdbcClient.sql("DELETE FROM normalization_anomaly").update();
        jdbcClient.sql("DELETE FROM provider_mapping").update();
        jdbcClient.sql("DELETE FROM fixture_observation").update();
        jdbcClient.sql("DELETE FROM raw_snapshot").update();
    }

    @Test
    void anomaliesDefaultToOpenAndUseCreatedAtThenUuidForKeysetPagination() {
        UUID snapshotId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID controlSnapshotId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        insertSnapshot(snapshotId, "a".repeat(64));
        insertSnapshot(controlSnapshotId, "9".repeat(64));
        insertAnomaly(FIRST, snapshotId, "OPEN", "fixture-1");
        insertAnomaly(SECOND, controlSnapshotId, "OPEN", "team-control");
        insertAnomaly(THIRD, snapshotId, "RESOLVED", "fixture-3");
        jdbcClient.sql("""
                UPDATE normalization_anomaly
                SET provider = 'control-provider', entity_type = 'TEAM',
                    anomaly_code = 'AMBIGUOUS_MAPPING'
                WHERE id = :id
                """)
                .param("id", SECOND)
                .update();

        AnomalyQueryFilter openByDefault = new AnomalyQueryFilter(
                null, null, null, null, null, null);
        CatalogQueryPage<AnomalyQueryPort.AnomalyView> firstPage =
                anomalyQueryService.list(openByDefault, null, 1);

        assertThat(firstPage.items()).extracting(AnomalyQueryPort.AnomalyView::id)
                .containsExactly(SECOND);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.items().getFirst().snapshot().payloadSha256())
                .isEqualTo("9".repeat(64));

        CatalogQueryPage<AnomalyQueryPort.AnomalyView> secondPage = anomalyQueryService.list(
                openByDefault,
                new TimestampKeysetAnchor(NOW, SECOND),
                1);
        assertThat(secondPage.items()).extracting(AnomalyQueryPort.AnomalyView::id)
                .containsExactly(FIRST);
        assertThat(secondPage.hasNext()).isFalse();

        CatalogQueryPage<AnomalyQueryPort.AnomalyView> resolved = anomalyQueryService.list(
                new AnomalyQueryFilter(AnomalyStatus.RESOLVED, null, null, null, null, null),
                null,
                10);
        assertThat(resolved.items()).extracting(AnomalyQueryPort.AnomalyView::id)
                .containsExactly(THIRD);

        assertThat(anomalyQueryService.list(new AnomalyQueryFilter(
                AnomalyStatus.OPEN, "control-provider", null, null, null, null),
                null, 10).items()).extracting(AnomalyQueryPort.AnomalyView::id)
                .containsExactly(SECOND);
        assertThat(anomalyQueryService.list(new AnomalyQueryFilter(
                AnomalyStatus.OPEN, null, NormalizationAnomalyCode.AMBIGUOUS_MAPPING,
                null, null, null), null, 10).items())
                .extracting(AnomalyQueryPort.AnomalyView::id).containsExactly(SECOND);
        assertThat(anomalyQueryService.list(new AnomalyQueryFilter(
                AnomalyStatus.OPEN, null, null, ProviderEntityType.TEAM,
                null, null), null, 10).items())
                .extracting(AnomalyQueryPort.AnomalyView::id).containsExactly(SECOND);
        assertThat(anomalyQueryService.list(new AnomalyQueryFilter(
                AnomalyStatus.OPEN, null, null, null, controlSnapshotId, null),
                null, 10).items()).extracting(AnomalyQueryPort.AnomalyView::id)
                .containsExactly(SECOND);
        assertThat(anomalyQueryService.list(new AnomalyQueryFilter(
                AnomalyStatus.OPEN, null, null, null, null, "team-control"),
                null, 10).items()).extracting(AnomalyQueryPort.AnomalyView::id)
                .containsExactly(SECOND);

        UUID firstEvent = UUID.fromString("11000000-0000-0000-0000-000000000001");
        UUID secondEvent = UUID.fromString("11000000-0000-0000-0000-000000000002");
        insertAnomalyEvent(firstEvent, FIRST, "OPENED", null, "OPEN");
        insertAnomalyEvent(secondEvent, FIRST, "OBSERVED", "OPEN", "OPEN");
        CatalogQueryPage<AnomalyQueryPort.AnomalyEventView> firstEventPage =
                anomalyQueryService.listEvents(FIRST, null, 1);
        CatalogQueryPage<AnomalyQueryPort.AnomalyEventView> secondEventPage =
                anomalyQueryService.listEvents(
                        FIRST, new TimestampKeysetAnchor(NOW, secondEvent), 1);
        assertThat(firstEventPage.items()).extracting(AnomalyQueryPort.AnomalyEventView::id)
                .containsExactly(secondEvent);
        assertThat(firstEventPage.hasNext()).isTrue();
        assertThat(secondEventPage.items()).extracting(AnomalyQueryPort.AnomalyEventView::id)
                .containsExactly(firstEvent);
        assertThat(secondEventPage.hasNext()).isFalse();
    }

    @Test
    void mappingContextDistinguishesNoFilterGlobalEmptyAndExactSeason() {
        insertMapping(FIRST, "");
        insertMapping(SECOND, "2026");
        insertMapping(THIRD, "2027");
        UUID decisionId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID secondDecisionId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        insertRejectedDecision(decisionId, FIRST);
        insertRejectedDecision(secondDecisionId, SECOND);
        UUID canonicalTeamId = UUID.fromString("22000000-0000-0000-0000-000000000001");
        jdbcClient.sql("""
                UPDATE provider_mapping
                SET canonical_entity_id = :canonicalTeamId, confidence = 1.0,
                    mapping_status = 'CONFIRMED'
                WHERE id = :id
                """)
                .param("canonicalTeamId", canonicalTeamId)
                .param("id", SECOND)
                .update();
        jdbcClient.sql("UPDATE provider_mapping SET phase = 'QUALIFYING' WHERE id = :id")
                .param("id", THIRD)
                .update();

        CatalogQueryPage<MappingQueryPort.MappingView> unfiltered = mappingQueryService.listMappings(
                new MappingQueryFilter(null, "provider", ProviderEntityType.TEAM,
                        null, null, null, null),
                null,
                10);
        CatalogQueryPage<MappingQueryPort.MappingView> global = mappingQueryService.listMappings(
                new MappingQueryFilter(null, "provider", ProviderEntityType.TEAM,
                        null, "", "", null),
                null,
                10);
        CatalogQueryPage<MappingQueryPort.MappingView> exact = mappingQueryService.listMappings(
                new MappingQueryFilter(null, "provider", ProviderEntityType.TEAM,
                        null, "2026", null, null),
                null,
                10);

        assertThat(unfiltered.items()).hasSize(3);
        assertThat(global.items()).extracting(MappingQueryPort.MappingView::id)
                .containsExactly(FIRST);
        assertThat(exact.items()).extracting(MappingQueryPort.MappingView::id)
                .containsExactly(SECOND);

        assertThat(mappingQueryService.listMappings(new MappingQueryFilter(
                MappingStatus.CONFIRMED, null, null, null, null, null, null),
                null, 10).items()).extracting(MappingQueryPort.MappingView::id)
                .containsExactly(SECOND);
        assertThat(mappingQueryService.listMappings(new MappingQueryFilter(
                null, null, null, "team-" + SECOND, null, null, null),
                null, 10).items()).extracting(MappingQueryPort.MappingView::id)
                .containsExactly(SECOND);
        assertThat(mappingQueryService.listMappings(new MappingQueryFilter(
                null, null, null, null, null, null, canonicalTeamId),
                null, 10).items()).extracting(MappingQueryPort.MappingView::id)
                .containsExactly(SECOND);
        assertThat(mappingQueryService.listMappings(new MappingQueryFilter(
                null, null, null, null, null, "QUALIFYING", null),
                null, 10).items()).extracting(MappingQueryPort.MappingView::id)
                .containsExactly(THIRD);

        CatalogQueryPage<MappingQueryPort.MappingView> firstMappingPage =
                mappingQueryService.listMappings(
                        new MappingQueryFilter(null, null, null, null, null, null, null),
                        null, 1);
        CatalogQueryPage<MappingQueryPort.MappingView> secondMappingPage =
                mappingQueryService.listMappings(
                        new MappingQueryFilter(null, null, null, null, null, null, null),
                        new TimestampKeysetAnchor(NOW, THIRD), 1);
        assertThat(firstMappingPage.items()).extracting(MappingQueryPort.MappingView::id)
                .containsExactly(THIRD);
        assertThat(secondMappingPage.items()).extracting(MappingQueryPort.MappingView::id)
                .containsExactly(SECOND);

        MappingQueryPort.MappingDecisionView decision = mappingQueryService.listDecisions(
                new MappingDecisionQueryFilter(FIRST), null, 10).items().getFirst();
        assertThat(decision.id()).isEqualTo(decisionId);
        assertThat(decision.operatorId()).isEqualTo("test-operator");
        assertThat(decision.justification()).isEqualTo("reviewed and rejected");

        CatalogQueryPage<MappingQueryPort.MappingDecisionView> firstDecisionPage =
                mappingQueryService.listDecisions(
                        new MappingDecisionQueryFilter(null), null, 1);
        CatalogQueryPage<MappingQueryPort.MappingDecisionView> secondDecisionPage =
                mappingQueryService.listDecisions(
                        new MappingDecisionQueryFilter(null),
                        new TimestampKeysetAnchor(NOW, secondDecisionId), 1);
        assertThat(firstDecisionPage.items())
                .extracting(MappingQueryPort.MappingDecisionView::id)
                .containsExactly(secondDecisionId);
        assertThat(secondDecisionPage.items())
                .extracting(MappingQueryPort.MappingDecisionView::id)
                .containsExactly(decisionId);

        UUID snapshotId = UUID.fromString("23000000-0000-0000-0000-000000000001");
        insertSnapshot(snapshotId, "8".repeat(64));
        insertAnomaly(FIRST, snapshotId, "OPEN", "fixture-correlation-1");
        insertAnomaly(SECOND, snapshotId, "OPEN", "fixture-correlation-2");
        insertDecisionAnomalyCorrelation(decisionId, FIRST);
        insertDecisionAnomalyCorrelation(decisionId, SECOND);
        CatalogQueryPage<MappingQueryPort.DecisionAnomalyView> firstCorrelationPage =
                mappingQueryService.listCorrelatedAnomalies(decisionId, null, 1);
        CatalogQueryPage<MappingQueryPort.DecisionAnomalyView> secondCorrelationPage =
                mappingQueryService.listCorrelatedAnomalies(
                        decisionId, new CorrelationKeysetAnchor(NOW, SECOND), 1);
        assertThat(firstCorrelationPage.items())
                .extracting(item -> item.anomaly().id()).containsExactly(SECOND);
        assertThat(secondCorrelationPage.items())
                .extracting(item -> item.anomaly().id()).containsExactly(FIRST);
    }

    @Test
    void replayQueriesExposeSafeProvenanceAndConfineAttemptsToTheirRequest() {
        UUID firstSnapshot = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID secondSnapshot = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID firstRequest = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID secondRequest = UUID.fromString("40000000-0000-0000-0000-000000000002");
        UUID decisionRequest = UUID.fromString("40000000-0000-0000-0000-000000000003");
        UUID mappingId = UUID.fromString("41000000-0000-0000-0000-000000000001");
        UUID decisionId = UUID.fromString("42000000-0000-0000-0000-000000000001");
        insertSnapshot(firstSnapshot, "b".repeat(64));
        insertSnapshot(secondSnapshot, "c".repeat(64));
        insertCompletedReplay(firstRequest, firstSnapshot, "b".repeat(64), 1);
        insertCompletedReplay(secondRequest, secondSnapshot, "c".repeat(64), 2);
        insertMapping(mappingId, "2026");
        insertRejectedDecision(decisionId, mappingId);
        insertCompletedReplay(
                decisionRequest,
                secondSnapshot,
                "c".repeat(64),
                1,
                "MAPPING_DECISION",
                decisionId);

        CatalogQueryPage<ReplayQueryPort.ReplayRequestView> filtered =
                replayQueryService.listRequests(
                        new ReplayQueryFilter(
                                NormalizationReplayStatus.COMPLETED,
                                NormalizationReplayOrigin.MANUAL,
                                firstSnapshot,
                                null),
                        null,
                        10);
        assertThat(filtered.items()).extracting(ReplayQueryPort.ReplayRequestView::id)
                .containsExactly(firstRequest);
        assertThat(filtered.items().getFirst().snapshot().endpoint()).isEqualTo("calendar");

        assertThat(replayQueryService.listRequests(new ReplayQueryFilter(
                null, NormalizationReplayOrigin.MAPPING_DECISION, null, null),
                null, 10).items()).extracting(ReplayQueryPort.ReplayRequestView::id)
                .containsExactly(decisionRequest);
        assertThat(replayQueryService.listRequests(new ReplayQueryFilter(
                null, null, null, decisionId), null, 10).items())
                .extracting(ReplayQueryPort.ReplayRequestView::id)
                .containsExactly(decisionRequest);

        CatalogQueryPage<ReplayQueryPort.ReplayRequestView> firstRequestPage =
                replayQueryService.listRequests(
                        new ReplayQueryFilter(null, null, null, null), null, 1);
        CatalogQueryPage<ReplayQueryPort.ReplayRequestView> secondRequestPage =
                replayQueryService.listRequests(
                        new ReplayQueryFilter(null, null, null, null),
                        new TimestampKeysetAnchor(NOW, decisionRequest), 1);
        assertThat(firstRequestPage.items()).extracting(ReplayQueryPort.ReplayRequestView::id)
                .containsExactly(decisionRequest);
        assertThat(secondRequestPage.items()).extracting(ReplayQueryPort.ReplayRequestView::id)
                .containsExactly(secondRequest);

        CatalogQueryPage<ReplayQueryPort.ReplayAttemptView> firstAttempts =
                replayQueryService.listAttempts(firstRequest, null, 10);
        CatalogQueryPage<ReplayQueryPort.ReplayAttemptView> secondAttempts =
                replayQueryService.listAttempts(secondRequest, null, 10);
        assertThat(firstAttempts.items()).extracting(ReplayQueryPort.ReplayAttemptView::attemptNumber)
                .containsExactly(1);
        assertThat(secondAttempts.items()).extracting(ReplayQueryPort.ReplayAttemptView::attemptNumber)
                .containsExactly(2);

        UUID secondFirstRequestAttempt = UUID.fromString(
                "43000000-0000-0000-0000-000000000002");
        insertCompletedAttempt(
                firstRequest, secondFirstRequestAttempt, 2, "b".repeat(64));
        jdbcClient.sql("""
                UPDATE normalization_replay_request
                SET attempt_count = 2
                WHERE id = :requestId
                """)
                .param("requestId", firstRequest)
                .update();
        CatalogQueryPage<ReplayQueryPort.ReplayAttemptView> firstAttemptPage =
                replayQueryService.listAttempts(firstRequest, null, 1);
        CatalogQueryPage<ReplayQueryPort.ReplayAttemptView> secondAttemptPage =
                replayQueryService.listAttempts(
                        firstRequest,
                        new AttemptKeysetAnchor(2, secondFirstRequestAttempt),
                        1);
        assertThat(firstAttemptPage.items())
                .extracting(ReplayQueryPort.ReplayAttemptView::attemptNumber)
                .containsExactly(2);
        assertThat(secondAttemptPage.items())
                .extracting(ReplayQueryPort.ReplayAttemptView::attemptNumber)
                .containsExactly(1);

        UUID firstObservation = UUID.fromString("44000000-0000-0000-0000-000000000001");
        UUID secondObservation = UUID.fromString("44000000-0000-0000-0000-000000000002");
        UUID firstApplication = UUID.fromString("45000000-0000-0000-0000-000000000001");
        UUID secondApplication = UUID.fromString("45000000-0000-0000-0000-000000000002");
        insertFixtureApplication(firstSnapshot, firstObservation, firstApplication);
        insertFixtureApplication(firstSnapshot, secondObservation, secondApplication);
        insertReplayApplicationCorrelation(secondFirstRequestAttempt, firstApplication);
        insertReplayApplicationCorrelation(secondFirstRequestAttempt, secondApplication);
        CatalogQueryPage<ReplayQueryPort.ReplayApplicationView> firstApplicationPage =
                replayQueryService.listApplications(
                        firstRequest, secondFirstRequestAttempt, null, 1);
        CatalogQueryPage<ReplayQueryPort.ReplayApplicationView> secondApplicationPage =
                replayQueryService.listApplications(
                        firstRequest,
                        secondFirstRequestAttempt,
                        new CorrelationKeysetAnchor(NOW, secondApplication),
                        1);
        assertThat(firstApplicationPage.items())
                .extracting(ReplayQueryPort.ReplayApplicationView::id)
                .containsExactly(secondApplication);
        assertThat(secondApplicationPage.items())
                .extracting(ReplayQueryPort.ReplayApplicationView::id)
                .containsExactly(firstApplication);
        assertThat(replayQueryService.listApplications(
                secondRequest, secondFirstRequestAttempt, null, 10).items()).isEmpty();

        UUID firstAnomaly = UUID.fromString("46000000-0000-0000-0000-000000000001");
        UUID secondAnomaly = UUID.fromString("46000000-0000-0000-0000-000000000002");
        UUID firstEvent = UUID.fromString("47000000-0000-0000-0000-000000000001");
        UUID secondEvent = UUID.fromString("47000000-0000-0000-0000-000000000002");
        insertAnomaly(firstAnomaly, firstSnapshot, "OPEN", "replay-fixture-1");
        insertAnomaly(secondAnomaly, firstSnapshot, "OPEN", "replay-fixture-2");
        insertAnomalyEvent(firstEvent, firstAnomaly, "OPENED", null, "OPEN");
        insertAnomalyEvent(secondEvent, secondAnomaly, "OPENED", null, "OPEN");
        insertReplayAnomalyEventCorrelation(secondFirstRequestAttempt, firstEvent);
        insertReplayAnomalyEventCorrelation(secondFirstRequestAttempt, secondEvent);
        CatalogQueryPage<ReplayQueryPort.ReplayAnomalyEventView> firstAnomalyEventPage =
                replayQueryService.listAnomalyEvents(
                        firstRequest, secondFirstRequestAttempt, null, 1);
        CatalogQueryPage<ReplayQueryPort.ReplayAnomalyEventView> secondAnomalyEventPage =
                replayQueryService.listAnomalyEvents(
                        firstRequest,
                        secondFirstRequestAttempt,
                        new CorrelationKeysetAnchor(NOW, secondEvent),
                        1);
        assertThat(firstAnomalyEventPage.items())
                .extracting(ReplayQueryPort.ReplayAnomalyEventView::id)
                .containsExactly(secondEvent);
        assertThat(secondAnomalyEventPage.items())
                .extracting(ReplayQueryPort.ReplayAnomalyEventView::id)
                .containsExactly(firstEvent);
        assertThat(replayQueryService.listAnomalyEvents(
                secondRequest, secondFirstRequestAttempt, null, 10).items()).isEmpty();
    }

    private void insertSnapshot(UUID id, String sha256) {
        jdbcClient.sql("""
                INSERT INTO raw_snapshot (
                    id, provider, endpoint, requested_at, received_at, source_observed_at,
                    http_status, latency_ms, quota_remaining, payload_sha256,
                    payload_compression, payload, connector_version, created_at
                ) VALUES (
                    :id, 'provider', 'calendar', :now, :now, :now,
                    200, 12, 99, :sha256, 'identity', :payload, 'test-v1', :now
                )
                """)
                .param("id", id)
                .param("now", utc(NOW))
                .param("sha256", sha256)
                .param("payload", new byte[] {1, 2, 3})
                .update();
    }

    private void insertAnomaly(UUID id, UUID snapshotId, String status, String providerEntityId) {
        jdbcClient.sql("""
                INSERT INTO normalization_anomaly (
                    id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                    season, phase, anomaly_code, details, status, version, created_at,
                    last_seen_at, updated_at, resolved_at, occurrence_count
                ) VALUES (
                    :id, :snapshotId, 'provider', 'FIXTURE', :providerEntityId,
                    '2026', 'REGULAR', 'MISSING_MAPPING', 'mapping is missing',
                    :status, 1, :now, :now, :now,
                    CASE WHEN :status = 'RESOLVED' THEN :now ELSE NULL END, 1
                )
                """)
                .param("id", id)
                .param("snapshotId", snapshotId)
                .param("providerEntityId", providerEntityId)
                .param("status", status)
                .param("now", utc(NOW))
                .update();
    }

    private void insertAnomalyEvent(
            UUID eventId,
            UUID anomalyId,
            String eventType,
            String previousStatus,
            String resultingStatus) {
        jdbcClient.sql("""
                INSERT INTO normalization_anomaly_event (
                    id, normalization_anomaly_id, event_type, previous_status,
                    resulting_status, fixture_application_log_id, details, created_at
                ) VALUES (
                    :id, :anomalyId, :eventType, CAST(:previousStatus AS VARCHAR),
                    :resultingStatus, NULL, 'synthetic lifecycle evidence', :now
                )
                """)
                .param("id", eventId)
                .param("anomalyId", anomalyId)
                .param("eventType", eventType)
                .param("previousStatus", previousStatus)
                .param("resultingStatus", resultingStatus)
                .param("now", utc(NOW))
                .update();
    }

    private void insertMapping(UUID id, String season) {
        jdbcClient.sql("""
                INSERT INTO provider_mapping (
                    id, provider, entity_type, provider_entity_id, canonical_entity_id,
                    season, phase, confidence, mapping_status, created_at, updated_at, version
                ) VALUES (
                    :id, 'provider', 'TEAM', :providerEntityId, NULL,
                    :season, '', NULL, 'REJECTED', :now, :now, 1
                )
                """)
                .param("id", id)
                .param("providerEntityId", "team-" + id)
                .param("season", season)
                .param("now", utc(NOW))
                .update();
    }

    private void insertRejectedDecision(UUID decisionId, UUID mappingId) {
        UUID receiptId = UUID.nameUUIDFromBytes(
                ("receipt-decision-" + decisionId).getBytes(StandardCharsets.UTF_8));
        jdbcClient.sql("UPDATE provider_mapping SET version = 2 WHERE id = :mappingId")
                .param("mappingId", mappingId)
                .update();
        jdbcClient.sql("""
                INSERT INTO control_command_receipt (
                    id, idempotency_key, command_type, command_sha256,
                    result_resource_id, created_at
                ) VALUES (
                    :id, :idempotencyKey, 'MAPPING_REJECT', :sha256,
                    :decisionId, :now
                )
                """)
                .param("id", receiptId)
                .param("idempotencyKey", "mapping-query-" + decisionId)
                .param("sha256", "d".repeat(64))
                .param("decisionId", decisionId)
                .param("now", utc(NOW))
                .update();
        jdbcClient.sql("""
                INSERT INTO provider_mapping_decision (
                    id, provider_mapping_id, control_command_receipt_id, decision_type,
                    expected_version, resulting_version, previous_mapping_status,
                    previous_canonical_entity_id, previous_confidence,
                    resulting_mapping_status, resulting_canonical_entity_id,
                    resulting_confidence, operator_id, justification, created_at
                ) VALUES (
                    :id, :mappingId, :receiptId, 'REJECT',
                    1, 2, 'REJECTED', NULL, NULL,
                    'REJECTED', NULL, NULL, 'test-operator',
                    'reviewed and rejected', :now
                )
                """)
                .param("id", decisionId)
                .param("mappingId", mappingId)
                .param("receiptId", receiptId)
                .param("now", utc(NOW))
                .update();
    }

    private void insertDecisionAnomalyCorrelation(UUID decisionId, UUID anomalyId) {
        jdbcClient.sql("""
                INSERT INTO provider_mapping_decision_anomaly (
                    provider_mapping_decision_id, normalization_anomaly_id, created_at
                ) VALUES (:decisionId, :anomalyId, :now)
                """)
                .param("decisionId", decisionId)
                .param("anomalyId", anomalyId)
                .param("now", utc(NOW))
                .update();
    }

    private void insertCompletedReplay(
            UUID requestId,
            UUID snapshotId,
            String sha256,
            int attemptNumber) {
        insertCompletedReplay(
                requestId,
                snapshotId,
                sha256,
                attemptNumber,
                "MANUAL",
                null);
    }

    private void insertCompletedReplay(
            UUID requestId,
            UUID snapshotId,
            String sha256,
            int attemptNumber,
            String origin,
            UUID mappingDecisionId) {
        UUID receiptId = UUID.nameUUIDFromBytes(
                ("receipt-" + requestId).getBytes(StandardCharsets.UTF_8));
        UUID attemptId = UUID.nameUUIDFromBytes(
                ("attempt-" + requestId + "-" + attemptNumber)
                        .getBytes(StandardCharsets.UTF_8));
        jdbcClient.sql("""
                INSERT INTO control_command_receipt (
                    id, idempotency_key, command_type, command_sha256,
                    result_resource_id, created_at
                ) VALUES (
                    :id, :key, 'REPLAY_REQUEST', :sha256, :requestId, :now
                )
                """)
                .param("id", receiptId)
                .param("key", "replay-" + requestId)
                .param("sha256", sha256)
                .param("requestId", requestId)
                .param("now", utc(NOW))
                .update();
        jdbcClient.sql("""
                INSERT INTO normalization_replay_request (
                    id, control_command_receipt_id, raw_snapshot_id,
                    expected_payload_sha256, provider_mapping_decision_id,
                    origin, selector_type, selector_value, status, version,
                    attempt_count, last_error_code, last_error_message,
                    created_at, updated_at, completed_at
                ) VALUES (
                    :id, :receiptId, :snapshotId, :sha256, :mappingDecisionId,
                    :origin, 'SNAPSHOT_ID', :selector, 'COMPLETED', 2,
                    :attemptNumber, NULL, NULL, :now, :now, :now
                )
                """)
                .param("id", requestId)
                .param("receiptId", receiptId)
                .param("snapshotId", snapshotId)
                .param("sha256", sha256)
                .param("origin", origin)
                .param("mappingDecisionId", mappingDecisionId)
                .param("selector", snapshotId.toString())
                .param("attemptNumber", attemptNumber)
                .param("now", utc(NOW))
                .update();
        insertCompletedAttempt(requestId, attemptId, attemptNumber, sha256);
    }

    private void insertCompletedAttempt(
            UUID requestId,
            UUID attemptId,
            int attemptNumber,
            String sha256) {
        jdbcClient.sql("""
                INSERT INTO normalization_replay_attempt (
                    id, normalization_replay_request_id, attempt_number, outcome,
                    expected_payload_sha256, actual_payload_sha256, compatible,
                    fixtures_created, fixtures_updated, fixtures_unchanged,
                    fixtures_blocked, anomalies, error_code, error_message,
                    started_at, finished_at
                ) VALUES (
                    :id, :requestId, :attemptNumber, 'COMPLETED',
                    :sha256, :sha256, TRUE, 0, 0, 1, 0, 0, NULL, NULL, :now, :now
                )
                """)
                .param("id", attemptId)
                .param("requestId", requestId)
                .param("attemptNumber", attemptNumber)
                .param("sha256", sha256)
                .param("now", utc(NOW))
                .update();
    }

    private void insertFixtureApplication(
            UUID snapshotId,
            UUID observationId,
            UUID applicationId) {
        jdbcClient.sql("""
                INSERT INTO fixture_observation (
                    id, raw_snapshot_id, canonical_fixture_id, provider,
                    provider_fixture_id, provider_competition_id,
                    provider_home_team_id, provider_away_team_id,
                    source_kickoff_at, source_status, source_phase,
                    normalization_status, reason_code, observed_at, created_at,
                    source_schema_version, source_season, source_neutral_venue,
                    source_participants_unordered
                ) VALUES (
                    :id, :snapshotId, NULL, 'provider', :providerFixtureId,
                    'competition', 'home', 'away', :now, 'SCHEDULED', 'REGULAR',
                    'BLOCKED', 'MISSING_MAPPING', :now, :now,
                    'cal01-fixture-v3', '2026', NULL, FALSE
                )
                """)
                .param("id", observationId)
                .param("snapshotId", snapshotId)
                .param("providerFixtureId", "fixture-" + observationId)
                .param("now", utc(NOW))
                .update();
        jdbcClient.sql("""
                INSERT INTO fixture_application_log (
                    id, fixture_observation_id, canonical_fixture_id,
                    previous_authority_observation_id, outcome, reason_code,
                    authority_role, policy_version, evaluated_at
                ) VALUES (
                    :id, :observationId, NULL, NULL, 'BLOCKED',
                    'MISSING_MAPPING', NULL, NULL, :now
                )
                """)
                .param("id", applicationId)
                .param("observationId", observationId)
                .param("now", utc(NOW))
                .update();
    }

    private void insertReplayApplicationCorrelation(UUID attemptId, UUID applicationId) {
        jdbcClient.sql("""
                INSERT INTO normalization_replay_attempt_application (
                    normalization_replay_attempt_id, fixture_application_log_id, created_at
                ) VALUES (:attemptId, :applicationId, :now)
                """)
                .param("attemptId", attemptId)
                .param("applicationId", applicationId)
                .param("now", utc(NOW))
                .update();
    }

    private void insertReplayAnomalyEventCorrelation(UUID attemptId, UUID eventId) {
        jdbcClient.sql("""
                INSERT INTO normalization_replay_attempt_anomaly_event (
                    normalization_replay_attempt_id, normalization_anomaly_event_id, created_at
                ) VALUES (:attemptId, :eventId, :now)
                """)
                .param("attemptId", attemptId)
                .param("eventId", eventId)
                .param("now", utc(NOW))
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
