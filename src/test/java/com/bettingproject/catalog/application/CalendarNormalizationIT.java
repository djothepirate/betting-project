package com.bettingproject.catalog.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.identity.domain.ProviderEntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER"
        })
@ActiveProfiles("batch-worker")
@Transactional
class CalendarNormalizationIT {

    private static final String SEASON = "2026/2027";
    private static final String PHASE = "REGULAR_SEASON";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private CatalogCommandService commandService;

    @Autowired
    private CalendarNormalizationService normalizationService;

    @Test
    void flywayV002CreatesCanonicalCalendarTables() {
        long migrationCount = jdbcClient.sql("""
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '002' AND success
                """)
                .query(Long.class)
                .single();
        long tableCount = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('canonical_season', 'fixture_observation', 'normalization_anomaly')
                """)
                .query(Long.class)
                .single();

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(3);
    }

    @Test
    void replayingTheSameSnapshotIsIdempotent() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        RawSnapshot snapshot = snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z");

        NormalizationResult first = normalizationService.normalize(snapshot);
        NormalizationResult second = normalizationService.normalize(snapshot);

        assertThat(first.snapshotInserted()).isTrue();
        assertThat(first.fixturesCreated()).isEqualTo(1);
        assertThat(second.snapshotInserted()).isFalse();
        assertThat(second.snapshotId()).isEqualTo(first.snapshotId());
        assertThat(second.fixturesUnchanged()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(fixtureMappings()).isEqualTo(1);
    }

    @Test
    void twoProvidersConvergeOnTheSameCanonicalFixture() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        bindFootballData(canonical);

        NormalizationResult first = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z"));
        NormalizationResult second = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-football-data.json",
                "football-data",
                "competitions/upl/matches",
                "2026-08-11T10:05:01Z"));

        assertThat(first.fixturesCreated()).isEqualTo(1);
        assertThat(second.fixturesUnchanged()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(fixtureMappings()).isEqualTo(2);
        assertThat(count("fixture_observation")).isEqualTo(2);
    }

    @Test
    void correctionUpdatesTheMappedFixtureWithoutDuplication() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z"));

        NormalizationResult corrected = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly-corrected.json",
                "highlightly",
                "calendar/upl",
                "2026-08-12T10:00:01Z"));

        OffsetDateTime kickoff = jdbcClient.sql("SELECT kickoff_at FROM canonical_fixture")
                .query(OffsetDateTime.class)
                .single();
        String status = jdbcClient.sql("SELECT status FROM canonical_fixture")
                .query(String.class)
                .single();
        assertThat(corrected.fixturesUpdated()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(kickoff.toInstant()).hasToString("2026-08-16T12:00:00Z");
        assertThat(status).isEqualTo("POSTPONED");
        assertThat(count("fixture_observation")).isEqualTo(2);
    }

    @Test
    void hirnykRemainsAmbiguousAndCannotBecomeKryvbas() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        confirmCompetition("highlightly", "hly-upl", canonical.competitionId());
        confirmTeam("highlightly", "hly-livyi-bereh", canonical.awayTeamId());
        commandService.markAmbiguous(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "", "", 0.45);

        NormalizationResult result = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:10:01Z"));

        UUID mappedCanonicalId = jdbcClient.sql("""
                SELECT canonical_entity_id
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                """)
                .query(UUID.class)
                .optional()
                .orElse(null);
        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(result.anomalies()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(mappedCanonicalId).isNull();
        assertThat(anomalyCount("AMBIGUOUS_MAPPING")).isEqualTo(1);
        assertThat(canonical.homeTeamId()).isNotNull();
    }

    @Test
    void confirmedHistoricalHirnykAliasResolvesExactProviderReferenceAndPreservesRawName() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        confirmCompetition("highlightly", "284167", canonical.competitionId());
        confirmTeam("highlightly", "5522923", canonical.homeTeamId());
        confirmTeam("highlightly", "14605646", canonical.awayTeamId());

        NormalizationResult result = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-hirnyk-historical-alias.json",
                "highlightly",
                "calendar/upl",
                "2026-08-14T21:50:33Z"));

        UUID mappedCanonicalId = jdbcClient.sql("""
                SELECT canonical_entity_id
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = '5522923'
                """)
                .query(UUID.class)
                .single();
        String mappingStatus = jdbcClient.sql("""
                SELECT mapping_status
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = '5522923'
                """)
                .query(String.class)
                .single();
        UUID fixtureHomeTeamId = jdbcClient.sql("SELECT home_team_id FROM canonical_fixture")
                .query(UUID.class)
                .single();
        byte[] rawPayload = jdbcClient.sql("SELECT payload FROM raw_snapshot")
                .query(byte[].class)
                .single();

        assertThat(result.fixturesCreated()).isEqualTo(1);
        assertThat(result.fixturesBlocked()).isZero();
        assertThat(result.anomalies()).isZero();
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(mappedCanonicalId).isEqualTo(canonical.homeTeamId());
        assertThat(mappingStatus).isEqualTo("CONFIRMED");
        assertThat(fixtureHomeTeamId).isEqualTo(canonical.homeTeamId());
        assertThat(new String(rawPayload, StandardCharsets.UTF_8))
                .contains("\"providerTeamId\": \"5522923\"")
                .contains("\"name\": \"Hirnyk\"");
    }

    @Test
    void unsupportedSchemaKeepsRawSnapshotAndPersistsAnomaly() throws IOException {
        RawSnapshot snapshot = snapshot(
                "/fixtures/cat001/calendar-unsupported.json",
                "offline-fixture",
                "calendar/unsupported",
                "2026-08-11T10:15:01Z");

        NormalizationResult result = normalizationService.normalize(snapshot);

        assertThat(result.compatible()).isFalse();
        assertThat(result.anomalies()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(anomalyCount("UNSUPPORTED_SCHEMA")).isEqualTo(1);
    }

    @Test
    void legacySchemaRemainsReplayableButIsNotNormalized() throws IOException {
        RawSnapshot snapshot = snapshot(
                "/fixtures/cal01/calendar-sample.json",
                "offline-fixture",
                "calendar/legacy",
                "2026-08-11T10:20:01Z");

        NormalizationResult result = normalizationService.normalize(snapshot);

        assertThat(result.compatible()).isFalse();
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(anomalyCount("LEGACY_SCHEMA_NOT_NORMALIZABLE")).isEqualTo(1);
    }

    private CanonicalIds registerCanonicalEntities() {
        UUID competitionId = commandService.registerCompetition(
                "Ukrainian Premier League", "UKR", CompetitionType.DOMESTIC_LEAGUE);
        UUID homeTeamId = commandService.registerTeam("FC Kryvbas Kryvyi Rih", "UKR");
        UUID awayTeamId = commandService.registerTeam("FC Livyi Bereh Kyiv", "UKR");
        return new CanonicalIds(competitionId, homeTeamId, awayTeamId);
    }

    private void bindHighlightly(CanonicalIds canonical) {
        confirmCompetition("highlightly", "hly-upl", canonical.competitionId());
        confirmTeam("highlightly", "hly-kryvbas", canonical.homeTeamId());
        confirmTeam("highlightly", "hly-livyi-bereh", canonical.awayTeamId());
    }

    private void bindFootballData(CanonicalIds canonical) {
        confirmCompetition("football-data", "fd-upl", canonical.competitionId());
        confirmTeam("football-data", "fd-kryvbas-77", canonical.homeTeamId());
        confirmTeam("football-data", "fd-livyi-88", canonical.awayTeamId());
    }

    private void confirmCompetition(String provider, String providerId, UUID canonicalId) {
        commandService.confirmMapping(
                provider, ProviderEntityType.COMPETITION, providerId, canonicalId, SEASON, PHASE);
    }

    private void confirmTeam(String provider, String providerId, UUID canonicalId) {
        commandService.confirmMapping(
                provider, ProviderEntityType.TEAM, providerId, canonicalId, "", "");
    }

    private RawSnapshot snapshot(String path, String provider, String endpoint, String receivedAt) throws IOException {
        return RawSnapshot.capture(
                provider,
                endpoint,
                Instant.parse(receivedAt),
                fixture(path),
                "cal01-replay-2");
    }

    private byte[] fixture(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test fixture: " + path);
            }
            return input.readAllBytes();
        }
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private long fixtureMappings() {
        return jdbcClient.sql("SELECT COUNT(*) FROM provider_mapping WHERE entity_type = 'FIXTURE'")
                .query(Long.class)
                .single();
    }

    private long anomalyCount(String code) {
        return jdbcClient.sql("SELECT COUNT(*) FROM normalization_anomaly WHERE anomaly_code = :code")
                .param("code", code)
                .query(Long.class)
                .single();
    }

    private record CanonicalIds(UUID competitionId, UUID homeTeamId, UUID awayTeamId) {
    }
}
