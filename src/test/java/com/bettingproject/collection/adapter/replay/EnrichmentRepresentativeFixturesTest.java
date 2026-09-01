package com.bettingproject.collection.adapter.replay;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.bettingproject.collection.domain.SnapshotHasher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentRepresentativeFixturesTest {

    private static final String ROOT = "/fixtures/enr001/";

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void everyObservedProviderAndEndpointFamilyHasAValidSyntheticFixture() throws IOException {
        List<String> fixtures = List.of(
                "highlightly-detail-prematch.synthetic.json",
                "highlightly-lineup-absent.synthetic.json",
                "highlightly-lineup-complete.synthetic.json",
                "highlightly-statistics-reduced.synthetic.json",
                "highlightly-events.synthetic.json",
                "highlightly-box-score.synthetic.json",
                "football-data-detail.synthetic.json");

        for (String fixture : fixtures) {
            JsonNode root = fixture(fixture);
            assertThat(root.isObject() || root.isArray()).as(fixture).isTrue();
            assertThat(root.toString())
                    .as(fixture)
                    .doesNotContainIgnoringCase("x-auth-token")
                    .doesNotContainIgnoringCase("x-rapidapi-key")
                    .doesNotContain("C:\\Users\\")
                    .contains("example.invalid");
        }
    }

    @Test
    void fixtureManifestLinksSyntheticFilesToTheVersionedEvidenceIndex() throws IOException {
        Path repository = Path.of("").toAbsolutePath().normalize();
        JsonNode fixtureManifest = objectMapper.readTree(Files.readAllBytes(repository.resolve(
                "docs/benchmark/evidence/enr-001-representative-fixtures-v0.1.json")));
        JsonNode evidenceIndex = objectMapper.readTree(Files.readAllBytes(repository.resolve(
                "docs/benchmark/evidence/enr-001-evidence-index-v0.1.json")));
        Set<String> evidenceIds = new HashSet<>();
        evidenceIndex.path("entries").forEach(entry -> evidenceIds.add(entry.path("logicalId").asText()));
        Set<String> coveredFamilies = new HashSet<>();

        assertThat(fixtureManifest.path("synthetic").asBoolean()).isTrue();
        assertThat(fixtureManifest.path("fullPayloadsTrackedInGit").asBoolean()).isFalse();
        assertThat(fixtureManifest.path("fixtures")).hasSize(7);
        for (JsonNode entry : fixtureManifest.path("fixtures")) {
            Path fixturePath = repository.resolve(entry.path("path").asText()).normalize();
            assertThat(fixturePath).startsWith(repository);
            byte[] content = Files.readAllBytes(fixturePath);
            assertThat(SnapshotHasher.sha256(content)).isEqualTo(entry.path("fixtureSha256").asText());
            assertThat(evidenceIds).contains(entry.path("derivedFromLogicalEvidenceId").asText());
            coveredFamilies.add(entry.path("provider").asText() + ":" + entry.path("endpointFamily").asText());
        }
        assertThat(coveredFamilies).containsExactlyInAnyOrder(
                "Highlightly:DETAIL",
                "Highlightly:LINEUP",
                "Highlightly:STATISTICS",
                "Highlightly:EVENTS",
                "Highlightly:BOX_SCORE",
                "football-data.org:DETAIL");
    }

    @Test
    void lineupStatesDistinguishAbsentIncompleteAndComplete() throws IOException {
        JsonNode absent = fixture("highlightly-lineup-absent.synthetic.json");
        JsonNode complete = fixture("highlightly-lineup-complete.synthetic.json");
        JsonNode incomplete = complete.deepCopy();
        ((ArrayNode) incomplete.path("awayTeam").path("initialLineup").get(1)).remove(0);

        assertThat(lineupState(absent)).isEqualTo("ABSENT");
        assertThat(lineupState(incomplete)).isEqualTo("INCOMPLETE");
        assertThat(lineupState(complete)).isEqualTo("COMPLETE");
    }

    @Test
    void statisticsKeepTeamIdentityAndDistinguishZeroFromMissing() throws IOException {
        JsonNode statistics = fixture("highlightly-statistics-reduced.synthetic.json");

        assertThat(statistics.get(0).path("team").path("id").asLong()).isEqualTo(1002);
        assertThat(statistics.get(1).path("team").path("id").asLong()).isEqualTo(1001);
        JsonNode alphaStatistics = statistics.get(1).path("statistics");
        JsonNode offsides = findStatistic(alphaStatistics, "Offsides");

        assertThat(offsides.path("value").isNumber()).isTrue();
        assertThat(offsides.path("value").asInt()).isZero();
        assertThat(findStatistic(alphaStatistics, "Attacks").isMissingNode()).isTrue();
    }

    @Test
    void eventsPreserveSourceOrderAndAddedTimeNotation() throws IOException {
        JsonNode events = fixture("highlightly-events.synthetic.json");

        assertThat(List.of(
                events.get(0).path("time").asText(),
                events.get(1).path("time").asText(),
                events.get(2).path("time").asText())).containsExactly("12", "45+2", "70");
        assertThat(events.get(1).path("type").asText()).isEqualTo("Yellow Card");
        assertThat(events.get(1).path("assistingPlayerId").isNull()).isTrue();
    }

    @Test
    void boxScoreFixtureCoversTheThreeQualityQuarantines() throws IOException {
        JsonNode player = fixture("highlightly-box-score.synthetic.json")
                .get(1)
                .path("players")
                .get(0);
        JsonNode statistics = player.path("statistics");

        assertThat(player.path("fullName").isNull()).isTrue();
        assertThat(player.path("minutesPlayed").asInt()).isZero();
        assertThat(statistics.path("expectedGoals").asDouble()).isGreaterThan(0.0);
        assertThat(statistics.path("cardsYellow").asInt()).isEqualTo(1);
        assertThat(statistics.path("cardsSecondYellow").asInt()).isEqualTo(2);
    }

    @Test
    void providerDetailSchemasRemainIsolated() throws IOException {
        JsonNode highlightly = fixture("highlightly-detail-prematch.synthetic.json");
        JsonNode footballData = fixture("football-data-detail.synthetic.json");

        assertThat(highlightly.has("date")).isTrue();
        assertThat(highlightly.has("utcDate")).isFalse();
        assertThat(footballData.has("utcDate")).isTrue();
        assertThat(footballData.has("date")).isFalse();
        assertThat(highlightly.path("homeTeam").path("id").asLong()).isNotEqualTo(
                footballData.path("homeTeam").path("id").asLong());
    }

    private String lineupState(JsonNode root) {
        JsonNode home = root.path("homeTeam");
        JsonNode away = root.path("awayTeam");
        if (home.isMissingNode() || away.isMissingNode()) {
            return "UNKNOWN";
        }
        Set<Long> homePlayers = starterIds(home.path("initialLineup"));
        Set<Long> awayPlayers = starterIds(away.path("initialLineup"));
        if (homePlayers.isEmpty() && awayPlayers.isEmpty()) {
            return "ABSENT";
        }
        return homePlayers.size() == 11 && awayPlayers.size() == 11 ? "COMPLETE" : "INCOMPLETE";
    }

    private Set<Long> starterIds(JsonNode lines) {
        Set<Long> identifiers = new HashSet<>();
        if (!lines.isArray()) {
            return identifiers;
        }
        lines.forEach(line -> {
            if (line.isArray()) {
                line.forEach(player -> {
                    if (player.path("id").canConvertToLong()) {
                        identifiers.add(player.path("id").asLong());
                    }
                });
            }
        });
        return identifiers;
    }

    private JsonNode findStatistic(JsonNode statistics, String displayName) {
        for (JsonNode statistic : statistics) {
            if (displayName.equals(statistic.path("displayName").asText())) {
                return statistic;
            }
        }
        return MissingNode.getInstance();
    }

    private JsonNode fixture(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(ROOT + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing ENR-001 fixture: " + name);
            }
            return objectMapper.readTree(input);
        }
    }
}
