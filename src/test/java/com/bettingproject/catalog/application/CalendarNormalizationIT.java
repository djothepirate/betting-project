package com.bettingproject.catalog.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.bettingproject.catalog.domain.CanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalSeason;
import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.catalog.domain.FixtureStatus;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
@Import(CalendarNormalizationIT.SyntheticCalendarAuthorityConfiguration.class)
class CalendarNormalizationIT {

    private static final String SEASON = "2026/2027";
    private static final String PHASE = "REGULAR_SEASON";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private CatalogCommandService commandService;

    @Autowired
    private CalendarNormalizationService normalizationService;

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private ProviderMappingRepository mappingRepository;

    @Test
    void flywayCreatesCanonicalCalendarAndApplicationJournalTables() {
        long migrationCount = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('002', '003') AND success
                """)
                .query(Long.class)
                .single();
        long tableCount = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'canonical_season', 'fixture_observation',
                    'normalization_anomaly', 'fixture_application_log'
                  )
                """)
                .query(Long.class)
                .single();

        assertThat(migrationCount).isEqualTo(2);
        assertThat(tableCount).isEqualTo(4);
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
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(applicationOutcomeCount("CREATED")).isEqualTo(1);
        assertThat(applicationOutcomeCount("UNCHANGED")).isEqualTo(1);
        assertThat(singleString("SELECT source_schema_version FROM fixture_observation"))
                .isEqualTo("cal01-fixture-v2");
        assertThat(singleString("SELECT source_season FROM fixture_observation"))
                .isEqualTo(SEASON);
        assertThat(singleBoolean("SELECT source_neutral_venue IS NULL FROM fixture_observation")).isTrue();
        assertThat(singleBoolean("SELECT source_participants_unordered FROM fixture_observation")).isFalse();
        assertThat(singleBoolean("SELECT neutral_venue IS NULL FROM canonical_fixture")).isTrue();
        assertThat(singleBoolean("SELECT participants_unordered FROM canonical_fixture")).isFalse();
        assertThat(fixtureMappings()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void journalFailureRollsBackTheWholeNormalizationTransaction() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        jdbcClient.sql("""
                ALTER TABLE fixture_application_log
                ADD CONSTRAINT cat002_test_force_journal_failure CHECK (FALSE)
                """).update();

        try {
            RawSnapshot snapshot = snapshot(
                    "/fixtures/cat001/calendar-v2-highlightly.json",
                    "highlightly",
                    "calendar/upl",
                    "2026-08-11T10:00:01Z");

            assertThatThrownBy(() -> normalizationService.normalize(snapshot))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(count("raw_snapshot")).isZero();
            assertThat(count("canonical_season")).isZero();
            assertThat(count("canonical_fixture")).isZero();
            assertThat(count("fixture_observation")).isZero();
            assertThat(count("fixture_application_log")).isZero();
            assertThat(count("normalization_anomaly")).isZero();
            assertThat(fixtureMappings()).isZero();
        } finally {
            jdbcClient.sql("""
                    ALTER TABLE fixture_application_log
                    DROP CONSTRAINT IF EXISTS cat002_test_force_journal_failure
                    """).update();
            deleteCommittedRollbackTestData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void anomalyEventFailureRollsBackTheWholeNormalizationTransaction() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        confirmCompetition("highlightly", "hly-upl", canonical.competitionId());
        confirmTeam("highlightly", "hly-livyi-bereh", canonical.awayTeamId());
        jdbcClient.sql("""
                ALTER TABLE normalization_anomaly_event
                ADD CONSTRAINT cat002_test_force_anomaly_event_failure CHECK (FALSE)
                """).update();

        try {
            RawSnapshot snapshot = snapshot(
                    "/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json",
                    "highlightly",
                    "calendar/upl",
                    "2026-08-11T10:10:01Z");

            assertThatThrownBy(() -> normalizationService.normalize(snapshot))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(count("raw_snapshot")).isZero();
            assertThat(count("canonical_season")).isZero();
            assertThat(count("canonical_fixture")).isZero();
            assertThat(count("fixture_observation")).isZero();
            assertThat(count("fixture_application_log")).isZero();
            assertThat(count("normalization_anomaly")).isZero();
            assertThat(count("normalization_anomaly_event")).isZero();
        } finally {
            jdbcClient.sql("""
                    ALTER TABLE normalization_anomaly_event
                    DROP CONSTRAINT IF EXISTS cat002_test_force_anomaly_event_failure
                    """).update();
            deleteCommittedRollbackTestData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void compareAndSetFailureRollsBackTheWholeNormalizationTransaction() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);

        try {
            jdbcClient.sql("""
                    CREATE OR REPLACE FUNCTION cat002_test_force_fixture_cas_miss()
                    RETURNS trigger
                    LANGUAGE plpgsql
                    AS $$
                    BEGIN
                        RETURN NULL;
                    END;
                    $$
                    """).update();
            jdbcClient.sql("""
                    CREATE TRIGGER cat002_test_force_fixture_cas_miss
                    BEFORE UPDATE ON canonical_fixture
                    FOR EACH ROW
                    EXECUTE FUNCTION cat002_test_force_fixture_cas_miss()
                    """).update();

            RawSnapshot snapshot = snapshot(
                    "/fixtures/cat001/calendar-v2-highlightly.json",
                    "highlightly",
                    "calendar/upl",
                    "2026-08-11T10:00:01Z");

            assertThatThrownBy(() -> normalizationService.normalize(snapshot))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Canonical fixture authority changed concurrently: ");

            assertThat(count("raw_snapshot")).isZero();
            assertThat(count("canonical_season")).isZero();
            assertThat(count("canonical_fixture")).isZero();
            assertThat(count("fixture_observation")).isZero();
            assertThat(count("fixture_application_log")).isZero();
            assertThat(count("normalization_anomaly")).isZero();
            assertThat(fixtureMappings()).isZero();
        }
        finally {
            jdbcClient.sql("""
                    DROP TRIGGER IF EXISTS cat002_test_force_fixture_cas_miss
                    ON canonical_fixture
                    """).update();
            jdbcClient.sql("""
                    DROP FUNCTION IF EXISTS cat002_test_force_fixture_cas_miss()
                    """).update();
            deleteCommittedRollbackTestData();
        }
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
        assertThat(singleString("SELECT last_authority_provider FROM canonical_fixture"))
                .isEqualTo("highlightly");
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-11T10:00:00Z");
        assertThat(singleString("""
                SELECT authority_role
                FROM fixture_application_log
                WHERE outcome = 'UNCHANGED'
                """)).isEqualTo("CONTROL");
    }

    @Test
    void contradictoryControlObservationNeverReplacesThePrimary() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        bindFootballData(canonical);
        normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z"));
        byte[] contradictoryControlPayload = replaceInFixture(
                "/fixtures/cat001/calendar-v2-football-data.json",
                "\"status\": \"SCHEDULED\"",
                "\"status\": \"POSTPONED\"");

        NormalizationResult control = normalizationService.normalize(snapshot(
                contradictoryControlPayload,
                "football-data",
                "competitions/upl/matches",
                "2026-08-11T10:05:01Z"));

        assertThat(control.fixturesBlocked()).isEqualTo(1);
        assertThat(control.anomalies()).isEqualTo(1);
        assertThat(singleString("SELECT status FROM canonical_fixture")).isEqualTo("SCHEDULED");
        assertThat(singleString("SELECT last_authority_provider FROM canonical_fixture"))
                .isEqualTo("highlightly");
        assertThat(applicationOutcomeCount("CONTROL_DIVERGENCE")).isEqualTo(1);
        assertThat(anomalyCount("CONTROL_DIVERGENCE")).isEqualTo(1);
        assertThat(singleString("""
                SELECT authority_role
                FROM fixture_application_log
                WHERE outcome = 'CONTROL_DIVERGENCE'
                """)).isEqualTo("CONTROL");
    }

