package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.domain.CanonicalCompetition;
import com.bettingproject.catalog.domain.CanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalSeason;
import com.bettingproject.catalog.domain.CanonicalTeam;

public interface CatalogRepository {

    Optional<CanonicalCompetition> findCompetition(String name, String countryCode);

    CanonicalCompetition getOrCreateCompetition(CanonicalCompetition competition);

    Optional<CanonicalSeason> findSeason(UUID competitionId, String label);

    CanonicalSeason getOrCreateSeason(CanonicalSeason season);

    Optional<CanonicalTeam> findTeam(String name, String countryCode);

    CanonicalTeam getOrCreateTeam(CanonicalTeam team);

    Optional<CanonicalFixture> findFixture(UUID fixtureId);

    Optional<CanonicalFixture> findFixtureForUpdate(UUID fixtureId);

    Optional<CanonicalFixture> findFixture(
            UUID competitionId,
            UUID seasonId,
            UUID homeTeamId,
            UUID awayTeamId,
            Instant kickoff);

    List<CanonicalFixture> findFixtureIdentityCandidatesForUpdate(
            UUID competitionId,
            UUID seasonId,
            UUID sourceHomeTeamId,
            UUID sourceAwayTeamId,
            Instant kickoff);

    StoredCanonicalFixture insertOrResolveFixture(CanonicalFixture fixture);

    boolean updateFixtureIfAuthorityMatches(
            CanonicalFixture fixture,
            UUID expectedAuthorityObservationId);

    boolean existsCompetition(UUID id);

    boolean existsTeam(UUID id);

    boolean existsFixture(UUID id);

}
