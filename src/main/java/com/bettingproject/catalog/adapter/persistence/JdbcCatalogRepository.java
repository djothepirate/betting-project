package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.CatalogRepository;
import com.bettingproject.catalog.application.StoredCanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalCompetition;
import com.bettingproject.catalog.domain.CanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalSeason;
import com.bettingproject.catalog.domain.CanonicalTeam;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.catalog.domain.FixtureAuthorityStamp;
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
    public CanonicalCompetition getOrCreateCompetition(CanonicalCompetition competition) {
        jdbcClient.sql("""
                INSERT INTO canonical_competition (
                    id, canonical_name, country_code, competition_type, created_at, updated_at
                ) VALUES (
                    :id, :name, :countryCode, :type, :createdAt, :updatedAt
                )
                ON CONFLICT (canonical_name, country_code) DO NOTHING
                """)
                .param("id", competition.id())
                .param("name", competition.name())
                .param("countryCode", competition.countryCode())
                .param("type", competition.type().name())
                .param("createdAt", utc(competition.createdAt()))
                .param("updatedAt", utc(competition.updatedAt()))
                .update();
        return findCompetition(competition.name(), competition.countryCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical competition could not be resolved after insert: " + competition.name()));
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
    public CanonicalSeason getOrCreateSeason(CanonicalSeason season) {
        jdbcClient.sql("""
                INSERT INTO canonical_season (
                    id, competition_id, season_label, starts_on, ends_on, created_at, updated_at
                ) VALUES (
                    :id, :competitionId, :label, :startsOn, :endsOn, :createdAt, :updatedAt
                )
                ON CONFLICT (competition_id, season_label) DO NOTHING
                """)
                .param("id", season.id())
                .param("competitionId", season.competitionId())
                .param("label", season.label())
                .param("startsOn", season.startsOn())
                .param("endsOn", season.endsOn())
                .param("createdAt", utc(season.createdAt()))
                .param("updatedAt", utc(season.updatedAt()))
                .update();
        return findSeason(season.competitionId(), season.label())
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical season could not be resolved after insert: " + season.label()));
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
    public CanonicalTeam getOrCreateTeam(CanonicalTeam team) {
        jdbcClient.sql("""
                INSERT INTO canonical_team (
                    id, canonical_name, country_code, created_at, updated_at
                ) VALUES (
                    :id, :name, :countryCode, :createdAt, :updatedAt
                )
                ON CONFLICT (canonical_name, country_code) DO NOTHING
                """)
                .param("id", team.id())
                .param("name", team.name())
                .param("countryCode", team.countryCode())
                .param("createdAt", utc(team.createdAt()))
                .param("updatedAt", utc(team.updatedAt()))
                .update();
        return findTeam(team.name(), team.countryCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical team could not be resolved after insert: " + team.name()));
    }

    @Override
    public Optional<CanonicalFixture> findFixture(UUID fixtureId) {
        return fixtureQuery("WHERE id = :fixtureId")
                .param("fixtureId", fixtureId)
                .query(this::mapFixture)
                .optional();
    }

    @Override
    public Optional<CanonicalFixture> findFixtureForUpdate(UUID fixtureId) {
        return fixtureQuery("WHERE id = :fixtureId FOR UPDATE")
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
    public List<CanonicalFixture> findFixtureIdentityCandidatesForUpdate(
            UUID competitionId,
            UUID seasonId,
            UUID sourceHomeTeamId,
            UUID sourceAwayTeamId,
            java.time.Instant kickoff) {
        return fixtureQuery("""
                WHERE competition_id = :competitionId
                  AND season_id = :seasonId
                  AND kickoff_at = :kickoff
                  AND (
                    (home_team_id = :sourceHomeTeamId AND away_team_id = :sourceAwayTeamId)
                    OR
                    (home_team_id = :sourceAwayTeamId AND away_team_id = :sourceHomeTeamId)
                  )
                ORDER BY
                  CASE
                    WHEN home_team_id = :sourceHomeTeamId AND away_team_id = :sourceAwayTeamId THEN 0
                    ELSE 1
                  END,
                  id
                FOR UPDATE
                """)
                .param("competitionId", competitionId)
                .param("seasonId", seasonId)
                .param("sourceHomeTeamId", sourceHomeTeamId)
                .param("sourceAwayTeamId", sourceAwayTeamId)
                .param("kickoff", utc(kickoff))
                .query(this::mapFixture)
                .list();
    }

    @Override
    public StoredCanonicalFixture insertOrResolveFixture(CanonicalFixture fixture) {
        int inserted = jdbcClient.sql("""
                INSERT INTO canonical_fixture (
                    id, competition_id, season_id, home_team_id, away_team_id,
                    neutral_venue, participants_unordered, kickoff_at, status, phase,
                    last_authority_observation_id, last_authority_observed_at,
                    last_authority_provider, last_authority_policy_version,
                    created_at, updated_at
                ) VALUES (
                    :id, :competitionId, :seasonId, :homeTeamId, :awayTeamId,
                    :neutralVenue, :participantsUnordered, :kickoff, :status, :phase,
                    :lastAuthorityObservationId, :lastAuthorityObservedAt,
                    :lastAuthorityProvider, :lastAuthorityPolicyVersion,
                    :createdAt, :updatedAt
                )
                ON CONFLICT ON CONSTRAINT uq_canonical_fixture DO NOTHING
                """)
                .param("id", fixture.id())
                .param("competitionId", fixture.competitionId())
                .param("seasonId", fixture.seasonId())
                .param("homeTeamId", fixture.homeTeamId())
                .param("awayTeamId", fixture.awayTeamId())
                .param("neutralVenue", fixture.neutralVenue())
                .param("participantsUnordered", fixture.participantsUnordered())
                .param("kickoff", utc(fixture.kickoff()))
                .param("status", fixture.status().name())
                .param("phase", fixture.phase())
                .param("lastAuthorityObservationId", authorityObservationId(fixture.lastAuthority()))
                .param("lastAuthorityObservedAt", authorityObservedAt(fixture.lastAuthority()))
                .param("lastAuthorityProvider", authorityProvider(fixture.lastAuthority()))
                .param("lastAuthorityPolicyVersion", authorityPolicyVersion(fixture.lastAuthority()))
                .param("createdAt", utc(fixture.createdAt()))
                .param("updatedAt", utc(fixture.updatedAt()))
                .update();
        if (inserted == 1) {
            return new StoredCanonicalFixture(fixture, true);
        }
        CanonicalFixture existing = fixtureQuery("""
                WHERE competition_id = :competitionId
                  AND season_id = :seasonId
                  AND home_team_id = :homeTeamId
                  AND away_team_id = :awayTeamId
                  AND kickoff_at = :kickoff
                FOR UPDATE
                """)
                .param("competitionId", fixture.competitionId())
                .param("seasonId", fixture.seasonId())
                .param("homeTeamId", fixture.homeTeamId())
                .param("awayTeamId", fixture.awayTeamId())
                .param("kickoff", utc(fixture.kickoff()))
                .query(this::mapFixture)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical fixture could not be resolved after insert conflict"));
        return new StoredCanonicalFixture(existing, false);
    }

    @Override
    public boolean updateFixtureIfAuthorityMatches(
            CanonicalFixture fixture,
            UUID expectedAuthorityObservationId) {
        int updated = jdbcClient.sql("""
                UPDATE canonical_fixture
                SET neutral_venue = :neutralVenue,
                    participants_unordered = :participantsUnordered,
                    kickoff_at = :kickoff,
                    status = :status,
                    phase = :phase,
                    last_authority_observation_id = :lastAuthorityObservationId,
                    last_authority_observed_at = :lastAuthorityObservedAt,
                    last_authority_provider = :lastAuthorityProvider,
                    last_authority_policy_version = :lastAuthorityPolicyVersion,
                    updated_at = :updatedAt
                WHERE id = :id
                  AND last_authority_observation_id IS NOT DISTINCT FROM :expectedAuthorityObservationId
                """)
                .param("id", fixture.id())
                .param("neutralVenue", fixture.neutralVenue())
                .param("participantsUnordered", fixture.participantsUnordered())
                .param("kickoff", utc(fixture.kickoff()))
                .param("status", fixture.status().name())
                .param("phase", fixture.phase())
                .param("lastAuthorityObservationId", authorityObservationId(fixture.lastAuthority()))
                .param("lastAuthorityObservedAt", authorityObservedAt(fixture.lastAuthority()))
                .param("lastAuthorityProvider", authorityProvider(fixture.lastAuthority()))
                .param("lastAuthorityPolicyVersion", authorityPolicyVersion(fixture.lastAuthority()))
                .param("updatedAt", utc(fixture.updatedAt()))
                .param("expectedAuthorityObservationId", expectedAuthorityObservationId)
                .update();
        return updated == 1;
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

    private JdbcClient.StatementSpec fixtureQuery(String whereClause) {
        return jdbcClient.sql("""
                SELECT id, competition_id, season_id, home_team_id, away_team_id,
                       neutral_venue, participants_unordered, kickoff_at, status, phase,
                       last_authority_observation_id, last_authority_observed_at,
                       last_authority_provider, last_authority_policy_version,
                       created_at, updated_at
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
                resultSet.getObject("neutral_venue", Boolean.class),
                resultSet.getBoolean("participants_unordered"),
                instant(resultSet, "kickoff_at"),
                FixtureStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("phase"),
                mapAuthorityStamp(resultSet),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private FixtureAuthorityStamp mapAuthorityStamp(ResultSet resultSet) throws SQLException {
        UUID observationId = resultSet.getObject("last_authority_observation_id", UUID.class);
        OffsetDateTime observedAt = resultSet.getObject("last_authority_observed_at", OffsetDateTime.class);
        String provider = resultSet.getString("last_authority_provider");
        String policyVersion = resultSet.getString("last_authority_policy_version");
        if (observationId == null && observedAt == null && provider == null && policyVersion == null) {
            return null;
        }
        if (observationId == null || observedAt == null || provider == null || policyVersion == null) {
            throw new IllegalStateException("Canonical fixture has an incomplete authority stamp");
        }
        return new FixtureAuthorityStamp(observationId, observedAt.toInstant(), provider, policyVersion);
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private OffsetDateTime utc(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private UUID authorityObservationId(FixtureAuthorityStamp authority) {
        return authority == null ? null : authority.observationId();
    }

    private OffsetDateTime authorityObservedAt(FixtureAuthorityStamp authority) {
        return authority == null ? null : utc(authority.observedAt());
    }

    private String authorityProvider(FixtureAuthorityStamp authority) {
        return authority == null ? null : authority.provider();
    }

    private String authorityPolicyVersion(FixtureAuthorityStamp authority) {
        return authority == null ? null : authority.policyVersion();
    }
}
