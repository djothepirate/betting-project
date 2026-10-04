package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentParseException;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ParsedEvents;
import com.bettingproject.enrichment.domain.ParsedMatchDetail;
import com.bettingproject.enrichment.domain.ParsedPlayerStatistics;
import com.bettingproject.enrichment.domain.ParsedTeamStatistics;
import com.bettingproject.enrichment.domain.ParsedLineup;
import com.bettingproject.enrichment.domain.LineupStatus;
import com.bettingproject.qualification.domain.QualityIssueCode;
import com.bettingproject.qualification.domain.PlayerQualityAssessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictEnrichmentParsersTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-01-15T18:59:00Z");

    private final StrictHighlightlyTeamStatisticsParser teamStatsParser = new StrictHighlightlyTeamStatisticsParser();
    private final StrictHighlightlyLineupParser lineupParser = new StrictHighlightlyLineupParser();
    private final LegacyStrictHighlightlyLineupParser legacyLineupParser = new LegacyStrictHighlightlyLineupParser();
    private final StrictHighlightlyEventsParser eventsParser = new StrictHighlightlyEventsParser();
    private final StrictHighlightlyPlayerStatisticsParser playerStatsParser =
            new StrictHighlightlyPlayerStatisticsParser();
    private final StrictHighlightlyDetailParser highlightlyDetailParser = new StrictHighlightlyDetailParser();
    private final StrictFootballDataDetailParser footballDataDetailParser = new StrictFootballDataDetailParser();

    @Test
    void teamStatisticsUseExactTeamIdsAndPreserveZeroAndUnknownMetricNames() throws IOException {
        ParsedTeamStatistics parsed = teamStatsParser.parse(fixture("highlightly-statistics-reduced.synthetic.json"),
                null, RECEIVED_AT);

        assertThat(parsed.state()).isEqualTo(EnrichmentObservationState.AVAILABLE);
        assertThat(parsed.teams()).extracting("providerTeamId").containsExactly("1002", "1001");
        var alpha = parsed.teams().get(1);
        assertThat(alpha.statistics()).filteredOn(metric -> metric.sourceName().equals("Offsides"))
                .singleElement().satisfies(metric -> {
                    assertThat(metric.value().state()).isEqualTo(EnrichmentObservationState.AVAILABLE);
                    assertThat(metric.value().value()).isEqualTo("0");
                });
        assertThat(alpha.statistics()).noneMatch(metric -> metric.sourceName().equals("Attacks"));
    }

    @Test
    void teamStatisticsDistinguishMissingNullEmptyAndAvailableValues() throws IOException {
        String source = "[{\"team\":{\"id\":1},\"statistics\":["
                + "{\"displayName\":\"Zero\",\"value\":0},"
                + "{\"displayName\":\"Null\",\"value\":null},"
                + "{\"displayName\":\"Missing\"}]},"
                + "{\"team\":{\"id\":2},\"statistics\":[]}]";
        ParsedTeamStatistics parsed = teamStatsParser.parse(source.getBytes(StandardCharsets.UTF_8), null, RECEIVED_AT);

        assertThat(parsed.state()).isEqualTo(EnrichmentObservationState.PARTIAL);
        assertThat(parsed.teams().get(0).statistics().get(0).value().value()).isEqualTo("0");
        assertThat(parsed.teams().get(0).statistics().get(1).value().state())
                .isEqualTo(EnrichmentObservationState.NULL_VALUE);
        assertThat(parsed.teams().get(0).statistics().get(2).value().state())
                .isEqualTo(EnrichmentObservationState.NOT_PRESENT);
        assertThat(parsed.teams().get(1).state()).isEqualTo(EnrichmentObservationState.EMPTY);
    }

    @Test
    void reducedTeamAndDeepPlayerFamiliesKeepDistinctObservationStatesAndExactTeamReferences() throws IOException {
        ParsedTeamStatistics reduced = teamStatsParser.parse(fixture("highlightly-statistics-reduced.synthetic.json"),
                null, RECEIVED_AT);
        String deepJson = """
                [
                  {"team":{"id":101},"players":[
                    {"id":1,"name":"One","statistics":{"zero":0,"nullable":null}},
                    {"id":2,"name":"Two"},
                    {"id":3,"name":"Three","statistics":null},
                    {"id":4,"name":"Four","statistics":{}}
                  ]}
                ]
                """;
        ParsedPlayerStatistics deep = playerStatsParser.parse(deepJson.getBytes(StandardCharsets.UTF_8), null, RECEIVED_AT);

        assertThat(reduced.teams()).extracting("providerTeamId").containsExactly("1002", "1001");
        assertThat(deep.teams()).extracting("providerTeamId").containsExactly("101");
        assertThat(deep.teams().getFirst().players()).extracting("statisticsState")
                .containsExactly(EnrichmentObservationState.AVAILABLE, EnrichmentObservationState.NOT_PRESENT,
                        EnrichmentObservationState.NULL_VALUE, EnrichmentObservationState.EMPTY);
        var metrics = deep.teams().getFirst().players().getFirst().statistics();
        assertThat(metrics).extracting(metric -> metric.value().state())
                .containsExactly(EnrichmentObservationState.AVAILABLE, EnrichmentObservationState.NULL_VALUE);
        assertThat(metrics.getFirst().value().value()).isEqualTo("0");
    }

    @Test
    void eventsRetainProviderOrderAddedTimeAndExplicitNulls() throws IOException {
        ParsedEvents parsed = eventsParser.parse(fixture("highlightly-events.synthetic.json"), null, RECEIVED_AT);

        assertThat(parsed.events()).extracting(event -> event.sourceTimeNotation().value())
                .containsExactly("12", "45+2", "70");
        assertThat(parsed.events().get(1).sourceType().value()).isEqualTo("Yellow Card");
        assertThat(parsed.events().get(1).assistingPlayerId().state())
                .isEqualTo(EnrichmentObservationState.NULL_VALUE);
        assertThat(parsed.events().get(0).playerId().value()).isEqualTo("1109");
    }

    @Test
    void playerStatisticsRetainTeamGroupingAndProduceLocalQuarantineFindings() throws IOException {
        ParsedPlayerStatistics parsed = playerStatsParser.parse(fixture("highlightly-box-score.synthetic.json"),
                null, RECEIVED_AT);

        assertThat(parsed.state()).isEqualTo(EnrichmentObservationState.AVAILABLE);
        assertThat(parsed.teams()).extracting("providerTeamId").containsExactly("1001", "1002");
        var substitute = parsed.teams().get(1).players().getFirst();
        assertThat(substitute.minutesPlayed().value()).isEqualTo("0");
        assertThat(substitute.statistics()).filteredOn(metric -> metric.sourceName().equals("expectedGoals"))
                .singleElement().satisfies(metric -> assertThat(metric.value().value()).isEqualTo("0.2"));
        assertThat(PlayerQualityAssessor.assess(substitute)).containsExactlyInAnyOrder(
                QualityIssueCode.MISSING_PLAYER_FULL_NAME,
                QualityIssueCode.ZERO_MINUTE_EXPECTED_METRICS,
                QualityIssueCode.INVALID_SECOND_YELLOW_VALUE);
        assertThat(substitute.statisticsState()).isEqualTo(EnrichmentObservationState.AVAILABLE);
    }

    @Test
    void detailParsersKeepNativeSeasonPhaseAndStatusWithoutLogicalConversion() throws IOException {
        ParsedMatchDetail highlightly = highlightlyDetailParser.parse(
                fixture("highlightly-detail-prematch.synthetic.json"), null, RECEIVED_AT);
        ParsedMatchDetail footballData = footballDataDetailParser.parse(
                fixture("football-data-detail.synthetic.json"), null, RECEIVED_AT);

        assertThat(highlightly.providerFixtureId()).isEqualTo("900001");
        assertThat(highlightly.providerCompetitionId()).isEqualTo("2001");
        assertThat(highlightly.sourceSeasonReference().value()).isEqualTo("2026");
        assertThat(highlightly.sourcePhaseReference().value()).isEqualTo("Final");
        assertThat(highlightly.providerStatus().value()).isEqualTo("Not started");
        assertThat(highlightly.scheduledKickoff()).isEqualTo(Instant.parse("2026-01-15T19:00:00Z"));
        assertThat(highlightly.competitionCountryCode().value()).isEqualTo("ZZ");
        assertThat(highlightly.homeTeam().countryCode().state()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);

        assertThat(footballData.providerFixtureId()).isEqualTo("800001");
        assertThat(footballData.providerCompetitionId()).isEqualTo("4001");
        assertThat(footballData.sourceSeasonReference().value()).isEqualTo("5001");
        assertThat(footballData.sourcePhaseReference().value()).isEqualTo("REGULAR_SEASON");
        assertThat(footballData.providerStatus().value()).isEqualTo("TIMED");
        assertThat(footballData.competitionCountryCode().value()).isEqualTo("ZZ");
        assertThat(footballData.statisticsState()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);
    }

    @Test
    void detailScoreNullIsNotRewrittenAsZeroOrMissing() throws IOException {
        ParsedMatchDetail detail = footballDataDetailParser.parse(
                fixture("football-data-detail.synthetic.json"), null, RECEIVED_AT);

        assertThat(detail.sourceHomeScore().state()).isEqualTo(EnrichmentObservationState.NULL_VALUE);
        assertThat(detail.sourceAwayScore().state()).isEqualTo(EnrichmentObservationState.NULL_VALUE);
    }

    @Test
    void missingDescriptiveMetadataDoesNotBlockStrictDetailParsing() throws IOException {
        String original = new String(fixture("football-data-detail.synthetic.json"), StandardCharsets.UTF_8);
        String withoutArea = original.replace("\"area\": {\n    \"id\": 3001,\n    \"name\": \"Sample Area\",\n    \"code\": \"ZZ\",\n    \"flag\": \"https://example.invalid/areas/zz.svg\"\n  },",
                "\"area\": null,");
        ParsedMatchDetail parsed = footballDataDetailParser.parse(withoutArea.getBytes(StandardCharsets.UTF_8),
                null, RECEIVED_AT);

        assertThat(parsed.competitionCountryCode().state()).isEqualTo(EnrichmentObservationState.NULL_VALUE);
        assertThat(parsed.homeTeam().countryCode().state()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);
    }

    @Test
    void unknownCompetitionAndTeamCountryMetadataDoesNotBlockEitherNativeDetailParser() throws IOException {
        String highlightlyJson = new String(fixture("highlightly-detail-prematch.synthetic.json"), StandardCharsets.UTF_8)
                .replace("  \"country\": {\n    \"code\": \"ZZ\",\n    \"name\": \"Sample Country\",\n    \"logo\": \"https://example.invalid/countries/zz.svg\"\n  },\n", "");
        // These descriptive fields are optional; exact fixture/competition/season references remain required.
        ParsedMatchDetail highlightly = highlightlyDetailParser.parse(
                highlightlyJson.getBytes(StandardCharsets.UTF_8), null, RECEIVED_AT);

        String footballDataJson = new String(fixture("football-data-detail.synthetic.json"), StandardCharsets.UTF_8)
                .replace("  \"area\": {\n    \"id\": 3001,\n    \"name\": \"Sample Area\",\n    \"code\": \"ZZ\",\n    \"flag\": \"https://example.invalid/areas/zz.svg\"\n  },\n", "")
                .replace("    \"type\": \"LEAGUE\",\n", "");
        ParsedMatchDetail footballData = footballDataDetailParser.parse(
                footballDataJson.getBytes(StandardCharsets.UTF_8), null, RECEIVED_AT);

        assertThat(highlightly.competitionCountryCode().state()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);
        assertThat(highlightly.competitionType().state()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);
        assertThat(highlightly.homeTeam().countryCode().state()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);
        assertThat(footballData.competitionCountryCode().state()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);
        assertThat(footballData.competitionType().state()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);
        assertThat(footballData.homeTeam().countryCode().state()).isEqualTo(EnrichmentObservationState.NOT_PRESENT);
        assertThat(highlightly.providerFixtureId()).isEqualTo("900001");
        assertThat(footballData.providerFixtureId()).isEqualTo("800001");
        assertThat(highlightly.sourceSeasonReference().value()).isEqualTo("2026");
        assertThat(footballData.sourceSeasonReference().value()).isEqualTo("5001");
    }

    @Test
    void lineupParserClassifiesLateAvailabilityWithoutPromotingItToPrematch() throws IOException {
        Instant kickoff = Instant.parse("2026-01-15T19:00:00Z");
        ParsedLineup onTime = lineupParser.parse(fixture("highlightly-lineup-complete.synthetic.json"),
                kickoff, kickoff.minusSeconds(1));
        ParsedLineup atKickoff = lineupParser.parse(fixture("highlightly-lineup-complete.synthetic.json"),
                kickoff, kickoff);
        ParsedLineup afterKickoff = lineupParser.parse(fixture("highlightly-lineup-complete.synthetic.json"),
                kickoff, kickoff.plusNanos(1));
        ParsedLineup absent = lineupParser.parse(fixture("highlightly-lineup-absent.synthetic.json"),
                kickoff, kickoff.minusSeconds(1));

        assertThat(onTime.assessment().status()).isEqualTo(LineupStatus.COMPLETE);
        assertThat(atKickoff.assessment().status()).isEqualTo(LineupStatus.COMPLETE_LATE);
        assertThat(atKickoff.assessment().isPrematchComplete()).isFalse();
        assertThat(afterKickoff.assessment().status()).isEqualTo(LineupStatus.COMPLETE_LATE);
        assertThat(afterKickoff.assessment().isPrematchComplete()).isFalse();
        assertThat(absent.assessment().status()).isEqualTo(LineupStatus.ABSENT);
    }

    @Test
    void lineupV2RetainsExactNameAndRoleWhileV1ReplayRemainsIdsOnly() throws IOException {
        byte[] fixture = fixture("highlightly-lineup-complete.synthetic.json");
        Instant kickoff = Instant.parse("2026-01-15T19:00:00Z");
        ParsedLineup current = lineupParser.parse(fixture, kickoff, kickoff.minusSeconds(1));
        ParsedLineup legacy = legacyLineupParser.parse(fixture, kickoff, kickoff.minusSeconds(1));

        assertThat(lineupParser.version()).isEqualTo("highlightly-lineup-v2");
        assertThat(legacyLineupParser.version()).isEqualTo("highlightly-lineup-v1");
        assertThat(legacyLineupParser.preferredForCollection()).isFalse();
        assertThat(current.home().starterEvidence()).hasSize(11);
        assertThat(current.home().starterEvidence().getFirst().fullName().value()).isEqualTo("Alpha 1");
        assertThat(current.home().starterEvidence().getFirst().role().value()).isEqualTo("Goalkeeper");
        assertThat(legacy.home().starterProviderIds()).containsExactlyElementsOf(current.home().starterProviderIds());
        assertThat(legacy.home().starterEvidence()).isEmpty();
    }

    @Test
    void lineupIdentityEvidenceSurvivesRepresentationReplayWhileLegacyEmptyEvidenceIsOmitted() throws Exception {
        byte[] source = fixture("highlightly-lineup-complete.synthetic.json");
        Instant kickoff = Instant.parse("2026-01-15T19:00:00Z");
        JsonMapper mapper = JsonMapper.builder().build();
        ParsedLineup current = lineupParser.parse(source, kickoff, kickoff.minusSeconds(1));
        ParsedLineup legacy = legacyLineupParser.parse(source, kickoff, kickoff.minusSeconds(1));

        String currentJson = mapper.writeValueAsString(current);
        String legacyJson = mapper.writeValueAsString(legacy);
        var currentTree = mapper.readTree(currentJson);

        assertThat(currentTree.get("home").get("starterEvidence").size()).isEqualTo(11);
        assertThat(currentTree.get("home").get("starterEvidence").get(10).get("fullName").get("value").textValue())
                .isEqualTo("Alpha 9");
        assertThat(currentTree.get("home").get("starterEvidence").get(10).get("role").get("value").textValue())
                .isEqualTo("Forward");
        assertThat(legacyJson).doesNotContain("starterEvidence");
        assertThat(current.home().starterProviderIds()).containsExactlyElementsOf(legacy.home().starterProviderIds());
    }

    @Test
    void allParsersRejectDuplicateKeysTrailingDocumentsAndWrongRootTypes() throws IOException {
        byte[] duplicate = "[{\"team\":{\"id\":1,\"id\":2},\"statistics\":[]}]"
                .getBytes(StandardCharsets.UTF_8);
        byte[] trailing = (new String(fixture("highlightly-events.synthetic.json"), StandardCharsets.UTF_8) + " {}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] wrongRoot = "{}".getBytes(StandardCharsets.UTF_8);

        assertIncompatible(() -> teamStatsParser.parse(duplicate, null, RECEIVED_AT));
        assertIncompatible(() -> eventsParser.parse(trailing, null, RECEIVED_AT));
        assertIncompatible(() -> playerStatsParser.parse(wrongRoot, null, RECEIVED_AT));
        assertIncompatible(() -> highlightlyDetailParser.parse(duplicate, null, RECEIVED_AT));
    }

    @Test
    void detailParserRejectsAnUnknownStatusInsteadOfNormalizingIt() throws IOException {
        String json = new String(fixture("highlightly-detail-prematch.synthetic.json"), StandardCharsets.UTF_8)
                .replace("Not started", "Some new status");

        assertIncompatible(() -> highlightlyDetailParser.parse(json.getBytes(StandardCharsets.UTF_8), null,
                RECEIVED_AT));
    }

    private static void assertIncompatible(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(EnrichmentParseException.class)
                .hasMessage("Enrichment payload is incompatible");
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream input = StrictEnrichmentParsersTest.class.getResourceAsStream("/fixtures/enr001/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing synthetic enrichment fixture");
            }
            return input.readAllBytes();
        }
    }
}
