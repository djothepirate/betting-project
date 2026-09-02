package com.bettingproject.catalog.adapter.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.AnomalyQueryPort;
import com.bettingproject.catalog.application.AnomalyQueryService;
import com.bettingproject.catalog.application.AttemptKeysetAnchor;
import com.bettingproject.catalog.application.CalendarNormalizationLock;
import com.bettingproject.catalog.application.CatalogRepository;
import com.bettingproject.catalog.application.ControlCommandLock;
import com.bettingproject.catalog.application.ControlCommandReceiptStore;
import com.bettingproject.catalog.application.CorrelationKeysetAnchor;
import com.bettingproject.catalog.application.DecisionJustificationSanitizer;
import com.bettingproject.catalog.application.MappingDecisionAnomalyStore;
import com.bettingproject.catalog.application.MappingDecisionCommand;
import com.bettingproject.catalog.application.MappingDecisionReplayPlanner;
import com.bettingproject.catalog.application.MappingDecisionResult;
import com.bettingproject.catalog.application.MappingDecisionService;
import com.bettingproject.catalog.application.MappingQueryPort;
import com.bettingproject.catalog.application.MappingQueryService;
import com.bettingproject.catalog.application.NormalizationReplayAfterCommitExecutor;
import com.bettingproject.catalog.application.NormalizationReplayAnomalyEventStore;
import com.bettingproject.catalog.application.NormalizationReplayApplicationStore;
import com.bettingproject.catalog.application.NormalizationReplayAttempt;
import com.bettingproject.catalog.application.NormalizationReplayAttemptJournal;
import com.bettingproject.catalog.application.NormalizationReplayCommand;
import com.bettingproject.catalog.application.NormalizationReplayExecutionResult;
import com.bettingproject.catalog.application.NormalizationReplayExecutionService;
import com.bettingproject.catalog.application.NormalizationReplayOrigin;
import com.bettingproject.catalog.application.NormalizationReplayRequest;
import com.bettingproject.catalog.application.NormalizationReplayRequestRepository;
import com.bettingproject.catalog.application.NormalizationReplayRequestResult;
import com.bettingproject.catalog.application.NormalizationReplayRequestService;
import com.bettingproject.catalog.application.NormalizationReplaySavepoint;
import com.bettingproject.catalog.application.NormalizationReplaySelectorType;
import com.bettingproject.catalog.application.NormalizationReplayStatus;
import com.bettingproject.catalog.application.OperatorIdentityProvider;
import com.bettingproject.catalog.application.ProviderMappingDecisionJournal;
import com.bettingproject.catalog.application.ReplayQueryPort;
import com.bettingproject.catalog.application.ReplayQueryService;
import com.bettingproject.catalog.application.SnapshotProvenance;
import com.bettingproject.catalog.application.StoredRawSnapshotReader;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import com.bettingproject.catalog.domain.FixtureApplicationOutcome;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.NormalizationAnomalyEventType;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMappingDecision;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogControlWebTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final UUID SNAPSHOT_ID = id(1);
    private static final UUID ANOMALY_ID = id(2);
    private static final UUID ANOMALY_EVENT_ID = id(3);
    private static final UUID MAPPING_ID = id(4);
    private static final UUID DECISION_ID = id(5);
    private static final UUID REQUEST_ID = id(6);
    private static final UUID ATTEMPT_ID = id(7);
    private static final UUID APPLICATION_ID = id(8);
    private static final UUID REPLAY_EVENT_ID = id(9);
    private static final UUID CANONICAL_ID = id(10);
    private static final UUID RECEIPT_ID = id(11);
    private static final String SHA = "a".repeat(64);

    private FakeAnomalyPort anomalyPort;
    private FakeMappingPort mappingPort;
    private FakeReplayPort replayPort;
    private FakeMappingDecisionService decisionService;
    private FakeReplayRequestService replayRequestService;
    private FakeReplayExecutionService replayExecutionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        anomalyPort = new FakeAnomalyPort();
        mappingPort = new FakeMappingPort();
        replayPort = new FakeReplayPort();
        decisionService = new FakeMappingDecisionService();
        replayRequestService = new FakeReplayRequestService();
        replayExecutionService = new FakeReplayExecutionService();
        CatalogCursorCodec cursorCodec = new CatalogCursorCodec();
        StrictCatalogCommandParser parser = new StrictCatalogCommandParser();

        mvc = MockMvcBuilders.standaloneSetup(
                        new AnomalyControlController(
                                new AnomalyQueryService(anomalyPort), cursorCodec),
                        new MappingControlController(
                                new MappingQueryService(mappingPort),
                                new ReplayQueryService(replayPort),
                                decisionService,
                                parser,
                                cursorCodec),
                        new ReplayControlController(
                                new ReplayQueryService(replayPort),
                                replayRequestService,
                                replayExecutionService,
                                parser,
                                cursorCodec))
                .setControllerAdvice(new CatalogProblemHandler())
                .build();
    }

    @Test
    void exposesEveryBoundedReadRouteWithoutRawPayloadOrReceipts() throws Exception {
        String[] routes = {
                "/internal/catalog/anomalies",
                "/internal/catalog/anomalies/" + ANOMALY_ID,
                "/internal/catalog/anomalies/" + ANOMALY_ID + "/events",
                "/internal/catalog/provider-mappings",
                "/internal/catalog/provider-mappings/" + MAPPING_ID,
                "/internal/catalog/mapping-decisions",
                "/internal/catalog/mapping-decisions/" + DECISION_ID,
                "/internal/catalog/mapping-decisions/" + DECISION_ID + "/anomalies",
                "/internal/catalog/replay-requests",
                "/internal/catalog/replay-requests/" + REQUEST_ID,
                "/internal/catalog/replay-requests/" + REQUEST_ID + "/attempts",
                "/internal/catalog/replay-requests/" + REQUEST_ID + "/attempts/"
                        + ATTEMPT_ID + "/applications",
                "/internal/catalog/replay-requests/" + REQUEST_ID + "/attempts/"
                        + ATTEMPT_ID + "/anomaly-events"
        };

        for (String route : routes) {
            String response = mvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(response)
                    .doesNotContain("controlCommandReceiptId")
                    .doesNotContain("\"payload\":");
        }
    }

    @Test
    void rejectsUnknownDuplicateAndArbitraryPathParameters() throws Exception {
        mvc.perform(get("/internal/catalog/anomalies").queryParam("path", "C:/sensitive"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARBITRARY_PATH_FORBIDDEN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:/sensitive"))));

        mvc.perform(get("/internal/catalog/provider-mappings").queryParam("sort", "drop"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_QUERY_PARAMETER"));

        mvc.perform(get("/internal/catalog/replay-requests")
                        .queryParam("limit", "10", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_QUERY_PARAMETER"));
    }

    @Test
    void listUsesDefaultOpenLimitPlusOneAndFilterBoundCursor() throws Exception {
        anomalyPort.paginate = true;

        String cursor = mvc.perform(get("/internal/catalog/anomalies").queryParam("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        cursor = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(cursor).get("nextCursor").textValue();

        org.assertj.core.api.Assertions.assertThat(anomalyPort.lastFilter.status())
                .isEqualTo(AnomalyStatus.OPEN);
        org.assertj.core.api.Assertions.assertThat(anomalyPort.lastFetchLimit).isEqualTo(2);

        mvc.perform(get("/internal/catalog/anomalies")
                        .queryParam("limit", "1")
                        .queryParam("status", "RESOLVED")
                        .queryParam("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void mappingCommandUsesStrictBodyAndMapsSuccessfulAndUnavailableResults() throws Exception {
        decisionService.result = new MappingDecisionResult.Applied(
                decisionDomain(), List.of(ANOMALY_ID), List.of(REQUEST_ID));

        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-confirm-1")
                        .content(mappingBody()))
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "Location",
                        "/internal/catalog/mapping-decisions/" + DECISION_ID))
                .andExpect(jsonPath("$.decision.id").value(DECISION_ID.toString()))
                .andExpect(jsonPath("$.replayRequests[0].id").value(REQUEST_ID.toString()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("controlCommandReceiptId"))));

        decisionService.result = new MappingDecisionResult.OperatorUnavailable();
        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-confirm-2")
                        .content(mappingBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OPERATOR_UNAVAILABLE"));

        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-confirm-3")
                        .content("{\"path\":\"C:/sensitive\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARBITRARY_PATH_FORBIDDEN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:/sensitive"))));
    }

    @Test
    void mappingDecisionMatrixUsesDurableReplayStates() throws Exception {
        decisionService.result = new MappingDecisionResult.Applied(
                decisionDomain(), List.of(), List.of());
        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-no-replay")
                        .content(mappingBody()))
                .andExpect(status().isOk());

        decisionService.result = new MappingDecisionResult.AlreadyApplied(
                decisionDomain(), List.of(), List.of(REQUEST_ID));
        replayPort.request = requestView(NormalizationReplayStatus.PENDING, 1, 0);
        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-repeat-pending")
                        .content(mappingBody()))
                .andExpect(status().isAccepted());

        replayPort.request = requestView(NormalizationReplayStatus.COMPLETED, 3, 1);
        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-repeat-terminal")
                        .content(mappingBody()))
                .andExpect(status().isOk());
    }

    @Test
    void snapshotMappingAndHttpSuppliedOperatorAreRejectedOrIgnored() throws Exception {
        String snapshotBody = new String(mappingBody(), UTF_8)
                .replace("\"TEAM\"", "\"SNAPSHOT\"");
        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-snapshot")
                        .content(snapshotBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_NOT_MAPPABLE"));

        String authorInBody = new String(mappingBody(), UTF_8)
                .replace("\"justification\"", "\"operatorId\":\"attacker\",\"justification\"");
        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-body-author")
                        .content(authorInBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));

        decisionService.result = new MappingDecisionResult.Applied(
                decisionDomain(), List.of(), List.of());
        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "mapping-header-author")
                        .header("X-Operator-Id", "attacker")
                        .content(mappingBody()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(decisionService.lastCommand)
                .extracting(MappingDecisionCommand::idempotencyKey)
                .isEqualTo("mapping-header-author");
    }

    @Test
    void replayCreationAndResumeUseDurableStateForHttpStatus() throws Exception {
        replayRequestService.result = new NormalizationReplayRequestResult.Created(
                pendingRequest());

        mvc.perform(post("/internal/catalog/replay-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "replay-create-1")
                        .content("{\"snapshotId\":\"" + SNAPSHOT_ID + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "Location",
                        "/internal/catalog/replay-requests/" + REQUEST_ID))
                .andExpect(jsonPath("$.id").value(REQUEST_ID.toString()));

        replayExecutionService.result = new NormalizationReplayExecutionResult.NotClaimed(
                runningRequest());
        replayPort.request = requestView(NormalizationReplayStatus.RUNNING, 2, 1);
        mvc.perform(post("/internal/catalog/replay-requests/" + REQUEST_ID + "/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        replayExecutionService.result = new NormalizationReplayExecutionResult.NotClaimed(
                runningRequest());
        replayPort.request = requestView(NormalizationReplayStatus.RUNNING, 3, 1);
        mvc.perform(post("/internal/catalog/replay-requests/" + REQUEST_ID + "/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void replayRequestAndResumeMatrixIsFullyTranslated() throws Exception {
        replayRequestService.result = new NormalizationReplayRequestResult.AlreadyCreated(
                pendingRequest());
        replayPort.request = requestView(NormalizationReplayStatus.PENDING, 1, 0);
        performReplayCreate("repeat-pending").andExpect(status().isAccepted());

        replayPort.request = requestView(NormalizationReplayStatus.COMPLETED, 3, 1);
        performReplayCreate("repeat-terminal").andExpect(status().isOk());

        replayRequestService.result = new NormalizationReplayRequestResult.AmbiguousPayloadSha256(SHA);
        performReplayCreate("ambiguous-hash")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_PAYLOAD_SHA256"));

        replayRequestService.result = new NormalizationReplayRequestResult.IdempotencyConflict();
        performReplayCreate("idempotency-conflict")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        replayExecutionService.result = new NormalizationReplayExecutionResult.Completed(
                completedRequest(), completedAttempt());
        replayPort.request = requestView(NormalizationReplayStatus.COMPLETED, 3, 1);
        performResume(3).andExpect(status().isOk());

        replayExecutionService.result = new NormalizationReplayExecutionResult.Failed(
                failedRequest(NormalizationReplayStatus.FAILED_RETRYABLE),
                failedAttempt(com.bettingproject.catalog.application.NormalizationReplayAttemptOutcome.FAILED_RETRYABLE));
        replayPort.request = requestView(NormalizationReplayStatus.FAILED_RETRYABLE, 3, 1);
        performResume(3).andExpect(status().isAccepted());

        replayExecutionService.result = new NormalizationReplayExecutionResult.Failed(
                failedRequest(NormalizationReplayStatus.FAILED_TERMINAL),
                failedAttempt(com.bettingproject.catalog.application.NormalizationReplayAttemptOutcome.FAILED_TERMINAL));
        replayPort.request = requestView(NormalizationReplayStatus.FAILED_TERMINAL, 3, 1);
        performResume(3).andExpect(status().isOk());

        replayExecutionService.result = new NormalizationReplayExecutionResult.AlreadyTerminal(
                completedRequest());
        replayPort.request = requestView(NormalizationReplayStatus.COMPLETED, 3, 1);
        performResume(3).andExpect(status().isOk());

        replayExecutionService.result = new NormalizationReplayExecutionResult.NotClaimed(
                pendingRequest());
        replayPort.request = requestView(NormalizationReplayStatus.PENDING, 1, 0);
        performResume(1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLAY_CLAIM_CONFLICT"));
    }

    @Test
    void missingDetailsAndParentsReturn404WhileKnownEmptyHistoriesRemain200() throws Exception {
        UUID missing = id(999);
        String[] missingRoutes = {
                "/internal/catalog/anomalies/" + missing,
                "/internal/catalog/anomalies/" + missing + "/events",
                "/internal/catalog/provider-mappings/" + missing,
                "/internal/catalog/mapping-decisions/" + missing,
                "/internal/catalog/mapping-decisions/" + missing + "/anomalies",
                "/internal/catalog/replay-requests/" + missing,
                "/internal/catalog/replay-requests/" + missing + "/attempts",
                "/internal/catalog/replay-requests/" + missing + "/attempts/"
                        + ATTEMPT_ID + "/applications"
        };
        for (String route : missingRoutes) {
            mvc.perform(get(route)).andExpect(status().isNotFound());
        }

        anomalyPort.events = List.of();
        mappingPort.correlated = List.of();
        replayPort.attempts = List.of();
        mvc.perform(get("/internal/catalog/anomalies/" + ANOMALY_ID + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/internal/catalog/mapping-decisions/" + DECISION_ID + "/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/internal/catalog/replay-requests/" + REQUEST_ID + "/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void everyDeclaredListFilterIsPassedToItsApplicationDto() throws Exception {
        mvc.perform(get("/internal/catalog/anomalies")
                        .queryParam("status", "RESOLVED")
                        .queryParam("provider", "provider")
                        .queryParam("code", "MISSING_MAPPING")
                        .queryParam("entityType", "TEAM")
                        .queryParam("snapshotId", SNAPSHOT_ID.toString())
                        .queryParam("providerEntityId", "team-1"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(anomalyPort.lastFilter)
                .isEqualTo(new AnomalyQueryPort.AnomalyQueryFilter(
                        AnomalyStatus.RESOLVED,
                        "provider",
                        NormalizationAnomalyCode.MISSING_MAPPING,
                        ProviderEntityType.TEAM,
                        SNAPSHOT_ID,
                        "team-1"));

        mvc.perform(get("/internal/catalog/provider-mappings")
                        .queryParam("status", "CONFIRMED")
                        .queryParam("provider", "provider")
                        .queryParam("entityType", "TEAM")
                        .queryParam("providerEntityId", "team-1")
                        .queryParam("season", "")
                        .queryParam("phase", "regular")
                        .queryParam("canonicalEntityId", CANONICAL_ID.toString()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(mappingPort.lastMappingFilter)
                .isEqualTo(new MappingQueryPort.MappingQueryFilter(
                        MappingStatus.CONFIRMED,
                        "provider",
                        ProviderEntityType.TEAM,
                        "team-1",
                        "",
                        "regular",
                        CANONICAL_ID));

        mvc.perform(get("/internal/catalog/mapping-decisions")
                        .queryParam("mappingId", MAPPING_ID.toString()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(mappingPort.lastDecisionFilter.mappingId())
                .isEqualTo(MAPPING_ID);

        mvc.perform(get("/internal/catalog/replay-requests")
                        .queryParam("status", "PENDING")
                        .queryParam("origin", "MANUAL")
                        .queryParam("snapshotId", SNAPSHOT_ID.toString())
                        .queryParam("mappingDecisionId", DECISION_ID.toString()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(replayPort.lastFilter)
                .isEqualTo(new ReplayQueryPort.ReplayQueryFilter(
                        NormalizationReplayStatus.PENDING,
                        NormalizationReplayOrigin.MANUAL,
                        SNAPSHOT_ID,
                        DECISION_ID));
    }

    @Test
    void problemDetailsAreRfc9457ShapedAndNeverEchoSubmittedValues() throws Exception {
        mvc.perform(post("/internal/catalog/replay-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "replay-secret-key")
                        .content("{\"payloadSha256\":\"not-a-hash\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "urn:betting-project:problem:INVALID_PAYLOAD_SHA256"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD_SHA256"))
                .andExpect(jsonPath("$.instance").value("/internal/catalog/replay-requests"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("not-a-hash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("replay-secret-key"))));
    }

    @Test
    void missingEmptyAndUnsupportedBodiesAreSafeBadRequests() throws Exception {
        mvc.perform(post("/internal/catalog/replay-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "replay-empty-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/internal/catalog/replay-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "replay-empty-2")
                        .content(new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/internal/catalog/replay-requests")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("Idempotency-Key", "replay-content-type")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTENT_TYPE"));
    }

    private static byte[] mappingBody() {
        return ("""
                {
                  "provider":"provider",
                  "entityType":"TEAM",
                  "providerEntityId":"team-1",
                  "decisionType":"CONFIRM",
                  "canonicalEntityId":"%s",
                  "expectedVersion":0,
                  "justification":"preuve exacte"
                }
                """.formatted(CANONICAL_ID)).getBytes(UTF_8);
    }

    private static SnapshotProvenance provenance() {
        return new SnapshotProvenance(
                SNAPSHOT_ID,
                "provider",
                "calendar",
                NOW.minusSeconds(1),
                NOW,
                NOW,
                200,
                25L,
                99L,
                SHA,
                "identity",
                "test-v1",
                NOW);
    }

    private static AnomalyQueryPort.AnomalyView anomalyView() {
        return new AnomalyQueryPort.AnomalyView(
                ANOMALY_ID,
                SNAPSHOT_ID,
                "provider",
                ProviderEntityType.TEAM,
                "team-1",
                null,
                null,
                NormalizationAnomalyCode.MISSING_MAPPING,
                "Missing exact mapping",
                AnomalyStatus.OPEN,
                1,
                NOW,
                NOW,
                NOW,
                null,
                1,
                provenance());
    }

    private static MappingQueryPort.MappingView mappingView() {
        return new MappingQueryPort.MappingView(
                MAPPING_ID,
                mappingKey(),
                CANONICAL_ID,
                1.0,
                MappingStatus.CONFIRMED,
                1,
                NOW,
                NOW);
    }

    private static MappingQueryPort.MappingDecisionView decisionView() {
        return new MappingQueryPort.MappingDecisionView(
                DECISION_ID,
                MAPPING_ID,
                mappingKey(),
                MappingDecisionType.CONFIRM,
                0,
                1,
                null,
                null,
                null,
                MappingStatus.CONFIRMED,
                CANONICAL_ID,
                1.0,
                "operator-test",
                "preuve [REDACTED]",
                NOW);
    }

    private static ProviderMappingDecision decisionDomain() {
        return new ProviderMappingDecision(
                DECISION_ID,
                MAPPING_ID,
                RECEIPT_ID,
                MappingDecisionType.CONFIRM,
                0,
                1,
                null,
                null,
                null,
                MappingStatus.CONFIRMED,
                CANONICAL_ID,
                1.0,
                "operator-test",
                "preuve [REDACTED]",
                NOW);
    }

    private static ProviderMappingKey mappingKey() {
        return new ProviderMappingKey(
                "provider", ProviderEntityType.TEAM, "team-1", null, null);
    }

    private static ReplayQueryPort.ReplayRequestView requestView(
            NormalizationReplayStatus status,
            long version,
            int attemptCount) {
        return new ReplayQueryPort.ReplayRequestView(
                REQUEST_ID,
                SNAPSHOT_ID,
                SHA,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                status,
                version,
                attemptCount,
                null,
                null,
                NOW,
                NOW,
                status.terminal() ? NOW : null,
                provenance());
    }

    private static NormalizationReplayRequest pendingRequest() {
        return NormalizationReplayRequest.pending(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                NOW);
    }

    private static NormalizationReplayRequest runningRequest() {
        return new NormalizationReplayRequest(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                NormalizationReplayStatus.RUNNING,
                2,
                1,
                null,
                null,
                NOW,
                NOW,
                null);
    }

    private static NormalizationReplayRequest completedRequest() {
        return new NormalizationReplayRequest(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                NormalizationReplayStatus.COMPLETED,
                3,
                1,
                null,
                null,
                NOW,
                NOW,
                NOW);
    }

    private static NormalizationReplayRequest failedRequest(NormalizationReplayStatus status) {
        return new NormalizationReplayRequest(
                REQUEST_ID,
                RECEIPT_ID,
                SNAPSHOT_ID,
                SHA,
                null,
                NormalizationReplayOrigin.MANUAL,
                NormalizationReplaySelectorType.SNAPSHOT_ID,
                SNAPSHOT_ID.toString(),
                status,
                3,
                1,
                "REPLAY_FAILED",
                "Replay failed safely",
                NOW,
                NOW,
                status.terminal() ? NOW : null);
    }

    private static NormalizationReplayAttempt completedAttempt() {
        return new NormalizationReplayAttempt(
                ATTEMPT_ID,
                REQUEST_ID,
                1,
                com.bettingproject.catalog.application.NormalizationReplayAttemptOutcome.COMPLETED,
                SHA,
                SHA,
                true,
                1,
                0,
                0,
                0,
                0,
                null,
                null,
                NOW,
                NOW);
    }

    private static NormalizationReplayAttempt failedAttempt(
            com.bettingproject.catalog.application.NormalizationReplayAttemptOutcome outcome) {
        return new NormalizationReplayAttempt(
                ATTEMPT_ID,
                REQUEST_ID,
                1,
                outcome,
                SHA,
                SHA,
                null,
                null,
                null,
                null,
                null,
                null,
                "REPLAY_FAILED",
                "Replay failed safely",
                NOW,
                NOW);
    }

    private ResultActions performReplayCreate(String idempotencyKey) throws Exception {
        return mvc.perform(post("/internal/catalog/replay-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content("{\"snapshotId\":\"" + SNAPSHOT_ID + "\"}"));
    }

    private ResultActions performResume(long expectedVersion) throws Exception {
        return mvc.perform(post("/internal/catalog/replay-requests/" + REQUEST_ID + "/resume")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + expectedVersion + "}"));
    }

    private static UUID id(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }

    private static final class FakeAnomalyPort implements AnomalyQueryPort {

        private boolean paginate;
        private AnomalyQueryFilter lastFilter;
        private int lastFetchLimit;
        private List<AnomalyEventView> events = List.of(new AnomalyEventView(
                ANOMALY_EVENT_ID,
                ANOMALY_ID,
                NormalizationAnomalyEventType.OPENED,
                null,
                AnomalyStatus.OPEN,
                APPLICATION_ID,
                "Opened",
                NOW));

        @Override
        public List<AnomalyView> fetchAnomalies(
                AnomalyQueryFilter filter,
                TimestampKeysetAnchor anchor,
                int fetchLimit) {
            lastFilter = filter;
            lastFetchLimit = fetchLimit;
            return paginate ? List.of(anomalyView(), anomalyView()) : List.of(anomalyView());
        }

        @Override
        public Optional<AnomalyView> findAnomaly(UUID anomalyId) {
            return ANOMALY_ID.equals(anomalyId) ? Optional.of(anomalyView()) : Optional.empty();
        }

        @Override
        public List<AnomalyEventView> fetchEvents(
                UUID anomalyId,
                TimestampKeysetAnchor anchor,
                int fetchLimit) {
            return events;
        }
    }

    private static final class FakeMappingPort implements MappingQueryPort {

        private MappingQueryFilter lastMappingFilter;
        private MappingDecisionQueryFilter lastDecisionFilter;
        private List<DecisionAnomalyView> correlated =
                List.of(new DecisionAnomalyView(anomalyView(), NOW));

        @Override
        public List<MappingView> fetchMappings(
                MappingQueryFilter filter,
                TimestampKeysetAnchor anchor,
                int fetchLimit) {
            lastMappingFilter = filter;
            return List.of(mappingView());
        }

        @Override
        public Optional<MappingView> findMapping(UUID mappingId) {
            return MAPPING_ID.equals(mappingId) ? Optional.of(mappingView()) : Optional.empty();
        }

        @Override
        public List<MappingDecisionView> fetchDecisions(
                MappingDecisionQueryFilter filter,
                TimestampKeysetAnchor anchor,
                int fetchLimit) {
            lastDecisionFilter = filter;
            return List.of(decisionView());
        }

        @Override
        public Optional<MappingDecisionView> findDecision(UUID decisionId) {
            return DECISION_ID.equals(decisionId) ? Optional.of(decisionView()) : Optional.empty();
        }

        @Override
        public List<DecisionAnomalyView> fetchCorrelatedAnomalies(
                UUID decisionId,
                CorrelationKeysetAnchor anchor,
                int fetchLimit) {
            return correlated;
        }
    }

    private static final class FakeReplayPort implements ReplayQueryPort {

        private ReplayRequestView request = requestView(NormalizationReplayStatus.PENDING, 1, 0);
        private ReplayQueryFilter lastFilter;
        private List<ReplayAttemptView> attempts = List.of(new ReplayAttemptView(
                ATTEMPT_ID,
                REQUEST_ID,
                1,
                com.bettingproject.catalog.application.NormalizationReplayAttemptOutcome.COMPLETED,
                SHA,
                SHA,
                true,
                1,
                0,
                0,
                0,
                0,
                null,
                null,
                NOW,
                NOW));

        @Override
        public List<ReplayRequestView> fetchRequests(
                ReplayQueryFilter filter,
                TimestampKeysetAnchor anchor,
                int fetchLimit) {
            lastFilter = filter;
            return List.of(request);
        }

        @Override
        public Optional<ReplayRequestView> findRequest(UUID requestId) {
            return REQUEST_ID.equals(requestId) ? Optional.of(request) : Optional.empty();
        }

        @Override
        public List<ReplayAttemptView> fetchAttempts(
                UUID requestId,
                AttemptKeysetAnchor anchor,
                int fetchLimit) {
            return attempts;
        }

        @Override
        public List<ReplayApplicationView> fetchApplications(
                UUID requestId,
                UUID attemptId,
                CorrelationKeysetAnchor anchor,
                int fetchLimit) {
            return List.of(new ReplayApplicationView(
                    APPLICATION_ID,
                    id(20),
                    id(21),
                    null,
                    FixtureApplicationOutcome.CREATED,
                    null,
                    CalendarAuthorityRole.PRIMARY,
                    "policy-v1",
                    NOW,
                    NOW));
        }

        @Override
        public List<ReplayAnomalyEventView> fetchAnomalyEvents(
                UUID requestId,
                UUID attemptId,
                CorrelationKeysetAnchor anchor,
                int fetchLimit) {
            return List.of(new ReplayAnomalyEventView(
                    REPLAY_EVENT_ID,
                    ANOMALY_ID,
                    NormalizationAnomalyEventType.OPENED,
                    null,
                    AnomalyStatus.OPEN,
                    APPLICATION_ID,
                    "Opened",
                    NOW,
                    NOW));
        }
    }

    private static final class FakeMappingDecisionService extends MappingDecisionService {

        private MappingDecisionResult result = new MappingDecisionResult.Invalid("NOT_CONFIGURED");
        private MappingDecisionCommand lastCommand;

        private FakeMappingDecisionService() {
            super(
                    (ProviderMappingRepository) null,
                    (CatalogRepository) null,
                    (ProviderMappingDecisionJournal) null,
                    (ControlCommandReceiptStore) null,
                    (MappingDecisionAnomalyStore) null,
                    (OperatorIdentityProvider) null,
                    (DecisionJustificationSanitizer) null,
                    (ControlCommandLock) null,
                    (CalendarNormalizationLock) null,
                    (MappingDecisionReplayPlanner) null,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        public MappingDecisionResult decide(MappingDecisionCommand command) {
            lastCommand = command;
            return result;
        }
    }

    private static final class FakeReplayRequestService extends NormalizationReplayRequestService {

        private NormalizationReplayRequestResult result =
                new NormalizationReplayRequestResult.Invalid("NOT_CONFIGURED");

        private FakeReplayRequestService() {
            super(
                    (StoredRawSnapshotReader) null,
                    (NormalizationReplayRequestRepository) null,
                    (ControlCommandReceiptStore) null,
                    (ControlCommandLock) null,
                    (NormalizationReplayAfterCommitExecutor) null,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        public NormalizationReplayRequestResult request(NormalizationReplayCommand command) {
            return result;
        }
    }

    private static final class FakeReplayExecutionService extends NormalizationReplayExecutionService {

        private NormalizationReplayExecutionResult result =
                new NormalizationReplayExecutionResult.Invalid("NOT_CONFIGURED");

        private FakeReplayExecutionService() {
            super(
                    (NormalizationReplayRequestRepository) null,
                    (StoredRawSnapshotReader) null,
                    (NormalizationReplayAttemptJournal) null,
                    (NormalizationReplayApplicationStore) null,
                    (NormalizationReplayAnomalyEventStore) null,
                    (CalendarNormalizationLock) null,
                    null,
                    (NormalizationReplaySavepoint) null,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        public NormalizationReplayExecutionResult resume(UUID requestId, long expectedVersion) {
            return result;
        }
    }
}
