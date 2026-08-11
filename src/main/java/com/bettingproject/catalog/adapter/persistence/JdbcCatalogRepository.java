package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.CatalogRepository;
import com.bettingproject.catalog.domain.CanonicalCompetition;
import com.bettingproject.catalog.domain.CanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalSeason;
import com.bettingproject.catalog.domain.CanonicalTeam;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.catalog.domain.FixtureObservation;
import com.bettingproject.catalog.domain.FixtureStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcCatalogRepository implements CatalogRepository {

    private final JdbcClient jdbcClient;

    public JdbcCatalogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<CanonicalCompetition> findCompetition(String name, String countryCode) {
        return jdbcClient.sql("""
                SELECT id, canonical_name, country_code, competition_type, created_at, updated_at
                FROM canonical_competition
                WHERE canonical_name = :name AND country_code = :countryCode
                """)
                .param("name", name)
                .param("countryCode", countryCode)
                .query(this::mapCompetition)
                .optional();
    }

    @Override
    public void insertCompetition(CanonicalCompetition competition) {
        jdbcClient.sql("""
                INSERT INTO canonical_competition (
                    id, canonical_name, country_code, competition_type, created_at, updated_at
                ) VALUES (
                    :id, :name, :countryCode, :type, :createdAt, :updatedAt
                )
                """)
                .param("id", competition.id())
                .param("name", competition.name())
                .param("countryCode", competition.countryCode())
                .param("type", competition.type().name())
                .param("createdAt", utc(competition.createdAt()))
                .param("updatedAt", utc(competition.updatedAt()))
                .update();
    }

    @Override
    public Optional<CanonicalSeason> findSeason(UUID competitionId, String label) {
        return jdbcClient.sql("""
                SELECT id, competition_id, season_label, starts_on, ends_on, created_at, updated_at
                FROM canonical_season
                WHERE competition_id = :competitionId AND season_label = :label
                """)
                .param("competitionId", competitionId)
                .param("label", label)
                .query(this::mapSeason)
                .optional();
    }

    @Override
    public void insertSeason(CanonicalSeason season) {
        jdbcClient.sql("""
                INSERT INTO canonical_season (
                    id, competition_id, season_label, starts_on, ends_on, created_at, updated_at
                ) VALUES (
                    :id, :competitionId, :label, :startsOn, :endsOn, :createdAt, :updatedAt
                )
                """)
                .param("id", season.id())
                .param("competitionId", season.competitionId())
                .param("label", season.label())
                .param("startsOn", season.startsOn())
                .param("endsOn", season.endsOn())
                .param("createdAt", utc(season.createdAt()))
                .param("updatedAt", utc(season.updatedAt()))
                .update();
    }

    @Override
    public Optional<CanonicalTeam> findTeam(String name, String countryCode) {
        return jdbcClient.sql("""
                SELECT id, canonical_name, country_code, created_at, updated_at
                FROM canonical_team
                WHERE canonical_name = :name AND country_code = :countryCode
                """)
                .param("name", name)
                .param("countryCode", countryCode)
                .query(this::mapTeam)
                .optional();
    }

    @Override
    public void insertTeam(CanonicalTeam team) {
        jdbcClient.sql("""
                INSERT INTO canonical_team (
                    id, canonical_name, country_code, created_at, updated_at
                ) VALUES (
                    :id, :name, :countryCode, :createdAt, :updatedAt
                )
                """)
                .param("id", team.id())
                .param("name", team.name())
                .param("countryCode", team.countryCode())
                .param("createdAt", utc(team.createdAt()))
                .param("updatedAt", utc(team.updatedAt()))
                .update();
    }

    @Override
    public Optional<CanonicalFixture> findFixture(UUID fixtureId) {
        return fixtureQuery("WHERE id = :fixtureId")
                .param("fixtureId", fixtureId)
                .query(this::mapFixture)
                .optional();
    }

    @Override
    public Optional<CanonicalFixture> findFixture(
            UUID competitionId,
            UUID seasonId,
            UUID homeTeamId,
            UUID awayTeamId,
            java.time.Instant kickoff) {
        return fixtureQuery("""
                WHERE competition_id = :competitionId
                  AND season_id = :seasonId
                  AND home_team_id = :homeTeamId
                  AND away_team_id = :awayTeamId
                  AND kickoff_at = :kickoff
                """)
                .param("competitionId", competitionId)
                .param("seasonId", seasonId)
                .param("homeTeamId", homeTeamId)
                .param("awayTeamId", awayTeamId)
                .param("kickoff", utc(kickoff))
                .query(this::mapFixture)
                .optional();
    }

    @Override
    public void insertFixture(CanonicalFixture fixture) {
        jdbcClient.sql("""
                INSERT INTO canonical_fixture (
                    id, competition_id, season_id, home_team_id, away_team_id,
                    kickoff_at, status, phase, created_at, updated_at
                ) VALUES (
                    :id, :competitionId, :seasonId, :homeTeamId, :awayTeamId,
                    :kickoff, :status, :phase, :createdAt, :updatedAt
                )
                """)
                .param("id", fixture.id())
                .param("competitionId", fixture.competitionId())
                .param("seasonId", fixture.seasonId())
                .param("homeTeamId", fixture.homeTeamId())
                .param("awayTeamId", fixture.awayTeamId())
                .param("kickoff", utc(fixture.kickoff()))
                .param("status", fixture.status().name())
                .param("phase", fixture.phase())
                .param("createdAt", utc(fixture.createdAt()))
                .param("updatedAt", utc(fixture.updatedAt()))
                .update();
    }

    @Override
    public void updateFixture(CanonicalFixture fixture) {
        int updated = jdbcClient.sql("""
                UPDATE canonical_fixture
                SET kickoff_at = :kickoff,
                    status = :status,
                    phase = :phase,
                    updated_at = :updatedAt
                WHERE id = :id
                """)
                .param("id", fixture.id())
                .param("kickoff", utc(fixture.kickoff()))
                .param("status", fixture.status().name())
                .param("phase", fixture.phase())
                .param("updatedAt", utc(fixture.updatedAt()))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Canonical fixture no longer exists: " + fixture.id());
        }
    }

    @Override
    public boolean existsCompetition(UUID id) {
        return exists("canonical_competition", id);
    }

    @Override
    public boolean existsTeam(UUID id) {
        return exists("canonical_team", id);
    }

    @Override
    public boolean existsFixture(UUID id) {
        return exists("canonical_fixture", id);
    }

    @Override
    public boolean insertObservation(FixtureObservation observation) {
        int inserted = jdbcClient.sql("""
                INSERT INTO fixture_observation (
                    id, raw_snapshot_id, canonical_fixture_id, provider, provider_fixture_id,
                    provider_competition_id, provider_home_team_id, provider_away_team_id,
                    source_kickoff_at, source_status, source_phase, normalization_status,
                    reason_code, observed_at, created_at
                ) VALUES (
                    :id, :rawSnapshotId, :canonicalFixtureId, :provider, :providerFixtureId,
                    :providerCompetitionId, :providerHomeTeamId, :providerAwayTeamId,
                    :sourceKickoff, :sourceStatus, :sourcePhase, :normalizationStatus,
                    :reasonCode, :observedAt, :createdAt
                )
                ON CONFLICT (raw_snapshot_id, provider_fixture_id) DO NOTHING
                """)
                .param("id", observation.id())
                .param("rawSnapshotId", observation.rawSnapshotId())
                .param("canonicalFixtureId", observation.canonicalFixtureId())
                .param("provider", observation.provider())
                .param("providerFixtureId", observation.providerFixtureId())
                .param("providerCompetitionId", observation.providerCompetitionId())
                .param("providerHomeTeamId", observation.providerHomeTeamId())
                .param("providerAwayTeamId", observation.providerAwayTeamId())
                .param("sourceKickoff", utc(observation.sourceKickoff()))
                .param("sourceStatus", observation.sourceStatus())
                .param("sourcePhase", observation.sourcePhase())
                .param("normalizationStatus", observation.normalizationStatus().name())
                .param("reasonCode", observation.reasonCode())
                .param("observedAt", utc(observation.observedAt()))
                .param("createdAt", utc(observation.createdAt()))
                .update();
        return inserted == 1;
    }

    private JdbcClient.StatementSpec fixtureQuery(String whereClause) {
        return jdbcClient.sql("""
                SELECT id, competition_id, season_id, home_team_id, away_team_id,
                       kickoff_at, status, phase, created_at, updated_at
                FROM canonical_fixture
                """ + whereClause);
    }

    private boolean exists(String table, UUID id) {
        long count = jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
        return count == 1;
    }

    private CanonicalCompetition mapCompetition(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CanonicalCompetition(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("canonical_name"),
                resultSet.getString("country_code"),
                CompetitionType.valueOf(resultSet.getString("competition_type")),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private CanonicalSeason mapSeason(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CanonicalSeason(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("competition_id", UUID.class),
                resultSet.getString("season_label"),
                resultSet.getObject("starts_on", java.time.LocalDate.class),
                resultSet.getObject("ends_on", java.time.LocalDate.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private CanonicalTeam mapTeam(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CanonicalTeam(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("canonical_name"),
                resultSet.getString("country_code"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private CanonicalFixture mapFixture(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CanonicalFixture(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("competition_id", UUID.class),
                resultSet.getObject("season_id", UUID.class),
                resultSet.getObject("home_team_id", UUID.class),
                resultSet.getObject("away_team_id", UUID.class),
                instant(resultSet, "kickoff_at"),
                FixtureStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("phase"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private OffsetDateTime utc(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
