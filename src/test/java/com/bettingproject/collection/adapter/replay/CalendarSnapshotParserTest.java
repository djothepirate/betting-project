package com.bettingproject.collection.adapter.replay;

import java.io.IOException;
import java.io.InputStream;

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
        });
    }

    @Test
    void unsupportedSchemaFailsExplicitly() throws IOException {
        byte[] payload = fixture("/fixtures/cat001/calendar-unsupported.json");

        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(UnsupportedCalendarSchemaException.class)
                .hasMessageContaining("cal01-fixture-v99");
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
