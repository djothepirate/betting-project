package com.bettingproject.collection.adapter.replay;

import java.time.Instant;
import java.util.List;

import com.bettingproject.collection.application.CalendarSnapshot;
import com.bettingproject.collection.application.DiscoveredFixture;
import com.bettingproject.collection.application.SnapshotParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CalendarSnapshotParser implements SnapshotParser<CalendarSnapshot> {

    static final String SUPPORTED_SCHEMA = "cal01-fixture-v1";

    private final JsonMapper jsonMapper;

    public CalendarSnapshotParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public CalendarSnapshot parse(byte[] payload) throws Exception {
        FixturePayload fixturePayload = jsonMapper.readValue(payload, FixturePayload.class);
        if (!SUPPORTED_SCHEMA.equals(fixturePayload.schemaVersion())) {
            throw new IllegalArgumentException("Unsupported calendar fixture schema: " + fixturePayload.schemaVersion());
        }
        if (fixturePayload.provider() == null || fixturePayload.provider().isBlank()) {
            throw new IllegalArgumentException("Fixture provider is required");
        }
        if (fixturePayload.fixtures() == null) {
            throw new IllegalArgumentException("Fixture list is required");
        }
        List<DiscoveredFixture> fixtures = fixturePayload.fixtures().stream()
                .map(item -> new DiscoveredFixture(
                        item.providerFixtureId(),
                        Instant.parse(item.kickoff()),
                        item.homeTeam(),
                        item.awayTeam()))
                .toList();
        return new CalendarSnapshot(fixturePayload.schemaVersion(), fixturePayload.provider(), fixtures);
    }

    private record FixturePayload(String schemaVersion, String provider, List<FixtureItem> fixtures) {
    }

    private record FixtureItem(String providerFixtureId, String kickoff, String homeTeam, String awayTeam) {
    }
}
