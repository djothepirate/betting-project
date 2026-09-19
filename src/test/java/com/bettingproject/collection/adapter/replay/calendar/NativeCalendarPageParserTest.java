package com.bettingproject.collection.adapter.replay.calendar;

import com.bettingproject.collection.application.CalendarSnapshotSchemas;
import com.bettingproject.collection.application.ReplayService;
import com.bettingproject.collection.application.calendar.CalendarPageParseException;
import com.bettingproject.collection.application.calendar.CalendarPageParser;
import com.bettingproject.collection.application.calendar.CalendarPageRequest;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeCalendarPageParserTest {
    private static final Instant OBSERVED = Instant.parse("2030-08-10T12:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2030-08-10");
    private final CalendarPageParser highlightly = new HighlightlyCalendarPageParser();
    private final CalendarPageParser footballData = new FootballDataCalendarPageParser();

    @Test
    void highlightlyPagesRetainTheirExactSourceContextAndTheirOwnOrder() throws IOException {
        var first = highlightly.parse(hlRequest(0), bytes(hl()), OBSERVED);
        var second = highlightly.parse(hlRequest(1), fixture("highlightly-page-1.synthetic.json"), OBSERVED);
        assertThat(first.nextOffset()).isEqualTo(1);
        assertThat(second.nextOffset()).isNull();
        assertThat(first.totalCount()).isEqualTo(2);
        assertThat(second.totalCount()).isEqualTo(2);
        assertThat(first.snapshot().schemaVersion()).isEqualTo(CalendarSnapshotSchemas.CANONICAL_V3);
        assertThat(first.snapshot().provider()).isEqualTo("highlightly");
        assertThat(first.snapshot().observedAt()).isEqualTo(OBSERVED);
        assertThat(first.snapshot().fixtures()).singleElement().satisfies(fixture -> {
            assertThat(fixture.providerFixtureId()).isEqualTo("900001");
            assertThat(fixture.homeTeam().providerTeamId()).isEqualTo("910001");
            assertThat(fixture.awayTeam().providerTeamId()).isEqualTo("910002");
            assertThat(fixture.competition().providerCompetitionId()).isEqualTo("920001");
            assertThat(fixture.competition().season()).isEqualTo("2030");
            assertThat(fixture.competition().phase()).isEqualTo("Regular Season - 1");
            assertThat(fixture.competition().countryCode()).isEqualTo("ZZ");
            assertThat(fixture.competition().type()).isNull();
            assertThat(fixture.homeTeam().countryCode()).isNull();
            assertThat(fixture.awayTeam().countryCode()).isNull();
            assertThat(fixture.neutralVenue()).isNull();
            assertThat(fixture.participantsUnordered()).isFalse();
        });
        assertThat(second.snapshot().fixtures().getFirst().competition().countryCode()).isNull();
    }

    @Test
    void footballDataKeepsSeasonIdSeparateFromFilterYearAndDoesNotUseLastUpdated() throws IOException {
        var result = footballData.parse(fdRequest(), bytes(fd()), OBSERVED);
        assertThat(result.nextOffset()).isNull();
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.snapshot().observedAt()).isEqualTo(OBSERVED);
        assertThat(result.snapshot().fixtures()).singleElement().satisfies(fixture -> {
            assertThat(fixture.competition().providerCompetitionId()).isEqualTo("SYN");
            assertThat(fixture.competition().season()).isEqualTo("950001");
            assertThat(fixture.competition().phase()).isEqualTo("REGULAR_SEASON");
            assertThat(fixture.competition().type()).isNull();
            assertThat(fixture.homeTeam().countryCode()).isNull();
            assertThat(fixture.awayTeam().countryCode()).isNull();
            assertThat(fixture.status()).isEqualTo("SCHEDULED");
            assertThat(fixture.neutralVenue()).isNull();
            assertThat(fixture.participantsUnordered()).isFalse();
        });
    }

    @ParameterizedTest
    @CsvSource({"Not started,SCHEDULED", "Finished,FINISHED", "Finished after penalties,FINISHED",
            "Finished after extra time,FINISHED", "Postponed,POSTPONED", "Cancelled,CANCELLED"})
    void highlightlySupportedStatusesAreExplicit(String source, String expected) throws IOException {
        var result = highlightly.parse(hlRequest(0), bytes(hl().replace("Not started", source)), OBSERVED);
        assertThat(result.snapshot().fixtures().getFirst().status()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"First half", "Second half", "Half time", "Extra time", "Break time", "Penalties",
            "Suspended", "Awarded", "Interrupted", "Abandoned", "In progress", "Unknown", "To be announced",
            "finished", "NEW_STATUS"})
    void highlightlyUnsupportedStatusesNeverBecomeNominal(String status) throws IOException {
        rejects(highlightly, hlRequest(0), hl().replace("Not started", status), "UNSUPPORTED_STATUS");
    }

    @ParameterizedTest
    @CsvSource({"SCHEDULED,SCHEDULED", "TIMED,SCHEDULED", "FINISHED,FINISHED", "POSTPONED,POSTPONED",
            "CANCELLED,CANCELLED"})
    void footballDataSupportedStatusesAreExplicit(String source, String expected) throws IOException {
        var result = footballData.parse(fdRequest(), bytes(fd().replace("TIMED", source)), OBSERVED);
        assertThat(result.snapshot().fixtures().getFirst().status()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PLAY", "PAUSED", "LIVE", "SUSPENDED", "AWARDED", "NEW_STATUS", "finished"})
    void footballDataLiveAndUnknownStatusesAreNotConverted(String status) throws IOException {
        rejects(footballData, fdRequest(), fd().replace("TIMED", status), "UNSUPPORTED_STATUS");
    }

    @Test
    void validEmptyResponsesAreNotUnavailableResponses() {
        var hl = highlightly.parse(hlRequest(0), bytes("""
                {"data":[],"pagination":{"totalCount":0,"offset":0,"limit":1}}
                """), OBSERVED);
        var fd = footballData.parse(fdRequest(), bytes("""
                {"matches":[],"resultSet":{"count":0},"competition":{"code":"SYN","id":940001}}
                """), OBSERVED);
        assertThat(hl.snapshot().fixtures()).isEmpty();
        assertThat(fd.snapshot().fixtures()).isEmpty();
        assertThat(hl.nextOffset()).isNull();
        assertThat(fd.nextOffset()).isNull();
        rejects(highlightly, hlRequest(0), "{\"data\":null}", "INCOMPATIBLE");
        rejects(footballData, fdRequest(), "{\"matches\":null}", "INCOMPATIBLE");
    }

    @ParameterizedTest
    @CsvSource({"'\"totalCount\": 2','\"totalCount\": 0'",
            "'\"offset\": 0','\"offset\": 1'", "'\"limit\": 1','\"limit\": 2'"})
    void inconsistentHighlightlyPaginationIsIncomplete(String before, String after) throws IOException {
        rejects(highlightly, hlRequest(0), hl().replace(before, after), "INCOMPLETE_PAGINATION");
    }

    @Test
    void shortAndEmptyIntermediateHighlightlyPagesAreIncomplete() throws IOException {
        var request = new CalendarPageRequest(hlRequest(0).capability(), DATE, 2030, 0, 2);
        rejects(highlightly, request, hl().replace("\"limit\": 1", "\"limit\": 2"), "INCOMPLETE_PAGINATION");
        rejects(highlightly, hlRequest(0), """
                {"data":[],"pagination":{"totalCount":2,"offset":0,"limit":1}}
                """, "INCOMPLETE_PAGINATION");
    }

    @Test
    void footballDataCountMismatchOrInventedOffsetIsIncomplete() throws IOException {
        rejects(footballData, fdRequest(), fd().replace("\"count\": 1", "\"count\": 2"), "INCOMPLETE_PAGINATION");
        var request = new CalendarPageRequest(fdRequest().capability(), DATE, 2030, 1, 100);
        rejects(footballData, request, fd(), "INCOMPLETE_PAGINATION");
    }

    @ParameterizedTest
    @CsvSource({"'\"id\": 920001','\"id\": 920002'", "'\"season\": 2030','\"season\": 2031'",
            "Regular Season - 1,Regular Season - 2", "2030-08-10T19:00:00Z,2030-08-11T00:00:00Z"})
    void highlightlyContextAndUtcDayMustMatchExactly(String before, String after) throws IOException {
        rejects(highlightly, hlRequest(0), hl().replace(before, after), "INCOMPATIBLE");
    }

    @ParameterizedTest
    @CsvSource({"'\"code\": \"SYN\"','\"code\": \"syn\"'", "'\"id\": 950001','\"id\": 2030'",
            "REGULAR_SEASON,FINAL", "2030-08-01,2029-08-01", "2030-08-10T19:00:00Z,2030-08-11T00:00:00Z"})
    void footballDataContextAndExclusiveEndDateAreChecked(String before, String after) throws IOException {
        rejects(footballData, fdRequest(), fd().replace(before, after), "INCOMPATIBLE");
    }

    @ParameterizedTest
    @CsvSource({"'\"id\": 900001','\"id\": \"900001\"'", "'\"id\": 900001','\"id\": 900001.0'",
            "'\"id\": 900001','\"id\": null'", "'\"season\": 2030','\"season\": \"2030\"'",
            "'\"totalCount\": 2','\"totalCount\": \"2\"'", "'\"totalCount\": 2','\"totalCount\": -1'",
            "'\"round\": \"Regular Season - 1\"','\"round\": null'"})
    void nativeFieldsDoNotCoerceTypes(String before, String after) throws IOException {
        rejects(highlightly, hlRequest(0), hl().replace(before, after), "INCOMPATIBLE");
    }

    @Test
    void noFixtureIsReturnedIfAnotherFixtureIsInvalid() throws IOException {
        String entry = hl().substring(hl().indexOf("    {"), hl().indexOf("\n  ],"));
        String validAndInvalid = "{\"data\":[" + entry + "," + entry.replace("900001", "900002")
                .replace("Not started", "First half") + "],\"pagination\":{\"totalCount\":2,\"offset\":0,\"limit\":2}}";
        var request = new CalendarPageRequest(hlRequest(0).capability(), DATE, 2030, 0, 2);
        rejects(highlightly, request, validAndInvalid, "UNSUPPORTED_STATUS");
        String duplicated = validAndInvalid.replace("900002", "900001").replace("First half", "Not started");
        rejects(highlightly, request, duplicated, "INCOMPATIBLE");
    }

    @Test
    void sourceParticipantsAreDistinctAndNeverReordered() throws IOException {
        rejects(highlightly, hlRequest(0), hl().replace("910002", "910001"), "INCOMPATIBLE");
        rejects(footballData, fdRequest(), fd().replace("960002", "960001"), "INCOMPATIBLE");
        var result = highlightly.parse(hlRequest(0), bytes(hl().replace("910001", "999999")), OBSERVED);
        assertThat(result.snapshot().fixtures().getFirst().homeTeam().providerTeamId()).isEqualTo("999999");
        assertThat(result.snapshot().fixtures().getFirst().awayTeam().providerTeamId()).isEqualTo("910002");
    }

    @Test
    void duplicateFieldsAndTrailingTokensAreRejectedWithoutLeakingData() throws IOException {
        rejects(highlightly, hlRequest(0), hl().replace("\"round\":", "\"round\":\"sensitive-marker\",\"round\":"),
                "INCOMPATIBLE");
        rejects(highlightly, hlRequest(0), hl() + "{}", "INCOMPATIBLE");
        rejects(footballData, fdRequest(), fd().replace("\"status\":", "\"status\":\"sensitive-marker\",\"status\":"),
                "INCOMPATIBLE");
        rejects(footballData, fdRequest(), fd() + "[]", "INCOMPATIBLE");
        rejects(footballData, fdRequest(), "<html>sensitive-marker</html>", "INCOMPATIBLE");
    }

    @Test
    void absentDescriptionsRemainUnknownWithoutInventingCountriesOrType() throws IOException {
        String withoutCountry = hl().replace("\"country\": {\"code\": \"ZZ\", \"name\": \"Synthetic Country\"},", "");
        var result = highlightly.parse(hlRequest(0), bytes(withoutCountry), OBSERVED);
        assertThat(result.snapshot().fixtures().getFirst().competition().countryCode()).isNull();
        String withoutArea = fd().replace("\"area\": {\"id\": 970001, \"code\": \"ZZ\", \"name\": \"Synthetic Country\"},", "");
        var other = footballData.parse(fdRequest(), bytes(withoutArea), OBSERVED);
        assertThat(other.snapshot().fixtures().getFirst().competition().countryCode()).isNull();
    }

    @Test
    void equivalentOffsetTimestampKeepsItsUtcDate() throws IOException {
        var result = highlightly.parse(hlRequest(0), bytes(hl().replace("2030-08-10T19:00:00Z", "2030-08-10T21:00:00+02:00")), OBSERVED);
        assertThat(result.snapshot().fixtures().getFirst().kickoff()).isEqualTo(Instant.parse("2030-08-10T19:00:00Z"));
        rejects(highlightly, hlRequest(0), hl().replace("2030-08-10T19:00:00Z", "2030-08-10T19:00:00"), "INCOMPATIBLE");
    }

    @Test
    void replayIsDeterministicAndPreservesTheOriginalBytesHash() throws IOException {
        byte[] body = bytes(hl());
        var replay = new ReplayService();
        var first = replay.replay(body, value -> highlightly.parse(hlRequest(0), value, OBSERVED));
        var second = replay.replay(body, value -> highlightly.parse(hlRequest(0), value, OBSERVED));
        assertThat(first.snapshotSha256()).hasSize(64).isEqualTo(second.snapshotSha256());
        assertThat(first.value()).isEqualTo(second.value());
        assertThat(highlightly.version()).isEqualTo("highlightly-calendar-v1");
        assertThat(footballData.version()).isEqualTo("football-data-calendar-v1");
    }

    @Test
    void observedAtIsMandatoryAndNeverInferredFromSourceTimestamps() throws IOException {
        assertThatThrownBy(() -> footballData.parse(fdRequest(), bytes(fd()), null))
                .isInstanceOf(CalendarPageParseException.class).hasMessageContaining("INCOMPATIBLE");
    }

    @Test
    void wrongProviderAndOversizedPayloadAreIncompatible() throws IOException {
        rejects(footballData, hlRequest(0), fd(), "INCOMPATIBLE");
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> highlightly.parse(hlRequest(0), oversized, OBSERVED))
                .isInstanceOf(CalendarPageParseException.class);
    }

    @Test
    void literalSourcePhaseCharactersRemainLiteralWithoutPatternMatching() throws IOException {
        String phase = "Synthetic *?% phase";
        var request = new CalendarPageRequest(new ProviderCapabilityKey("highlightly", "920001", "2030",
                phase, CapabilityDataType.CALENDAR), DATE, 2030, 0, 1);
        var result = highlightly.parse(request, bytes(hl().replace("Regular Season - 1", phase)), OBSERVED);
        assertThat(result.snapshot().fixtures().getFirst().competition().phase()).isEqualTo(phase);
        rejects(highlightly, request, hl(), "INCOMPATIBLE");
    }

    @Test
    void laterPagesCannotSmuggleDuplicateOrMismatchedFixtureContextWithinOnePage() throws IOException {
        String entry = fd().substring(fd().indexOf("    {"), fd().indexOf("\n  ]"));
        String prefix = fd().substring(0, fd().indexOf("    {"));
        String duplicate = prefix.replace("\"count\": 1", "\"count\": 2") + entry + "," + entry + "]}";
        rejects(footballData, fdRequest(), duplicate, "INCOMPATIBLE");
        String conflicting = prefix.replace("\"count\": 1", "\"count\": 2") + entry + ","
                + entry.replace("930001", "930002").replace("REGULAR_SEASON", "FINAL") + "]}";
        rejects(footballData, fdRequest(), conflicting, "INCOMPATIBLE");
    }

    private static CalendarPageRequest hlRequest(int offset) {
        return new CalendarPageRequest(new ProviderCapabilityKey("highlightly", "920001", "2030",
                "Regular Season - 1", CapabilityDataType.CALENDAR), DATE, 2030, offset, 1);
    }

    private static CalendarPageRequest fdRequest() {
        return new CalendarPageRequest(new ProviderCapabilityKey("football-data.org", "SYN", "950001",
                "REGULAR_SEASON", CapabilityDataType.CALENDAR), DATE, 2030, 0, 100);
    }

    private static String hl() throws IOException {
        return new String(fixture("highlightly-page-0.synthetic.json"), StandardCharsets.UTF_8);
    }

    private static String fd() throws IOException {
        return new String(fixture("football-data-calendar.synthetic.json"), StandardCharsets.UTF_8);
    }

    private static byte[] fixture(String name) throws IOException {
        try (var stream = NativeCalendarPageParserTest.class.getResourceAsStream("/fixtures/mvp001/calendar/" + name)) {
            if (stream == null) {
                throw new IOException("synthetic calendar fixture missing");
            }
            return stream.readAllBytes();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void rejects(CalendarPageParser parser, CalendarPageRequest request, String body, String code) {
        assertThatThrownBy(() -> parser.parse(request, bytes(body), OBSERVED))
                .isInstanceOfSatisfying(CalendarPageParseException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(code);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain("sensitive-marker");
                });
    }
}
