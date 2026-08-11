package com.bettingproject.collection.adapter.replay;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.bettingproject.collection.application.CalendarSnapshot;
import com.bettingproject.collection.application.CalendarSnapshotSchemas;
import com.bettingproject.collection.application.DiscoveredCompetition;
import com.bettingproject.collection.application.DiscoveredFixture;
import com.bettingproject.collection.application.DiscoveredTeam;
import com.bettingproject.collection.application.SnapshotParser;
import com.bettingproject.collection.application.UnsupportedCalendarSchemaException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CalendarSnapshotParser implements SnapshotParser<CalendarSnapshot> {

    public static final String LEGACY_SCHEMA = CalendarSnapshotSchemas.LEGACY_V1;
    public static final String CANONICAL_SCHEMA = CalendarSnapshotSchemas.CANONICAL_V2;
    private static final Set<String> SUPPORTED_SCHEMAS = Set.of(LEGACY_SCHEMA, CANONICAL_SCHEMA);

    private final JsonMapper jsonMapper;

    public CalendarSnapshotParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public CalendarSnapshot parse(byte[] payload) throws Exception {
        SchemaHeader header = jsonMapper.readValue(payload, SchemaHeader.class);
        if (!SUPPORTED_SCHEMAS.contains(header.schemaVersion())) {
            throw new UnsupportedCalendarSchemaException(header.schemaVersion());
        }
        if (LEGACY_SCHEMA.equals(header.schemaVersion())) {
            return parseLegacy(payload);
        }
        return parseCanonical(payload);
    }

    private CalendarSnapshot parseLegacy(byte[] payload) throws Exception {
        LegacyPayload fixturePayload = jsonMapper.readValue(payload, LegacyPayload.class);
        requirePayloadHeader(fixturePayload.provider(), fixturePayload.fixtures());
        List<DiscoveredFixture> fixtures = fixturePayload.fixtures().stream()
                .map(item -> new DiscoveredFixture(
                        item.providerFixtureId(),
                        Instant.parse(item.kickoff()),
                        item.homeTeam(),
                        item.awayTeam()))
                .toList();
        return new CalendarSnapshot(fixturePayload.schemaVersion(), fixturePayload.provider(), fixtures);
    }

    private CalendarSnapshot parseCanonical(byte[] payload) throws Exception {
        CanonicalPayload fixturePayload = jsonMapper.readValue(payload, CanonicalPayload.class);
        requirePayloadHeader(fixturePayload.provider(), fixturePayload.fixtures());
        if (fixturePayload.observedAt() == null || fixturePayload.observedAt().isBlank()) {
            throw new IllegalArgumentException("Fixture observedAt is required for schema v2");
        }
        List<DiscoveredFixture> fixtures = fixturePayload.fixtures().stream()
                .map(this::canonicalFixture)
                .toList();
        return new CalendarSnapshot(
                fixturePayload.schemaVersion(),
                fixturePayload.provider(),
                Instant.parse(fixturePayload.observedAt()),
                fixtures);
    }

    private DiscoveredFixture canonicalFixture(CanonicalFixtureItem item) {
        if (item.competition() == null || item.homeTeam() == null || item.awayTeam() == null) {
            throw new IllegalArgumentException("Competition and teams are required for schema v2");
        }
        return new DiscoveredFixture(
                item.providerFixtureId(),
                new DiscoveredCompetition(
                        item.competition().providerCompetitionId(),
                        item.competition().name(),
                        item.competition().countryCode(),
                        item.competition().type(),
                        item.competition().season(),
                        item.competition().phase()),
                Instant.parse(item.kickoff()),
                item.status(),
                new DiscoveredTeam(
                        item.homeTeam().providerTeamId(),
                        item.homeTeam().name(),
                        item.homeTeam().countryCode()),
                new DiscoveredTeam(
                        item.awayTeam().providerTeamId(),
                        item.awayTeam().name(),
                        item.awayTeam().countryCode()));
    }

    private void requirePayloadHeader(String provider, List<?> fixtures) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Fixture provider is required");
        }
        if (fixtures == null) {
            throw new IllegalArgumentException("Fixture list is required");
        }
    }

    private record SchemaHeader(String schemaVersion) {
    }

    private record LegacyPayload(String schemaVersion, String provider, List<LegacyFixtureItem> fixtures) {
    }

    private record LegacyFixtureItem(String providerFixtureId, String kickoff, String homeTeam, String awayTeam) {
    }

    private record CanonicalPayload(
            String schemaVersion,
            String provider,
            String observedAt,
            List<CanonicalFixtureItem> fixtures) {
    }

    private record CanonicalFixtureItem(
            String providerFixtureId,
            CanonicalCompetitionItem competition,
            String kickoff,
            String status,
            CanonicalTeamItem homeTeam,
            CanonicalTeamItem awayTeam) {
    }

    private record CanonicalCompetitionItem(
            String providerCompetitionId,
            String name,
            String countryCode,
            String type,
            String season,
            String phase) {
    }

    private record CanonicalTeamItem(String providerTeamId, String name, String countryCode) {
    }
}