    @Test
    void unassignedObservationIsPreservedWithoutCanonicalMutation() throws IOException {
        byte[] unassignedPayload = replaceInFixture(
                "/fixtures/cat002/calendar-v3-ordered-neutral.json",
                "\"provider\": \"synthetic-provider\"",
                "\"provider\": \"unassigned-provider\"");

        NormalizationResult unassigned = normalizationService.normalize(snapshot(
                unassignedPayload,
                "unassigned-provider",
                "calendar/synthetic-league",
                "2026-09-01T15:00:01Z"));

        assertThat(unassigned.fixturesBlocked()).isEqualTo(1);
        assertThat(unassigned.anomalies()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(fixtureMappings()).isZero();
        assertThat(applicationOutcomeCount("UNASSIGNED")).isEqualTo(1);
        assertThat(anomalyCount("UNASSIGNED_AUTHORITY")).isEqualTo(1);
    }

    @ParameterizedTest(name = "literal {1} in runtime {0} remains an exact value")
    @MethodSource("wildcardLikeRuntimeAuthorityValues")
    void wildcardLikeRuntimeAuthorityValuesArePreservedAsUnassigned(
            String field,
            String character,
            String expectedJson,
            String replacementJson,
            String snapshotProvider,
            String expectedStoredValue) throws IOException {
        byte[] payload = replaceInFixture(
                "/fixtures/cat002/calendar-v3-ordered-neutral.json",
                expectedJson,
                replacementJson);

        NormalizationResult result = normalizationService.normalize(snapshot(
                payload,
                snapshotProvider,
                "calendar/runtime-literal-authority-key",
                "2026-09-01T15:00:01Z"));

        assertThat(result.snapshotInserted()).isTrue();
        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(result.anomalies()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(1);
        assertThat(count("normalization_anomaly")).isEqualTo(1);
        assertThat(applicationOutcomeCount("UNASSIGNED")).isEqualTo(1);
        assertThat(anomalyCount("UNASSIGNED_AUTHORITY")).isEqualTo(1);
        assertThat(storedAuthorityValue(field)).isEqualTo(expectedStoredValue);
        assertThat(expectedStoredValue).contains(character);
        assertThat(count("canonical_competition")).isZero();
        assertThat(count("canonical_season")).isZero();
        assertThat(count("canonical_team")).isZero();
        assertThat(count("canonical_fixture")).isZero();
        assertThat(count("provider_mapping")).isZero();
    }

    @Test
    void controlWithoutPrimaryIsPreservedAndCannotCreateCanonicalState() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindFootballData(canonical);

        NormalizationResult result = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-football-data.json",
                "football-data",
                "competitions/upl/matches",
                "2026-08-11T10:05:01Z"));

        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(result.anomalies()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(1);
        assertThat(count("canonical_season")).isZero();
        assertThat(count("canonical_fixture")).isZero();
        assertThat(fixtureMappings()).isZero();
        assertThat(applicationOutcomeCount("BLOCKED")).isEqualTo(1);
        assertThat(anomalyCount("CONTROL_WITHOUT_PRIMARY")).isEqualTo(1);
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
        assertThat(applicationOutcomeCount("CREATED")).isEqualTo(1);
        assertThat(applicationOutcomeCount("UPDATED")).isEqualTo(1);
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-12T10:00:00Z");
        assertThat(singleString("SELECT last_authority_provider FROM canonical_fixture"))
                .isEqualTo("highlightly");
        assertThat(singleString("SELECT last_authority_policy_version FROM canonical_fixture"))
                .isEqualTo("cat-002-lot4-test-policy-v1");
    }

