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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CalendarSnapshotParser implements SnapshotParser<CalendarSnapshot> {

    public static final String LEGACY_SCHEMA = CalendarSnapshotSchemas.LEGACY_V1;
    public static final String CANONICAL_SCHEMA = CalendarSnapshotSchemas.CANONICAL_V2;
    public static final String CANONICAL_V3_SCHEMA = CalendarSnapshotSchemas.CANONICAL_V3;
    private static final Set<String> SUPPORTED_SCHEMAS = Set.of(
            LEGACY_SCHEMA,
            CANONICAL_SCHEMA,
            CANONICAL_V3_SCHEMA);

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
        if (CANONICAL_SCHEMA.equals(header.schemaVersion())) {
            return parseCanonicalV2(payload);
        }
        return parseCanonicalV3(payload);
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

    private CalendarSnapshot parseCanonicalV2(byte[] payload) throws Exception {
        CanonicalPayload fixturePayload = jsonMapper.readValue(payload, CanonicalPayload.class);
        requirePayloadHeader(fixturePayload.provider(), fixturePayload.fixtures());
        List<DiscoveredFixture> fixtures = fixturePayload.fixtures().stream()
                .map(item -> canonicalFixture(
                        fixturePayload.schemaVersion(),
                        item.providerFixtureId(),
                        item.competition(),
                        item.kickoff(),
                        item.status(),
                        null,
                        false,
                        item.homeTeam(),
                        item.awayTeam()))
                .toList();
        return new CalendarSnapshot(
                fixturePayload.schemaVersion(),
                fixturePayload.provider(),
                requireObservedAt(fixturePayload.observedAt(), fixturePayload.schemaVersion()),
                fixtures);
    }

    private CalendarSnapshot parseCanonicalV3(byte[] payload) throws Exception {
        validateV3FixtureAttributes(payload);
        CanonicalV3Payload fixturePayload = jsonMapper.readValue(payload, CanonicalV3Payload.class);
        requirePayloadHeader(fixturePayload.provider(), fixturePayload.fixtures());
        List<DiscoveredFixture> fixtures = fixturePayload.fixtures().stream()
                .map(item -> canonicalFixture(
                        fixturePayload.schemaVersion(),
                        item.providerFixtureId(),
                        item.competition(),
                        item.kickoff(),
                        item.status(),
                        item.neutralVenue(),
                        item.participantsUnordered(),
                        item.homeTeam(),
                        item.awayTeam()))
                .toList();
        return new CalendarSnapshot(
                fixturePayload.schemaVersion(),
                fixturePayload.provider(),
                requireObservedAt(fixturePayload.observedAt(), fixturePayload.schemaVersion()),
                fixtures);
    }

    private DiscoveredFixture canonicalFixture(
            String schemaVersion,
            String providerFixtureId,
            CanonicalCompetitionItem competition,
            String kickoff,
            String status,
            Boolean neutralVenue,
            boolean participantsUnordered,
            CanonicalTeamItem homeTeam,
            CanonicalTeamItem awayTeam) {
        if (competition == null || homeTeam == null || awayTeam == null) {
            throw new IllegalArgumentException(
                    "Competition and teams are required for schema " + schemaVersion);
        }
        return new DiscoveredFixture(
                providerFixtureId,
                new DiscoveredCompetition(
                        competition.providerCompetitionId(),
                        competition.name(),
                        competition.countryCode(),
                        competition.type(),
                        competition.season(),
                        competition.phase()),
                Instant.parse(kickoff),
                status,
                neutralVenue,
                participantsUnordered,
                new DiscoveredTeam(
                        homeTeam.providerTeamId(),
                        homeTeam.name(),
                        homeTeam.countryCode()),
                new DiscoveredTeam(
                        awayTeam.providerTeamId(),
                        awayTeam.name(),
                        awayTeam.countryCode()));
    }

    private Instant requireObservedAt(String observedAt, String schemaVersion) {
        if (observedAt == null || observedAt.isBlank()) {
            throw new IllegalArgumentException(
                    "Fixture observedAt is required for schema " + schemaVersion);
        }
        return Instant.parse(observedAt);
    }

    private void validateV3FixtureAttributes(byte[] payload) throws Exception {
        JsonNode root = jsonMapper.readTree(payload);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Calendar payload must be a JSON object for schema v3");
        }
        JsonNode fixtures = root.get("fixtures");
        if (fixtures == null || !fixtures.isArray()) {
            throw new IllegalArgumentException("Fixture list is required for schema v3");
        }
        int index = 0;
        for (JsonNode fixture : fixtures) {
            if (!fixture.isObject()) {
                throw new IllegalArgumentException("Fixture at index " + index + " must be a JSON object");
            }
            requireNullableBoolean(fixture, "neutralVenue", index);
            requireBoolean(fixture, "participantsUnordered", index);
            index++;
        }
    }

    private void requireNullableBoolean(JsonNode fixture, String field, int fixtureIndex) {
        if (!fixture.has(field)) {
            throw new IllegalArgumentException(
                    field + " must be present for schema v3 fixture at index " + fixtureIndex);
        }
        JsonNode value = fixture.get(field);
        if (!value.isNull() && !value.isBoolean()) {
            throw new IllegalArgumentException(
                    field + " must be a boolean or null for schema v3 fixture at index " + fixtureIndex);
        }
    }

    private void requireBoolean(JsonNode fixture, String field, int fixtureIndex) {
        if (!fixture.has(field) || !fixture.get(field).isBoolean()) {
            throw new IllegalArgumentException(
                    field + " must be a non-null boolean for schema v3 fixture at index " + fixtureIndex);
        }
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

    private record CanonicalV3Payload(
            String schemaVersion,
            String provider,
            String observedAt,
            List<CanonicalV3FixtureItem> fixtures) {
    }

    private record CanonicalFixtureItem(
            String providerFixtureId,
            CanonicalCompetitionItem competition,
            String kickoff,
            String status,
            CanonicalTeamItem homeTeam,
            CanonicalTeamItem awayTeam) {
    }

    private record CanonicalV3FixtureItem(
            String providerFixtureId,
            CanonicalCompetitionItem competition,
            String kickoff,
            String status,
            Boolean neutralVenue,
            boolean participantsUnordered,
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
