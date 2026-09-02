package com.bettingproject.collection.adapter.replay;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.bettingproject.collection.application.CalendarSnapshot;
import com.bettingproject.collection.application.CalendarSnapshotSchemas;
import com.bettingproject.collection.application.ReplayResult;
import com.bettingproject.collection.application.ReplayService;
import com.bettingproject.collection.application.UnsupportedCalendarSchemaException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarSnapshotParserTest {

    private final ReplayService replayService = new ReplayService();
    private final CalendarSnapshotParser parser = new CalendarSnapshotParser(JsonMapper.builder().build());

    @Test
    void sanitizedCal01FixtureCanBeReplayedWithoutNetwork() throws IOException {
        byte[] payload = fixture("/fixtures/cal01/calendar-sample.json");

        ReplayResult<CalendarSnapshot> result = replayService.replay(payload, parser);

        assertThat(result.snapshotSha256()).hasSize(64);
        assertThat(result.value().schemaVersion()).isEqualTo(CalendarSnapshotSchemas.LEGACY_V1);
        assertThat(result.value().provider()).isEqualTo("offline-fixture");
        assertThat(result.value().fixtures()).hasSize(2);
        assertThat(result.value().fixtures().getFirst().homeTeam().name()).isEqualTo("FC København");
        assertThat(result.value().fixtures().getFirst().competition()).isNull();
        assertThat(result.value().fixtures().getFirst().neutralVenue()).isNull();
        assertThat(result.value().fixtures().getFirst().participantsUnordered()).isFalse();
    }

    @Test
    void canonicalV2FixtureCarriesIdentityAndCompetitionContext() throws Exception {
        byte[] payload = fixture("/fixtures/cat001/calendar-v2-highlightly.json");

        CalendarSnapshot snapshot = parser.parse(payload);

        assertThat(snapshot.schemaVersion()).isEqualTo(CalendarSnapshotSchemas.CANONICAL_V2);
        assertThat(snapshot.observedAt()).hasToString("2026-08-11T10:00:00Z");
        assertThat(snapshot.fixtures()).singleElement().satisfies(fixture -> {
            assertThat(fixture.providerFixtureId()).isEqualTo("hly-upl-001");
            assertThat(fixture.competition().season()).isEqualTo("2026/2027");
            assertThat(fixture.competition().phase()).isEqualTo("REGULAR_SEASON");
            assertThat(fixture.homeTeam().providerTeamId()).isEqualTo("hly-kryvbas");
            assertThat(fixture.homeTeam().name()).isEqualTo("FC Kryvbas Kryvyi Rih");
            assertThat(fixture.status()).isEqualTo("SCHEDULED");
            assertThat(fixture.neutralVenue()).isNull();
            assertThat(fixture.participantsUnordered()).isFalse();
        });
    }

    @Test
    void canonicalV3FixtureCarriesExplicitNeutralAndOrderedParticipants() throws Exception {
        byte[] payload = fixture("/fixtures/cat002/calendar-v3-ordered-neutral.json");

        CalendarSnapshot snapshot = parser.parse(payload);

        assertThat(snapshot.schemaVersion()).isEqualTo(CalendarSnapshotSchemas.CANONICAL_V3);
        assertThat(snapshot.observedAt()).hasToString("2026-09-01T15:00:00Z");
        assertThat(snapshot.fixtures()).singleElement().satisfies(fixture -> {
            assertThat(fixture.providerFixtureId()).isEqualTo("synthetic-v3-ordered-001");
            assertThat(fixture.neutralVenue()).isTrue();
            assertThat(fixture.participantsUnordered()).isFalse();
            assertThat(fixture.homeTeam().name()).isEqualTo("Synthetic FC Alpha");
            assertThat(fixture.awayTeam().name()).isEqualTo("Synthetic FC Beta");
        });
    }

    @Test
    void canonicalV3FixtureDistinguishesUnknownNeutralityFromUnorderedParticipants() throws Exception {
        byte[] payload = fixture("/fixtures/cat002/calendar-v3-unordered-unknown-neutral.json");

        CalendarSnapshot snapshot = parser.parse(payload);

        assertThat(snapshot.schemaVersion()).isEqualTo(CalendarSnapshotSchemas.CANONICAL_V3);
        assertThat(snapshot.fixtures()).singleElement().satisfies(fixture -> {
            assertThat(fixture.neutralVenue()).isNull();
            assertThat(fixture.participantsUnordered()).isTrue();
            assertThat(fixture.homeTeam().name()).isEqualTo("Synthetic FC Gamma");
            assertThat(fixture.awayTeam().name()).isEqualTo("Synthetic FC Delta");
        });
    }

    @Test
    void canonicalV3AcceptsExplicitNonNeutralVenueAndUnorderedParticipants() throws Exception {
        byte[] payload = canonicalPayload(
                CalendarSnapshotSchemas.CANONICAL_V3,
                observedAtEntry(),
                "\"neutralVenue\": false,\n"
                        + "      \"participantsUnordered\": true,");

        CalendarSnapshot snapshot = parser.parse(payload);

        assertThat(snapshot.fixtures()).singleElement().satisfies(fixture -> {
            assertThat(fixture.neutralVenue()).isFalse();
            assertThat(fixture.participantsUnordered()).isTrue();
        });
    }

    @Test
    void canonicalV3RequiresNeutralVenueToBePresent() {
        byte[] payload = canonicalPayload(
                CalendarSnapshotSchemas.CANONICAL_V3,
                observedAtEntry(),
                "\"participantsUnordered\": false,");

        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neutralVenue must be present");
    }

    @Test
    void canonicalV3RejectsNonBooleanNeutralVenue() {
        for (String invalidValue : List.of("\"true\"", "1", "{}")) {
            byte[] payload = canonicalPayload(
                    CalendarSnapshotSchemas.CANONICAL_V3,
                    observedAtEntry(),
                    "\"neutralVenue\": " + invalidValue + ",\n"
                            + "      \"participantsUnordered\": false,");

            assertThatThrownBy(() -> parser.parse(payload))
                    .as("neutralVenue=%s", invalidValue)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("neutralVenue must be a boolean or null");
        }
    }

    @Test
    void canonicalV3RequiresParticipantsUnorderedToBePresent() {
        byte[] payload = canonicalPayload(
                CalendarSnapshotSchemas.CANONICAL_V3,
                observedAtEntry(),
                "\"neutralVenue\": null,");

        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("participantsUnordered must be a non-null boolean");
    }

    @Test
    void canonicalV3RejectsNullOrNonBooleanParticipantsUnordered() {
        for (String invalidValue : List.of("null", "\"false\"", "0")) {
            byte[] payload = canonicalPayload(
                    CalendarSnapshotSchemas.CANONICAL_V3,
                    observedAtEntry(),
                    "\"neutralVenue\": null,\n"
                            + "      \"participantsUnordered\": " + invalidValue + ",");

            assertThatThrownBy(() -> parser.parse(payload))
                    .as("participantsUnordered=%s", invalidValue)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("participantsUnordered must be a non-null boolean");
        }
    }

    @Test
    void canonicalSchemasRequireObservedAt() {
        for (String schemaVersion : List.of(
                CalendarSnapshotSchemas.CANONICAL_V2,
                CalendarSnapshotSchemas.CANONICAL_V3)) {
            String attributes = CalendarSnapshotSchemas.CANONICAL_V3.equals(schemaVersion)
                    ? "\"neutralVenue\": null,\n      \"participantsUnordered\": false,"
                    : "";
            byte[] payload = canonicalPayload(schemaVersion, "", attributes);

            assertThatThrownBy(() -> parser.parse(payload))
                    .as("schemaVersion=%s", schemaVersion)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("observedAt is required");
        }
    }

    @Test
    void canonicalSchemasRejectInvalidObservedAt() {
        for (String schemaVersion : List.of(
                CalendarSnapshotSchemas.CANONICAL_V2,
                CalendarSnapshotSchemas.CANONICAL_V3)) {
            String attributes = CalendarSnapshotSchemas.CANONICAL_V3.equals(schemaVersion)
                    ? "\"neutralVenue\": null,\n      \"participantsUnordered\": false,"
                    : "";
            byte[] payload = canonicalPayload(
                    schemaVersion,
                    "  \"observedAt\": \"not-an-instant\",\n",
                    attributes);

            assertThatThrownBy(() -> parser.parse(payload))
                    .as("schemaVersion=%s", schemaVersion)
                    .isInstanceOf(DateTimeParseException.class);
        }
    }

    @Test
    void unsupportedSchemaFailsExplicitly() throws IOException {
        byte[] payload = fixture("/fixtures/cat001/calendar-unsupported.json");

        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(UnsupportedCalendarSchemaException.class)
                .hasMessageContaining("cal01-fixture-v99");
    }

    private byte[] canonicalPayload(String schemaVersion, String observedAt, String attributes) {
        return ("""
                {
                  "schemaVersion": "%s",
                  "provider": "synthetic-provider",
                %s  "fixtures": [
                    {
                      "providerFixtureId": "synthetic-inline-001",
                      "competition": {
                        "providerCompetitionId": "synthetic-competition",
                        "name": "Synthetic League",
                        "countryCode": "FRA",
                        "type": "DOMESTIC_LEAGUE",
                        "season": "2026/2027",
                        "phase": "REGULAR_SEASON"
                      },
                      "kickoff": "2026-09-10T18:00:00Z",
                      "status": "SCHEDULED",
                      %s
                      "homeTeam": {
                        "providerTeamId": "synthetic-alpha",
                        "name": "Synthetic FC Alpha",
                        "countryCode": "FRA"
                      },
                      "awayTeam": {
                        "providerTeamId": "synthetic-beta",
                        "name": "Synthetic FC Beta",
                        "countryCode": "FRA"
                      }
                    }
                  ]
                }
                """.formatted(schemaVersion, observedAt, attributes)).getBytes(StandardCharsets.UTF_8);
    }

    private String observedAtEntry() {
        return "  \"observedAt\": \"2026-09-01T15:00:00Z\",\n";
    }

    private byte[] fixture(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test fixture: " + path);
            }
            return input.readAllBytes();
        }
    }
}
