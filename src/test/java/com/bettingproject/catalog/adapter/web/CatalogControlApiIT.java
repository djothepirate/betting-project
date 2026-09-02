package com.bettingproject.catalog.adapter.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.bettingproject.collection.domain.SnapshotHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                "BETTING_OPERATOR_ID=control-api-it-operator"
        })
@ActiveProfiles("control-api")
class CatalogControlApiIT {

    private static final Instant BASE_TIME = Instant.parse("2026-09-02T08:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private MockMvc mvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcClient.sql("DELETE FROM normalization_replay_attempt_application").update();
        jdbcClient.sql("DELETE FROM normalization_replay_attempt_anomaly_event").update();
        jdbcClient.sql("DELETE FROM normalization_replay_attempt").update();
        jdbcClient.sql("DELETE FROM normalization_replay_request").update();
        jdbcClient.sql("DELETE FROM provider_mapping_decision_anomaly").update();
        jdbcClient.sql("DELETE FROM normalization_anomaly_event").update();
        jdbcClient.sql("DELETE FROM provider_mapping_decision").update();
        jdbcClient.sql("DELETE FROM control_command_receipt").update();
        jdbcClient.sql("DELETE FROM normalization_anomaly").update();
        jdbcClient.sql("DELETE FROM provider_mapping").update();
        jdbcClient.sql("DELETE FROM raw_snapshot").update();
        jdbcClient.sql("DELETE FROM canonical_team").update();
    }

