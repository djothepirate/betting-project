package com.bettingproject.collection.adapter.replay;

import java.io.IOException;
import java.io.InputStream;

import com.bettingproject.collection.application.CalendarSnapshot;
import com.bettingproject.collection.application.ReplayResult;
import com.bettingproject.collection.application.ReplayService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarSnapshotParserTest {

    private final ReplayService replayService = new ReplayService();
    private final CalendarSnapshotParser parser = new CalendarSnapshotParser(JsonMapper.builder().build());

    @Test
    void sanitizedCal01FixtureCanBeReplayedWithoutNetwork() throws IOException {
        byte[] payload = fixture("/fixtures/cal01/calendar-sample.json");

        ReplayResult<CalendarSnapshot> result = replayService.replay(payload, parser);

        assertThat(result.snapshotSha256()).hasSize(64);
        assertThat(result.value().schemaVersion()).isEqualTo("cal01-fixture-v1");
        assertThat(result.value().provider()).isEqualTo("offline-fixture");
        assertThat(result.value().fixtures()).hasSize(2);
        assertThat(result.value().fixtures().getFirst().homeTeam()).isEqualTo("FC København");
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