    @Test
    void newerPrimaryThenOlderObservationKeepsTheNewerCanonicalState() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);

        NormalizationResult recent = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly-corrected.json",
                "highlightly",
                "calendar/upl",
                "2026-08-12T10:00:01Z"));
        NormalizationResult stale = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-13T10:00:01Z"));

        assertThat(recent.fixturesCreated()).isEqualTo(1);
        assertThat(stale.fixturesBlocked()).isEqualTo(1);
        assertThat(stale.anomalies()).isZero();
        assertThat(singleInstant("SELECT kickoff_at FROM canonical_fixture"))
                .hasToString("2026-08-16T12:00:00Z");
        assertThat(singleString("SELECT status FROM canonical_fixture")).isEqualTo("POSTPONED");
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-12T10:00:00Z");
        assertThat(applicationOutcomeCount("CREATED")).isEqualTo(1);
        assertThat(applicationOutcomeCount("STALE")).isEqualTo(1);
        assertThat(anomalyCount("STALE_OBSERVATION")).isZero();
    }

    @Test
    void newerUnchangedPrimaryObservationAdvancesTheAuthorityWatermark() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z"));
        UUID firstAuthorityObservation = singleUuid(
                "SELECT last_authority_observation_id FROM canonical_fixture");
        byte[] laterIdenticalPayload = replaceInFixture(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "2026-08-11T10:00:00Z",
                "2026-08-13T10:00:00Z");

        NormalizationResult later = normalizationService.normalize(snapshot(
                laterIdenticalPayload,
                "highlightly",
                "calendar/upl",
                "2026-08-13T10:00:01Z"));

        assertThat(later.fixturesUnchanged()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(2);
        assertThat(singleUuid("SELECT last_authority_observation_id FROM canonical_fixture"))
                .isNotEqualTo(firstAuthorityObservation);
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-13T10:00:00Z");
        assertThat(applicationOutcomeCount("UNCHANGED")).isEqualTo(1);
    }

    @Test
    void equalAuthorityTimeWithSameFactsKeepsThePreviousWatermark() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z"));
        UUID firstAuthorityObservation = singleUuid(
                "SELECT last_authority_observation_id FROM canonical_fixture");
        byte[] sameFactsDifferentEvidence = replaceInFixture(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "\"name\": \"FC Kryvbas Kryvyi Rih\"",
                "\"name\": \"Kryvbas source spelling\"");

        NormalizationResult result = normalizationService.normalize(snapshot(
                sameFactsDifferentEvidence,
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:05:01Z"));

        assertThat(result.fixturesUnchanged()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(2);
        assertThat(count("fixture_observation")).isEqualTo(2);
        assertThat(singleUuid("SELECT last_authority_observation_id FROM canonical_fixture"))
                .isEqualTo(firstAuthorityObservation);
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-11T10:00:00Z");
        assertThat(applicationOutcomeCount("UNCHANGED")).isEqualTo(1);
    }

    @Test
    void equalAuthorityTimeWithContradictoryFactsKeepsTheCanonAndCreatesAnAnomaly() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z"));
        byte[] contradictoryPayload = replaceInFixture(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "\"status\": \"SCHEDULED\"",
                "\"status\": \"POSTPONED\"");

        NormalizationResult conflict = normalizationService.normalize(snapshot(
                contradictoryPayload,
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:05:01Z"));

        assertThat(conflict.fixturesBlocked()).isEqualTo(1);
        assertThat(conflict.anomalies()).isEqualTo(1);
        assertThat(singleString("SELECT status FROM canonical_fixture")).isEqualTo("SCHEDULED");
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-11T10:00:00Z");
        assertThat(applicationOutcomeCount("EQUAL_AUTHORITY_TIME_CONFLICT")).isEqualTo(1);
        assertThat(anomalyCount("EQUAL_AUTHORITY_TIME_CONFLICT")).isEqualTo(1);
    }

    @Test
    void terminalFixtureCannotRegress() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        byte[] finishedPayload = replaceInFixture(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "\"status\": \"SCHEDULED\"",
                "\"status\": \"FINISHED\"");
        normalizationService.normalize(snapshot(
                finishedPayload,
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z"));
        byte[] regressionPayload = replaceInFixture(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "2026-08-11T10:00:00Z",
                "2026-08-12T10:00:00Z");

        NormalizationResult regression = normalizationService.normalize(snapshot(
                regressionPayload,
                "highlightly",
                "calendar/upl",
                "2026-08-12T10:00:01Z"));

        assertThat(regression.fixturesBlocked()).isEqualTo(1);
        assertThat(regression.anomalies()).isEqualTo(1);
        assertThat(singleString("SELECT status FROM canonical_fixture")).isEqualTo("FINISHED");
        assertThat(applicationOutcomeCount("INVALID_TRANSITION")).isEqualTo(1);
        assertThat(anomalyCount("INVALID_TRANSITION")).isEqualTo(1);
    }

    @Test
    void cancelledFixtureCannotRegress() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        byte[] cancelledPayload = replaceInFixture(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "\"status\": \"SCHEDULED\"",
                "\"status\": \"CANCELLED\"");
        normalizationService.normalize(snapshot(
                cancelledPayload,
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:00:01Z"));
        byte[] regressionPayload = replaceInFixture(
                "/fixtures/cat001/calendar-v2-highlightly-corrected.json",
                "\"status\": \"POSTPONED\"",
                "\"status\": \"FINISHED\"");

        NormalizationResult regression = normalizationService.normalize(snapshot(
                regressionPayload,
                "highlightly",
                "calendar/upl",
                "2026-08-12T10:00:01Z"));

        assertThat(regression.fixturesBlocked()).isEqualTo(1);
        assertThat(regression.anomalies()).isEqualTo(1);
        assertThat(singleString("SELECT status FROM canonical_fixture")).isEqualTo("CANCELLED");
        assertThat(applicationOutcomeCount("INVALID_TRANSITION")).isEqualTo(1);
        assertThat(anomalyCount("INVALID_TRANSITION")).isEqualTo(1);
    }

    @Test
    void postponedFixtureCanReturnToScheduledAfterAReschedule() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        bindHighlightly(canonical);
        normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-highlightly-corrected.json",
                "highlightly",
                "calendar/upl",
                "2026-08-12T10:00:01Z"));
        byte[] rescheduledPayload = replaceInFixture(
                "/fixtures/cat001/calendar-v2-highlightly.json",
                "2026-08-11T10:00:00Z",
                "2026-08-13T10:00:00Z");

        NormalizationResult result = normalizationService.normalize(snapshot(
                rescheduledPayload,
                "highlightly",
                "calendar/upl",
                "2026-08-13T10:00:01Z"));

        assertThat(result.fixturesUpdated()).isEqualTo(1);
        assertThat(singleString("SELECT status FROM canonical_fixture")).isEqualTo("SCHEDULED");
        assertThat(singleInstant("SELECT kickoff_at FROM canonical_fixture"))
                .hasToString("2026-08-15T12:00:00Z");
        assertThat(singleInstant("SELECT last_authority_observed_at FROM canonical_fixture"))
                .hasToString("2026-08-13T10:00:00Z");
    }

    @Test
    void hirnykRemainsAmbiguousAndCannotBecomeKryvbas() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        confirmCompetition("highlightly", "hly-upl", canonical.competitionId());
        confirmTeam("highlightly", "hly-livyi-bereh", canonical.awayTeamId());
        insertMapping(ProviderMapping.ambiguous(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "", "", 0.45,
                Instant.parse("2026-09-01T10:00:00Z")));

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
        assertThat(applicationOutcomeCount("BLOCKED")).isEqualTo(1);
        assertThat(canonical.homeTeamId()).isNotNull();
    }

    @Test
    void mappingAnomalyIsObservedResolvedAndReopenedAcrossCompleteAssessments() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        confirmCompetition("highlightly", "hly-upl", canonical.competitionId());
        confirmTeam("highlightly", "hly-livyi-bereh", canonical.awayTeamId());
        insertMapping(ProviderMapping.ambiguous(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "", "", 0.45,
                Instant.parse("2026-09-01T10:00:00Z")));
        RawSnapshot snapshot = snapshot(
                "/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:10:01Z");

        NormalizationResult opened = normalizationService.normalize(snapshot);
        NormalizationResult observed = normalizationService.normalize(snapshot);

        assertThat(opened.anomalies()).isEqualTo(1);
        assertThat(observed.anomalies()).isEqualTo(1);
        assertThat(anomalyCount("AMBIGUOUS_MAPPING")).isEqualTo(1);
        assertThat(singleLong("""
                SELECT occurrence_count
                FROM normalization_anomaly
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """)).isEqualTo(2);
        assertThat(anomalyEventTypes()).containsExactly("OPENED", "OBSERVED");
        assertThat(singleLong("""
                SELECT COUNT(*)
                FROM normalization_anomaly_event
                WHERE fixture_application_log_id IS NOT NULL
                """)).isEqualTo(2);

        updateHirnykMapping(
                MappingStatus.CONFIRMED, canonical.homeTeamId(), 1.0,
                Instant.parse("2026-09-01T11:00:00Z"));
        NormalizationResult resolved = normalizationService.normalize(snapshot);

        assertThat(resolved.fixturesCreated()).isEqualTo(1);
        assertThat(resolved.anomalies()).isZero();
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """)).isEqualTo("RESOLVED");
        assertThat(anomalyEventTypes()).containsExactly("OPENED", "OBSERVED", "RESOLVED");

        updateHirnykMapping(
                MappingStatus.AMBIGUOUS, null, 0.45,
                Instant.parse("2026-09-01T12:00:00Z"));
        NormalizationResult reopened = normalizationService.normalize(snapshot);

        assertThat(reopened.fixturesBlocked()).isEqualTo(1);
        assertThat(reopened.anomalies()).isEqualTo(1);
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """)).isEqualTo("OPEN");
        assertThat(singleLong("""
                SELECT occurrence_count
                FROM normalization_anomaly
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """)).isEqualTo(3);
        assertThat(singleLong("""
                SELECT version
                FROM normalization_anomaly
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """)).isEqualTo(4);
        assertThat(anomalyEventTypes())
                .containsExactly("OPENED", "OBSERVED", "RESOLVED", "REOPENED");
        assertThat(count("normalization_anomaly")).isEqualTo(1);
        assertThat(count("normalization_anomaly_event")).isEqualTo(4);

        jdbcClient.sql("""
                UPDATE normalization_anomaly
                SET status = 'IGNORED',
                    resolved_at = updated_at + INTERVAL '1 millisecond',
                    updated_at = updated_at + INTERVAL '1 millisecond',
                    version = version + 1
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """)
                .update();

        NormalizationResult ignored = normalizationService.normalize(snapshot);

        assertThat(ignored.anomalies()).isEqualTo(1);
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """)).isEqualTo("IGNORED");
        assertThat(singleLong("""
                SELECT occurrence_count
                FROM normalization_anomaly
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """)).isEqualTo(4);
        assertThat(anomalyEventTypes()).containsExactly(
                "OPENED", "OBSERVED", "RESOLVED", "REOPENED", "OBSERVED");
    }

    @Test
    void uniqueHistoricalAnomalyWithoutContextIsEnrichedAndObserved() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        confirmCompetition("highlightly", "hly-upl", canonical.competitionId());
        confirmTeam("highlightly", "hly-livyi-bereh", canonical.awayTeamId());
        insertMapping(ProviderMapping.ambiguous(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "", "", 0.45,
                Instant.parse("2026-09-01T10:00:00Z")));
        RawSnapshot snapshot = snapshot(
                "/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:10:01Z");

        normalizationService.normalize(snapshot);
        UUID historicalId = singleUuid("""
                SELECT id
                FROM normalization_anomaly
                WHERE anomaly_code = 'AMBIGUOUS_MAPPING'
                """);
        jdbcClient.sql("""
                UPDATE normalization_anomaly
                SET season = NULL,
                    phase = NULL
                WHERE id = :id
                """)
                .param("id", historicalId)
                .update();

        NormalizationResult observed = normalizationService.normalize(snapshot);

        assertThat(observed.anomalies()).isEqualTo(1);
        assertThat(count("normalization_anomaly")).isEqualTo(1);
        assertThat(singleUuid("SELECT id FROM normalization_anomaly")).isEqualTo(historicalId);
        assertThat(singleString("SELECT season FROM normalization_anomaly")).isEmpty();
        assertThat(singleString("SELECT phase FROM normalization_anomaly")).isEmpty();
        assertThat(singleLong("SELECT occurrence_count FROM normalization_anomaly")).isEqualTo(2);
        assertThat(anomalyEventTypes()).containsExactly("OPENED", "OBSERVED");
    }

    @Test
    void ambiguousHistoricalAnomalyCandidatesAreNotEnrichedArbitrarily() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        confirmCompetition("highlightly", "hly-upl", canonical.competitionId());
        confirmTeam("highlightly", "hly-livyi-bereh", canonical.awayTeamId());
        insertMapping(ProviderMapping.ambiguous(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "", "", 0.45,
                Instant.parse("2026-09-01T10:00:00Z")));
        RawSnapshot snapshot = snapshot(
                "/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:10:01Z");

        NormalizationResult first = normalizationService.normalize(snapshot);
        UUID historicalId = singleUuid("SELECT id FROM normalization_anomaly");
        jdbcClient.sql("""
                UPDATE normalization_anomaly
                SET season = NULL,
                    phase = NULL
                WHERE id = :id
                """)
                .param("id", historicalId)
                .update();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-01T16:00:00Z");
        jdbcClient.sql("""
                INSERT INTO normalization_anomaly (
                    id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                    season, phase, anomaly_code, details, status, version, created_at,
                    last_seen_at, updated_at, resolved_at, occurrence_count
                ) VALUES (
                    :id, :rawSnapshotId, 'highlightly', 'TEAM', 'hly-hirnyk',
                    'another-season', 'another-phase', 'AMBIGUOUS_MAPPING',
                    'Another historical context', 'OPEN', 1, :timestamp, :timestamp,
                    :timestamp, NULL, 1
                )
                """)
                .param("id", UUID.randomUUID())
                .param("rawSnapshotId", first.snapshotId())
                .param("timestamp", timestamp)
                .update();

        normalizationService.normalize(snapshot);

        assertThat(count("normalization_anomaly")).isEqualTo(3);
        assertThat(singleBoolean("""
                SELECT season IS NULL AND phase IS NULL
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(historicalId))).isTrue();
        UUID exactOccurrenceId = singleUuid("""
                SELECT id
                FROM normalization_anomaly
                WHERE season = ''
                  AND phase = ''
                  AND anomaly_code = 'AMBIGUOUS_MAPPING'
                """);
        assertThat(exactOccurrenceId).isNotEqualTo(historicalId);
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(exactOccurrenceId))).isEqualTo("OPEN");
    }

    @Test
    void rejectedMappingBlocksNormalizationWithoutBeingRecreated() throws IOException {
        CanonicalIds canonical = registerCanonicalEntities();
        confirmCompetition("highlightly", "hly-upl", canonical.competitionId());
        confirmTeam("highlightly", "hly-livyi-bereh", canonical.awayTeamId());
        ProviderMapping rejected = ProviderMapping.rejected(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "", "",
                Instant.parse("2026-09-01T10:00:00Z"));
        insertMapping(rejected);

        NormalizationResult result = normalizationService.normalize(snapshot(
                "/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json",
                "highlightly",
                "calendar/upl",
                "2026-08-11T10:10:01Z"));

        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(result.anomalies()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(anomalyCount("REJECTED_MAPPING")).isEqualTo(1);
        assertThat(singleUuid("""
                SELECT id
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                """)).isEqualTo(rejected.id());
        assertThat(singleString("""
                SELECT mapping_status
                FROM provider_mapping
                WHERE id = '%s'
                """.formatted(rejected.id()))).isEqualTo("REJECTED");
        assertThat(singleLong("""
                SELECT COUNT(*)
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                """)).isEqualTo(1);
    }

    @Test
    void canonicalV3PersistsNeutralVenueWithoutInferringUnorderedParticipants() throws IOException {
        CanonicalIds canonical = registerSyntheticEntities(
                "Synthetic League", CompetitionType.DOMESTIC_LEAGUE,
                "Synthetic FC Alpha", "Synthetic FC Beta");
        bindSynthetic(
                canonical, "synthetic-league", PHASE,
                "synthetic-alpha", "synthetic-beta");

        NormalizationResult result = normalizationService.normalize(snapshot(
                "/fixtures/cat002/calendar-v3-ordered-neutral.json",
                "synthetic-provider",
                "calendar/synthetic-league",
                "2026-09-01T15:00:01Z"));

        assertThat(result.fixturesCreated()).isEqualTo(1);
        assertThat(singleBoolean("SELECT neutral_venue FROM canonical_fixture")).isTrue();
        assertThat(singleBoolean("SELECT participants_unordered FROM canonical_fixture")).isFalse();
        assertThat(singleBoolean("SELECT source_neutral_venue FROM fixture_observation")).isTrue();
        assertThat(singleBoolean("SELECT source_participants_unordered FROM fixture_observation")).isFalse();
        assertThat(singleString("SELECT source_schema_version FROM fixture_observation"))
                .isEqualTo("cal01-fixture-v3");
        assertThat(applicationOutcomeCount("CREATED")).isEqualTo(1);
    }

    @Test
    void canonicalV3PersistsUnknownNeutralityAndExplicitlyUnorderedParticipants() throws IOException {
        CanonicalIds canonical = registerSyntheticEntities(
                "Synthetic Cup", CompetitionType.DOMESTIC_CUP,
                "Synthetic FC Gamma", "Synthetic FC Delta");
        bindSynthetic(
                canonical, "synthetic-cup", "FINAL",
                "synthetic-gamma", "synthetic-delta");

        NormalizationResult result = normalizationService.normalize(snapshot(
                "/fixtures/cat002/calendar-v3-unordered-unknown-neutral.json",
                "synthetic-provider",
                "calendar/synthetic-cup",
                "2026-09-01T15:05:01Z"));

        assertThat(result.fixturesCreated()).isEqualTo(1);
        assertThat(singleBoolean("SELECT neutral_venue IS NULL FROM canonical_fixture")).isTrue();
        assertThat(singleBoolean("SELECT participants_unordered FROM canonical_fixture")).isTrue();
        assertThat(singleBoolean("SELECT source_neutral_venue IS NULL FROM fixture_observation")).isTrue();
        assertThat(singleBoolean("SELECT source_participants_unordered FROM fixture_observation")).isTrue();
        assertThat(singleString("SELECT provider_home_team_id FROM fixture_observation"))
                .isEqualTo("synthetic-gamma");
        assertThat(singleString("SELECT provider_away_team_id FROM fixture_observation"))
                .isEqualTo("synthetic-delta");
    }

    @Test
    void explicitlyUnorderedInversionResolvesTheExistingFixtureAndPreservesSourceOrder() throws IOException {
        CanonicalIds canonical = registerSyntheticEntities(
                "Synthetic Cup", CompetitionType.DOMESTIC_CUP,
                "Synthetic FC Gamma", "Synthetic FC Delta");
        bindSynthetic(
                canonical, "synthetic-cup", "FINAL",
                "synthetic-gamma", "synthetic-delta");
        normalizationService.normalize(snapshot(
                "/fixtures/cat002/calendar-v3-unordered-unknown-neutral.json",
                "synthetic-provider",
                "calendar/synthetic-cup",
                "2026-09-01T15:05:01Z"));
        byte[] inverted = swapProviderTeams(
                replaceInFixture(
                        "/fixtures/cat002/calendar-v3-unordered-unknown-neutral.json",
                        "2026-09-01T15:05:00Z",
                        "2026-09-01T16:05:00Z"),
                "synthetic-gamma",
                "synthetic-delta");

        NormalizationResult result = normalizationService.normalize(snapshot(
                inverted,
                "synthetic-provider",
                "calendar/synthetic-cup",
                "2026-09-01T16:05:01Z"));

        assertThat(result.fixturesUnchanged()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(singleUuid("SELECT home_team_id FROM canonical_fixture"))
                .isEqualTo(canonical.homeTeamId());
        assertThat(singleUuid("SELECT away_team_id FROM canonical_fixture"))
                .isEqualTo(canonical.awayTeamId());
        assertThat(singleString("""
                SELECT provider_home_team_id
                FROM fixture_observation
                ORDER BY observed_at DESC
                LIMIT 1
                """)).isEqualTo("synthetic-delta");
        assertThat(singleString("""
                SELECT provider_away_team_id
                FROM fixture_observation
                ORDER BY observed_at DESC
                LIMIT 1
                """)).isEqualTo("synthetic-gamma");
        assertThat(anomalyCount("PARTICIPANT_ORDER_CONFLICT")).isZero();
    }

    @Test
    void neutralVenueAloneDoesNotAllowAnOrderedInversion() throws IOException {
        CanonicalIds canonical = registerSyntheticEntities(
                "Synthetic League", CompetitionType.DOMESTIC_LEAGUE,
                "Synthetic FC Alpha", "Synthetic FC Beta");
        bindSynthetic(
                canonical, "synthetic-league", PHASE,
                "synthetic-alpha", "synthetic-beta");
        normalizationService.normalize(snapshot(
                "/fixtures/cat002/calendar-v3-ordered-neutral.json",
                "synthetic-provider",
                "calendar/synthetic-league",
                "2026-09-01T15:00:01Z"));
        byte[] inverted = swapProviderTeams(
                replaceInFixture(
                        "/fixtures/cat002/calendar-v3-ordered-neutral.json",
                        "2026-09-01T15:00:00Z",
                        "2026-09-01T16:00:00Z"),
                "synthetic-alpha",
                "synthetic-beta");

        NormalizationResult result = normalizationService.normalize(snapshot(
                inverted,
                "synthetic-provider",
                "calendar/synthetic-league",
                "2026-09-01T16:00:01Z"));

        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(result.anomalies()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(singleUuid("SELECT home_team_id FROM canonical_fixture"))
                .isEqualTo(canonical.homeTeamId());
        assertThat(singleUuid("SELECT away_team_id FROM canonical_fixture"))
                .isEqualTo(canonical.awayTeamId());
        assertThat(applicationOutcomeCount("BLOCKED")).isEqualTo(1);
        assertThat(anomalyCount("PARTICIPANT_ORDER_CONFLICT")).isEqualTo(1);
    }

    @Test
    void orderedSourceChoosesTheExactHistoricalCandidateWhenReverseAlsoExists() throws IOException {
        CanonicalIds canonical = registerSyntheticEntities(
                "Synthetic League", CompetitionType.DOMESTIC_LEAGUE,
                "Synthetic FC Alpha", "Synthetic FC Beta");
        bindSynthetic(
                canonical, "synthetic-league", PHASE,
                "synthetic-alpha", "synthetic-beta");
        CanonicalFixture exact = insertHistoricalFixture(
                canonical, PHASE, true, false,
                canonical.homeTeamId(), canonical.awayTeamId(),
                Instant.parse("2026-09-10T18:00:00Z"));
        insertHistoricalFixture(
                canonical, PHASE, true, false,
                canonical.awayTeamId(), canonical.homeTeamId(),
                Instant.parse("2026-09-10T18:00:00Z"));

        NormalizationResult result = normalizationService.normalize(snapshot(
                "/fixtures/cat002/calendar-v3-ordered-neutral.json",
                "synthetic-provider",
                "calendar/synthetic-league",
                "2026-09-01T15:00:01Z"));

        assertThat(result.fixturesUnchanged()).isEqualTo(1);
        assertThat(result.fixturesBlocked()).isZero();
        assertThat(count("canonical_fixture")).isEqualTo(2);
        assertThat(singleUuid("""
                SELECT canonical_entity_id
                FROM provider_mapping
                WHERE provider = 'synthetic-provider'
                  AND entity_type = 'FIXTURE'
                  AND provider_entity_id = 'synthetic-v3-ordered-001'
                """)).isEqualTo(exact.id());
        assertThat(anomalyCount("AMBIGUOUS_FIXTURE_IDENTITY")).isZero();
    }

    @Test
    void unorderedSourceIsBlockedWhenExactAndReverseHistoricalCandidatesBothExist() throws IOException {
        CanonicalIds canonical = registerSyntheticEntities(
                "Synthetic Cup", CompetitionType.DOMESTIC_CUP,
                "Synthetic FC Gamma", "Synthetic FC Delta");
        bindSynthetic(
                canonical, "synthetic-cup", "FINAL",
                "synthetic-gamma", "synthetic-delta");
        insertHistoricalFixture(
                canonical, "FINAL", null, true,
                canonical.homeTeamId(), canonical.awayTeamId(),
                Instant.parse("2026-09-12T19:00:00Z"));
        insertHistoricalFixture(
                canonical, "FINAL", null, true,
                canonical.awayTeamId(), canonical.homeTeamId(),
                Instant.parse("2026-09-12T19:00:00Z"));

        NormalizationResult result = normalizationService.normalize(snapshot(
                "/fixtures/cat002/calendar-v3-unordered-unknown-neutral.json",
                "synthetic-provider",
                "calendar/synthetic-cup",
                "2026-09-01T15:05:01Z"));

        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(result.anomalies()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(2);
        assertThat(fixtureMappings()).isZero();
        assertThat(applicationOutcomeCount("BLOCKED")).isEqualTo(1);
        assertThat(anomalyCount("AMBIGUOUS_FIXTURE_IDENTITY")).isEqualTo(1);
    }

    @Test
    void structurallyInvalidV3KeepsRawSnapshotWithoutPartialNormalization() throws IOException {
        String invalidPayload = new String(
                fixture("/fixtures/cat002/calendar-v3-ordered-neutral.json"),
                StandardCharsets.UTF_8)
                .replace("\"participantsUnordered\": false", "\"participantsUnordered\": \"false\"");
        RawSnapshot snapshot = snapshot(
                invalidPayload.getBytes(StandardCharsets.UTF_8),
                "synthetic-provider",
                "calendar/invalid-v3",
                "2026-09-01T15:10:01Z");

        NormalizationResult result = normalizationService.normalize(snapshot);

        assertThat(result.compatible()).isFalse();
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(count("fixture_observation")).isZero();
        assertThat(count("fixture_application_log")).isZero();
        assertThat(anomalyCount("INVALID_SNAPSHOT")).isEqualTo(1);
        assertThat(singleString("""
                SELECT details
                FROM normalization_anomaly
                WHERE anomaly_code = 'INVALID_SNAPSHOT'
                """)).isEqualTo("Calendar snapshot payload is invalid");
        assertIncompleteAssessmentPreservesOpenAnomaly(snapshot, result, "pending-invalid-v3");
    }

    @Test
    void missingObservedAtDoesNotFallBackToSnapshotReceivedAt() throws IOException {
        String invalidPayload = new String(
                fixture("/fixtures/cat002/calendar-v3-ordered-neutral.json"),
                StandardCharsets.UTF_8)
                .replaceFirst("\\s*\"observedAt\"\\s*:\\s*\"[^\"]+\"\\s*,", "");

        NormalizationResult result = normalizationService.normalize(snapshot(
                invalidPayload.getBytes(StandardCharsets.UTF_8),
                "synthetic-provider",
                "calendar/missing-observed-at",
                "2026-09-01T15:15:01Z"));

        assertThat(result.compatible()).isFalse();
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(count("fixture_observation")).isZero();
        assertThat(anomalyCount("INVALID_SNAPSHOT")).isEqualTo(1);
    }

    @Test
    void unsupportedCanonicalStatusCreatesRejectedObservationAndApplication() throws IOException {
        String invalidStatusPayload = new String(
                fixture("/fixtures/cat002/calendar-v3-ordered-neutral.json"),
                StandardCharsets.UTF_8)
                .replace("\"status\": \"SCHEDULED\"", "\"status\": \"NOT_A_STATUS\"");

        NormalizationResult result = normalizationService.normalize(snapshot(
                invalidStatusPayload.getBytes(StandardCharsets.UTF_8),
                "synthetic-provider",
                "calendar/invalid-status",
                "2026-09-01T15:20:01Z"));

        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(singleString("SELECT normalization_status FROM fixture_observation"))
                .isEqualTo("REJECTED");
        assertThat(applicationOutcomeCount("REJECTED")).isEqualTo(1);
        assertThat(anomalyCount("INVALID_FIXTURE")).isEqualTo(1);
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
        assertThat(singleString("""
                SELECT details
                FROM normalization_anomaly
                WHERE anomaly_code = 'UNSUPPORTED_SCHEMA'
                """)).isEqualTo("Unsupported calendar fixture schema");
        assertIncompleteAssessmentPreservesOpenAnomaly(snapshot, result, "pending-unsupported-schema");
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
        assertIncompleteAssessmentPreservesOpenAnomaly(snapshot, result, "pending-legacy-schema");
    }

    private CanonicalIds registerCanonicalEntities() {
        UUID competitionId = commandService.registerCompetition(
                "Ukrainian Premier League", "UKR", CompetitionType.DOMESTIC_LEAGUE);
        UUID homeTeamId = commandService.registerTeam("FC Kryvbas Kryvyi Rih", "UKR");
        UUID awayTeamId = commandService.registerTeam("FC Livyi Bereh Kyiv", "UKR");
        return new CanonicalIds(competitionId, homeTeamId, awayTeamId);
    }

    private CanonicalIds registerSyntheticEntities(
            String competitionName,
            CompetitionType competitionType,
            String homeTeamName,
            String awayTeamName) {
        UUID competitionId = commandService.registerCompetition(competitionName, "FRA", competitionType);
        UUID homeTeamId = commandService.registerTeam(homeTeamName, "FRA");
        UUID awayTeamId = commandService.registerTeam(awayTeamName, "FRA");
        return new CanonicalIds(competitionId, homeTeamId, awayTeamId);
    }

    private CanonicalFixture insertHistoricalFixture(
            CanonicalIds canonical,
            String phase,
            Boolean neutralVenue,
            boolean participantsUnordered,
            UUID homeTeamId,
            UUID awayTeamId,
            Instant kickoff) {
        Instant now = Instant.parse("2026-09-01T14:00:00Z");
        CanonicalSeason season = catalogRepository.getOrCreateSeason(new CanonicalSeason(
                UUID.randomUUID(), canonical.competitionId(), SEASON,
                null, null, now, now));
        return catalogRepository.insertOrResolveFixture(new CanonicalFixture(
                UUID.randomUUID(),
                canonical.competitionId(),
                season.id(),
                homeTeamId,
                awayTeamId,
                neutralVenue,
                participantsUnordered,
                kickoff,
                FixtureStatus.SCHEDULED,
                phase,
                null,
                now,
                now)).fixture();
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

    private void bindSynthetic(
            CanonicalIds canonical,
            String providerCompetitionId,
            String phase,
            String providerHomeTeamId,
            String providerAwayTeamId) {
        insertMapping(ProviderMapping.confirmed(
                "synthetic-provider", ProviderEntityType.COMPETITION, providerCompetitionId,
                canonical.competitionId(), SEASON, phase,
                Instant.parse("2026-09-01T10:00:00Z")));
        confirmTeam("synthetic-provider", providerHomeTeamId, canonical.homeTeamId());
        confirmTeam("synthetic-provider", providerAwayTeamId, canonical.awayTeamId());
    }

    private void confirmCompetition(String provider, String providerId, UUID canonicalId) {
        insertMapping(ProviderMapping.confirmed(
                provider, ProviderEntityType.COMPETITION, providerId, canonicalId, SEASON, PHASE,
                Instant.parse("2026-09-01T10:00:00Z")));
    }

    private void confirmTeam(String provider, String providerId, UUID canonicalId) {
        insertMapping(ProviderMapping.confirmed(
                provider, ProviderEntityType.TEAM, providerId, canonicalId, "", "",
                Instant.parse("2026-09-01T10:00:00Z")));
    }

    private void insertMapping(ProviderMapping candidate) {
        StoredProviderMapping stored = mappingRepository.insertIfAbsentAndResolve(candidate);
        ProviderMapping mapping = stored.mapping();
        if (mapping.status() != candidate.status()
                || !java.util.Objects.equals(
                        mapping.canonicalEntityId(), candidate.canonicalEntityId())) {
            throw new IllegalStateException("Test mapping conflicts with an existing mapping");
        }
    }

    private void updateHirnykMapping(
            MappingStatus status,
            UUID canonicalEntityId,
            Double confidence,
            Instant updatedAt) {
        int updated = jdbcClient.sql("""
                UPDATE provider_mapping
                SET mapping_status = :status,
                    canonical_entity_id = :canonicalEntityId,
                    confidence = :confidence,
                    version = version + 1,
                    updated_at = :updatedAt
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                  AND season = ''
                  AND phase = ''
                """)
                .param("status", status.name())
                .param("canonicalEntityId", canonicalEntityId)
                .param("confidence", confidence)
                .param("updatedAt", updatedAt.atOffset(java.time.ZoneOffset.UTC))
                .update();
        assertThat(updated).isEqualTo(1);
    }

    private RawSnapshot snapshot(String path, String provider, String endpoint, String receivedAt) throws IOException {
        return snapshot(fixture(path), provider, endpoint, receivedAt);
    }

    private RawSnapshot snapshot(byte[] payload, String provider, String endpoint, String receivedAt) {
        return RawSnapshot.capture(
                provider,
                endpoint,
                Instant.parse(receivedAt),
                payload,
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

    private byte[] replaceInFixture(String path, String expected, String replacement) throws IOException {
        return replaceInPayload(fixture(path), expected, replacement);
    }

    private byte[] replaceInPayload(byte[] payload, String expected, String replacement) {
        String json = new String(payload, StandardCharsets.UTF_8);
        if (!json.contains(expected)) {
            throw new IllegalArgumentException("Fixture does not contain expected text: " + expected);
        }
        return json.replace(expected, replacement).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] swapProviderTeams(byte[] payload, String firstProviderTeamId, String secondProviderTeamId) {
        String placeholder = "cat002-team-swap-placeholder";
        String json = new String(payload, StandardCharsets.UTF_8);
        if (!json.contains(firstProviderTeamId) || !json.contains(secondProviderTeamId)) {
            throw new IllegalArgumentException("Fixture does not contain both provider team identifiers");
        }
        return json.replace(firstProviderTeamId, placeholder)
                .replace(secondProviderTeamId, firstProviderTeamId)
                .replace(placeholder, secondProviderTeamId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private long singleLong(String sql) {
        return jdbcClient.sql(sql)
                .query(Long.class)
                .single();
    }

    private List<String> anomalyEventTypes() {
        return jdbcClient.sql("""
                SELECT event_type
                FROM normalization_anomaly_event
                ORDER BY created_at, id
                """)
                .query(String.class)
                .list();
    }

    private void assertIncompleteAssessmentPreservesOpenAnomaly(
            RawSnapshot snapshot,
            NormalizationResult firstResult,
            String marker) {
        UUID pendingId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-01T16:00:00Z");
        jdbcClient.sql("""
                INSERT INTO normalization_anomaly (
                    id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                    season, phase, anomaly_code, details, status, version, created_at,
                    last_seen_at, updated_at, resolved_at, occurrence_count
                ) VALUES (
                    :id, :rawSnapshotId, :provider, 'SNAPSHOT', :providerEntityId,
                    NULL, NULL, 'MAPPING_CONFLICT', 'Pending anomaly must remain open',
                    'OPEN', 1, :timestamp, :timestamp, :timestamp, NULL, 1
                )
                """)
                .param("id", pendingId)
                .param("rawSnapshotId", firstResult.snapshotId())
                .param("provider", snapshot.provider())
                .param("providerEntityId", marker)
                .param("timestamp", timestamp)
                .update();

        NormalizationResult replay = normalizationService.normalize(snapshot);

        assertThat(replay.snapshotId()).isEqualTo(firstResult.snapshotId());
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(pendingId))).isEqualTo("OPEN");
    }

    private long fixtureMappings() {
        return jdbcClient.sql("SELECT COUNT(*) FROM provider_mapping WHERE entity_type = 'FIXTURE'")
                .query(Long.class)
                .single();
    }

    private long applicationOutcomeCount(String outcome) {
        return jdbcClient.sql("SELECT COUNT(*) FROM fixture_application_log WHERE outcome = :outcome")
                .param("outcome", outcome)
                .query(Long.class)
                .single();
    }

    private String singleString(String sql) {
        return jdbcClient.sql(sql)
                .query(String.class)
                .single();
    }

    private boolean singleBoolean(String sql) {
        return jdbcClient.sql(sql)
                .query(Boolean.class)
                .single();
    }

    private UUID singleUuid(String sql) {
        return jdbcClient.sql(sql)
                .query(UUID.class)
                .single();
    }

    private Instant singleInstant(String sql) {
        return jdbcClient.sql(sql)
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }

    private long anomalyCount(String code) {
        return jdbcClient.sql("SELECT COUNT(*) FROM normalization_anomaly WHERE anomaly_code = :code")
                .param("code", code)
                .query(Long.class)
                .single();
    }

    private String storedAuthorityValue(String field) {
        return switch (field) {
            case "provider" -> singleString("SELECT provider FROM fixture_observation");
            case "providerCompetitionId" -> singleString(
                    "SELECT provider_competition_id FROM fixture_observation");
            case "season" -> singleString("SELECT source_season FROM fixture_observation");
            case "phase" -> singleString("SELECT source_phase FROM fixture_observation");
            default -> throw new IllegalArgumentException("Unsupported authority key field: " + field);
        };
    }

    private static Stream<Arguments> wildcardLikeRuntimeAuthorityValues() {
        return Stream.of("*", "?", "%")
                .flatMap(character -> Stream.of(
                        Arguments.of(
                                "provider",
                                character,
                                "\"provider\": \"synthetic-provider\"",
                                "\"provider\": \"runtime" + character + "provider\"",
                                "runtime" + character + "provider",
                                "runtime" + character + "provider"),
                        Arguments.of(
                                "providerCompetitionId",
                                character,
                                "\"providerCompetitionId\": \"synthetic-league\"",
                                "\"providerCompetitionId\": \"runtime" + character + "competition\"",
                                "synthetic-provider",
                                "runtime" + character + "competition"),
                        Arguments.of(
                                "season",
                                character,
                                "\"season\": \"2026/2027\"",
                                "\"season\": \"2026" + character + "2027\"",
                                "synthetic-provider",
                                "2026" + character + "2027"),
                        Arguments.of(
                                "phase",
                                character,
                                "\"phase\": \"REGULAR_SEASON\"",
                                "\"phase\": \"REGULAR" + character + "SEASON\"",
                                "synthetic-provider",
                                "REGULAR" + character + "SEASON")));
    }

    private void deleteCommittedRollbackTestData() {
        jdbcClient.sql("""
                UPDATE canonical_fixture
                SET last_authority_observation_id = NULL,
                    last_authority_observed_at = NULL,
                    last_authority_provider = NULL,
                    last_authority_policy_version = NULL
                """).update();
        for (String table : new String[] {
                "normalization_anomaly_event",
                "fixture_application_log",
                "normalization_anomaly",
                "fixture_observation",
                "provider_mapping",
                "canonical_fixture",
                "canonical_season",
                "canonical_team",
                "canonical_competition",
                "raw_snapshot"
        }) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }

    private record CanonicalIds(UUID competitionId, UUID homeTeamId, UUID awayTeamId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SyntheticCalendarAuthorityConfiguration {

        @Bean
        @Primary
        CalendarAuthorityPolicy syntheticCalendarAuthorityPolicy() {
            return new ConfiguredCalendarAuthorityPolicy(
                    "cat-002-lot4-test-policy-v1",
                    List.of(
                            primary("highlightly", "hly-upl", PHASE),
                            primary("highlightly", "284167", PHASE),
                            primary("synthetic-primary-peer", "fd-upl", PHASE),
                            control("football-data", "fd-upl", PHASE),
                            primary("synthetic-provider", "synthetic-league", PHASE),
                            primary("synthetic-provider", "synthetic-cup", "FINAL")));
        }

        private static CalendarAuthorityAssignment primary(
                String provider,
                String providerCompetitionId,
                String phase) {
            return assignment(provider, providerCompetitionId, phase, CalendarAuthorityRole.PRIMARY);
        }

        private static CalendarAuthorityAssignment control(
                String provider,
                String providerCompetitionId,
                String phase) {
            return assignment(provider, providerCompetitionId, phase, CalendarAuthorityRole.CONTROL);
        }

        private static CalendarAuthorityAssignment assignment(
                String provider,
                String providerCompetitionId,
                String phase,
                CalendarAuthorityRole role) {
            return new CalendarAuthorityAssignment(
                    new CalendarAuthorityKey(
                            provider,
                            providerCompetitionId,
                            SEASON,
                            phase,
                            CalendarAuthorityDataType.CALENDAR),
                    role);
        }
    }
}