    @Test
    void paginatedReadsExposeSafeSnapshotProvenanceWithoutRawPayload() throws Exception {
        UUID firstSnapshot = id(101);
        UUID secondSnapshot = id(102);
        UUID firstAnomaly = id(201);
        UUID secondAnomaly = id(202);
        byte[] firstPayload = "SYNTHETIC-PRIVATE-PAYLOAD-ONE".getBytes(UTF_8);
        byte[] secondPayload = "SYNTHETIC-PRIVATE-PAYLOAD-TWO".getBytes(UTF_8);
        insertSnapshot(firstSnapshot, firstPayload, BASE_TIME);
        insertSnapshot(secondSnapshot, secondPayload, BASE_TIME);
        insertOpenAnomaly(firstAnomaly, firstSnapshot, "fixture-1", BASE_TIME);
        insertOpenAnomaly(secondAnomaly, secondSnapshot, "fixture-2", BASE_TIME);

        MvcResult firstPage = mvc.perform(get("/internal/catalog/anomalies")
                        .queryParam("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(secondAnomaly.toString()))
                .andExpect(jsonPath("$.items[0].snapshot.id").value(secondSnapshot.toString()))
                .andExpect(jsonPath("$.items[0].snapshot.payloadSha256")
                        .value(SnapshotHasher.sha256(secondPayload)))
                .andExpect(jsonPath("$.items[0].snapshot.payload").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isString())
                .andExpect(content().string(not(containsString("SYNTHETIC-PRIVATE-PAYLOAD"))))
                .andReturn();

        String cursor = json(firstPage).get("nextCursor").asString();
        mvc.perform(get("/internal/catalog/anomalies")
                        .queryParam("limit", "1")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(firstAnomaly.toString()))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(content().string(not(containsString("SYNTHETIC-PRIVATE-PAYLOAD"))));
    }

    @Test
    void humanMappingDecisionUsesConfiguredOperatorAndOnlyReturnsSanitizedHistory()
            throws Exception {
        UUID teamId = id(301);
        insertCanonicalTeam(teamId);
        String body = """
                {
                  "provider":"synthetic-provider",
                  "entityType":"TEAM",
                  "providerEntityId":"team-external-1",
                  "decisionType":"CONFIRM",
                  "canonicalEntityId":"%s",
                  "expectedVersion":0,
                  "justification":"review api_key=topsecret approved"
                }
                """.formatted(teamId);

        MvcResult applied = mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "control-api-it-mapping-1")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.decision.operatorId")
                        .value("control-api-it-operator"))
                .andExpect(jsonPath("$.decision.justification")
                        .value("review api_key=[REDACTED] approved"))
                .andExpect(jsonPath("$.decision.controlCommandReceiptId").doesNotExist())
                .andExpect(content().string(not(containsString("topsecret"))))
                .andExpect(content().string(not(containsString("control-api-it-mapping-1"))))
                .andReturn();

        JsonNode appliedJson = json(applied);
        String decisionId = appliedJson.get("decision").get("id").asString();
        String mappingId = appliedJson.get("decision").get("providerMappingId").asString();

        mvc.perform(get("/internal/catalog/mapping-decisions")
                        .queryParam("mappingId", mappingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(decisionId))
                .andExpect(jsonPath("$.items[0].operatorId")
                        .value("control-api-it-operator"))
                .andExpect(jsonPath("$.items[0].justification")
                        .value("review api_key=[REDACTED] approved"))
                .andExpect(jsonPath("$.items[0].controlCommandReceiptId").doesNotExist())
                .andExpect(content().string(not(containsString("topsecret"))));

        mvc.perform(post("/internal/catalog/mapping-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "control-api-it-mapping-1")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.id").value(decisionId));

        assertThatCount("provider_mapping", 1);
        assertThatCount("provider_mapping_decision", 1);
    }

    @Test
    void replayCreationRunsTheRealCallbackAndDurableIdempotentReadbackIsConfined()
            throws Exception {
        UUID firstSnapshot = id(401);
        UUID secondSnapshot = id(402);
        byte[] firstPayload = "{not-valid-json-one".getBytes(UTF_8);
        byte[] secondPayload = "{not-valid-json-two".getBytes(UTF_8);
        insertSnapshot(firstSnapshot, firstPayload, BASE_TIME);
        insertSnapshot(secondSnapshot, secondPayload, BASE_TIME.plusSeconds(1));

        MvcResult firstCreation = createReplay(
                firstSnapshot, "control-api-it-replay-1", 202);
        String firstRequestId = json(firstCreation).get("id").asString();
        org.assertj.core.api.Assertions.assertThat(
                        json(firstCreation).get("status").asString())
                .isEqualTo("COMPLETED");
        MvcResult firstAttempts = mvc.perform(get(
                        "/internal/catalog/replay-requests/{id}/attempts", firstRequestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].outcome").value("COMPLETED"))
                .andReturn();
        String firstAttemptId = json(firstAttempts).get("items").get(0).get("id").asString();

        mvc.perform(post("/internal/catalog/replay-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "control-api-it-replay-1")
                        .content("{\"snapshotId\":\"" + firstSnapshot + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstRequestId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.controlCommandReceiptId").doesNotExist())
                .andExpect(jsonPath("$.snapshot.payload").doesNotExist())
                .andExpect(content().string(not(containsString("{not-valid-json-one"))));

        mvc.perform(get(
                        "/internal/catalog/replay-requests/{requestId}/attempts/{attemptId}/anomaly-events",
                        firstRequestId,
                        firstAttemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventType").value("OPENED"));

        MvcResult secondCreation = createReplay(
                secondSnapshot, "control-api-it-replay-2", 202);
        String secondRequestId = json(secondCreation).get("id").asString();
        mvc.perform(get(
                        "/internal/catalog/replay-requests/{requestId}/attempts/{attemptId}/anomaly-events",
                        secondRequestId,
                        firstAttemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(get(
                        "/internal/catalog/replay-requests/{requestId}/attempts/{attemptId}/applications",
                        firstRequestId,
                        firstAttemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void retryableReplayRequiresTheStoredVersionAndResumesThroughTheRealService()
            throws Exception {
        UUID snapshotId = id(501);
        UUID requestId = id(502);
        byte[] payload = "{retry-now-completes-as-invalid-snapshot".getBytes(UTF_8);
        String sha256 = SnapshotHasher.sha256(payload);
        insertSnapshot(snapshotId, payload, BASE_TIME);
        insertRetryableReplay(requestId, snapshotId, sha256);

        mvc.perform(post("/internal/catalog/replay-requests/{id}/resume", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mvc.perform(post("/internal/catalog/replay-requests/{id}/resume", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.attemptCount").value(2));

        mvc.perform(get("/internal/catalog/replay-requests/{id}/attempts", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].attemptNumber").value(2))
                .andExpect(jsonPath("$.items[0].outcome").value("COMPLETED"))
                .andExpect(jsonPath("$.items[1].attemptNumber").value(1))
                .andExpect(jsonPath("$.items[1].outcome").value("FAILED_RETRYABLE"));
    }

    private MvcResult createReplay(UUID snapshotId, String key, int expectedStatus)
            throws Exception {
        return mvc.perform(post("/internal/catalog/replay-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content("{\"snapshotId\":\"" + snapshotId + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.snapshot.id").value(snapshotId.toString()))
                .andExpect(jsonPath("$.snapshot.payload").doesNotExist())
                .andReturn();
    }

    private void insertCanonicalTeam(UUID teamId) {
        jdbcClient.sql("""
                INSERT INTO canonical_team (
                    id, canonical_name, country_code, created_at, updated_at
                ) VALUES (
                    :id, 'Synthetic Team', 'TST', :now, :now
                )
                """)
                .param("id", teamId)
                .param("now", utc(BASE_TIME))
                .update();
    }

    private void insertSnapshot(UUID snapshotId, byte[] payload, Instant receivedAt) {
        jdbcClient.sql("""
                INSERT INTO raw_snapshot (
                    id, provider, endpoint, requested_at, received_at, source_observed_at,
                    http_status, latency_ms, quota_remaining, payload_sha256,
                    payload_compression, payload, connector_version, created_at
                ) VALUES (
                    :id, 'synthetic-provider', 'calendar', :receivedAt, :receivedAt, NULL,
                    200, 4, 90, :sha256, 'identity', :payload, 'control-api-it-v1', :receivedAt
                )
                """)
                .param("id", snapshotId)
                .param("receivedAt", utc(receivedAt))
                .param("sha256", SnapshotHasher.sha256(payload))
                .param("payload", payload)
                .update();
    }

    private void insertOpenAnomaly(
            UUID anomalyId,
            UUID snapshotId,
            String providerEntityId,
            Instant createdAt) {
        jdbcClient.sql("""
                INSERT INTO normalization_anomaly (
                    id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                    season, phase, anomaly_code, details, status, version, created_at,
                    last_seen_at, updated_at, resolved_at, occurrence_count
                ) VALUES (
                    :id, :snapshotId, 'synthetic-provider', 'FIXTURE', :providerEntityId,
                    '2026', 'REGULAR', 'MISSING_MAPPING', 'Synthetic missing mapping',
                    'OPEN', 1, :createdAt, :createdAt, :createdAt, NULL, 1
                )
                """)
                .param("id", anomalyId)
                .param("snapshotId", snapshotId)
                .param("providerEntityId", providerEntityId)
                .param("createdAt", utc(createdAt))
                .update();
    }

    private void insertRetryableReplay(
            UUID requestId,
            UUID snapshotId,
            String sha256) {
        UUID receiptId = id(503);
        UUID attemptId = id(504);
        Instant createdAt = Instant.now().minusSeconds(60);
        jdbcClient.sql("""
                INSERT INTO control_command_receipt (
                    id, idempotency_key, command_type, command_sha256,
                    result_resource_id, created_at
                ) VALUES (
                    :id, 'control-api-it-retry-seed', 'REPLAY_REQUEST',
                    :sha256, :requestId, :createdAt
                )
                """)
                .param("id", receiptId)
                .param("sha256", "f".repeat(64))
                .param("requestId", requestId)
                .param("createdAt", utc(createdAt))
                .update();
        jdbcClient.sql("""
                INSERT INTO normalization_replay_request (
                    id, control_command_receipt_id, raw_snapshot_id,
                    expected_payload_sha256, provider_mapping_decision_id,
                    origin, selector_type, selector_value, status, version,
                    attempt_count, last_error_code, last_error_message,
                    created_at, updated_at, completed_at
                ) VALUES (
                    :id, :receiptId, :snapshotId, :sha256, NULL,
                    'MANUAL', 'SNAPSHOT_ID', :selector, 'FAILED_RETRYABLE', 2,
                    1, 'NORMALIZATION_FAILED', 'Synthetic retryable failure',
                    :createdAt, :createdAt, NULL
                )
                """)
                .param("id", requestId)
                .param("receiptId", receiptId)
                .param("snapshotId", snapshotId)
                .param("sha256", sha256)
                .param("selector", snapshotId.toString())
                .param("createdAt", utc(createdAt))
                .update();
        jdbcClient.sql("""
                INSERT INTO normalization_replay_attempt (
                    id, normalization_replay_request_id, attempt_number, outcome,
                    expected_payload_sha256, actual_payload_sha256, compatible,
                    fixtures_created, fixtures_updated, fixtures_unchanged,
                    fixtures_blocked, anomalies, error_code, error_message,
                    started_at, finished_at
                ) VALUES (
                    :id, :requestId, 1, 'FAILED_RETRYABLE', :sha256, :sha256,
                    NULL, NULL, NULL, NULL, NULL, NULL,
                    'NORMALIZATION_FAILED', 'Synthetic retryable failure',
                    :createdAt, :createdAt
                )
                """)
                .param("id", attemptId)
                .param("requestId", requestId)
                .param("sha256", sha256)
                .param("createdAt", utc(createdAt))
                .update();
    }

    private void assertThatCount(String table, int expected) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Integer.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static UUID id(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
