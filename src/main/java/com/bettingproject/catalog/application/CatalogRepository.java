package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.domain.CanonicalCompetition;
import com.bettingproject.catalog.domain.CanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalSeason;
import com.bettingproject.catalog.domain.CanonicalTeam;
import com.bettingproject.catalog.domain.FixtureObservation;

public interface CatalogRepository {

    Optional<CanonicalCompetition> findCompetition(String name, String countryCode);

    void insertCompetition(CanonicalCompetition competition);

    Optional<CanonicalSeason> findSeason(UUID competitionId, String label);

    void insertSeason(CanonicalSeason season);

    Optional<CanonicalTeam> findTeam(String name, String countryCode);

    void insertTeam(CanonicalTeam team);

    Optional<CanonicalFixture> findFixture(UUID fixtureId);

    Optional<CanonicalFixture> findFixture(
            UUID competitionId,
            UUID seasonId,
            UUID homeTeamId,
            UUID awayTeamId,
            Instant kickoff);

    void insertFixture(CanonicalFixture fixture);

    void updateFixture(CanonicalFixture fixture);

    boolean existsCompetition(UUID id);

    boolean existsTeam(UUID id);

    boolean existsFixture(UUID id);

    boolean insertObservation(FixtureObservation observation);
}
