package com.bettingproject.catalog.adapter.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bettingproject.catalog.application.MappingDecisionCommand;
import com.bettingproject.catalog.application.NormalizationReplayCommand;
import com.bettingproject.catalog.application.NormalizationReplaySelector;
import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.ProviderEntityType;
import org.junit.jupiter.api.Test;

class StrictCatalogCommandParserTest {

    private final StrictCatalogCommandParser parser = new StrictCatalogCommandParser();

    @Test
    void parsesStrictMappingConfirmation() {
        MappingDecisionCommand command = parser.parseMappingDecision(bytes("""
                {
                  "provider":"fixture-provider",
                  "entityType":"TEAM",
                  "providerEntityId":"team-1",
                  "season":null,
                  "phase":"",
                  "decisionType":"CONFIRM",
                  "canonicalEntityId":"00000000-0000-0000-0000-000000000001",
                  "expectedVersion":2,
                  "justification":"  preuve exacte  "
                }
                """), "mapping-confirm-1");

        assertThat(command.mappingKey().provider()).isEqualTo("fixture-provider");
        assertThat(command.mappingKey().entityType()).isEqualTo(ProviderEntityType.TEAM);
        assertThat(command.mappingKey().season()).isEmpty();
        assertThat(command.decisionType()).isEqualTo(MappingDecisionType.CONFIRM);
        assertThat(command.expectedVersion()).isEqualTo(2);
        assertThat(command.justification()).isEqualTo("preuve exacte");
    }

    @Test
    void parsesExactlyOneReplaySelectorAndResumeVersion() {
        NormalizationReplayCommand byId = parser.parseReplayRequest(bytes("""
                {"snapshotId":"00000000-0000-0000-0000-000000000001"}
                """), "replay-1");
        NormalizationReplayCommand byHash = parser.parseReplayRequest(bytes("""
                {"payloadSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                """), "replay-2");

        assertThat(byId.selector()).isInstanceOf(NormalizationReplaySelector.BySnapshotId.class);
        assertThat(byHash.selector()).isInstanceOf(NormalizationReplaySelector.ByPayloadSha256.class);
        assertThat(parser.parseReplayResume(bytes("{" + "\"expectedVersion\":3}")))
                .isEqualTo(3);
    }

    @Test
    void rejectsForbiddenLocationFieldAtAnyDepthBeforeUnknownFieldHandling() {
        assertFailure(
                () -> parser.parseReplayRequest(
                        bytes("{\"selector\":{\"path\":\"C:/sensitive\"}}"),
                        "replay-1"),
                400,
                "ARBITRARY_PATH_FORBIDDEN");
        assertFailure(
                () -> parser.parseMappingDecision(
                        bytes("{\"filePath\":\"secret\"}"),
                        "mapping-1"),
                400,
                "ARBITRARY_PATH_FORBIDDEN");
    }

    @Test
    void rejectsUnknownDuplicateTrailingAndCoercedJson() {
        assertFailure(
                () -> parser.parseReplayResume(bytes("{\"expectedVersion\":1,\"other\":2}")),
                400,
                "UNKNOWN_FIELD");
        assertFailure(
                () -> parser.parseReplayResume(bytes(
                        "{\"expectedVersion\":1,\"expectedVersion\":2}")),
                400,
                "INVALID_JSON");
        assertFailure(
                () -> parser.parseReplayResume(bytes("{\"expectedVersion\":1} {}")),
                400,
                "INVALID_JSON");
        assertFailure(
                () -> parser.parseReplayResume(bytes("{\"expectedVersion\":\"1\"}")),
                400,
                "INVALID_FIELD");
    }

    @Test
    void rejectsInvalidDecisionCardinalityAndSnapshotMapping() {
        String rejectWithTarget = """
                {
                  "provider":"provider",
                  "entityType":"TEAM",
                  "providerEntityId":"team-1",
                  "decisionType":"REJECT",
                  "canonicalEntityId":"00000000-0000-0000-0000-000000000001",
                  "expectedVersion":1,
                  "justification":"preuve"
                }
                """;
        String snapshot = """
                {
                  "provider":"provider",
                  "entityType":"SNAPSHOT",
                  "providerEntityId":"snapshot-1",
                  "decisionType":"REJECT",
                  "expectedVersion":0,
                  "justification":"preuve"
                }
                """;

        assertFailure(
                () -> parser.parseMappingDecision(bytes(rejectWithTarget), "mapping-1"),
                400,
                "INVALID_MAPPING_DECISION");
        assertFailure(
                () -> parser.parseMappingDecision(bytes(snapshot), "mapping-2"),
                422,
                "SNAPSHOT_NOT_MAPPABLE");
        assertFailure(
                () -> parser.parseMappingDecision(
                        bytes("{\"provider\":\"provider\",\"entityType\":\"SNAPSHOT\"}"),
                        "mapping-3"),
                400,
                "INVALID_FIELD");
    }

    @Test
    void rejectsInvalidIdempotencyAndReplaySelectorCardinality() {
        assertFailure(
                () -> parser.parseReplayRequest(
                        bytes("{\"snapshotId\":\"00000000-0000-0000-0000-000000000001\"}"),
                        "contains space"),
                400,
                "INVALID_IDEMPOTENCY_KEY");
        assertFailure(
                () -> parser.parseReplayRequest(bytes("""
                        {
                          "snapshotId":"00000000-0000-0000-0000-000000000001",
                          "payloadSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        }
                        """), "replay-1"),
                400,
                "INVALID_REPLAY_SELECTOR");
    }

    private byte[] bytes(String json) {
        return json.getBytes(UTF_8);
    }

    private void assertFailure(
            Runnable operation,
            int expectedStatus,
            String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(CatalogWebException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(expectedStatus);
                    assertThat(exception.code()).isEqualTo(expectedCode);
                });
    }
}
