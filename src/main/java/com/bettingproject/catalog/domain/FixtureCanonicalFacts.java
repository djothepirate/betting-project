package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical fixture facts whose provenance time governs whether they may replace the current state.
 */
public record FixtureCanonicalFacts(
        UUID competitionId,
        UUID seasonId,
        UUID homeTeamId,
        UUID awayTeamId,
        Boolean neutralVenue,
        boolean participantsUnordered,
        Instant kickoff,
        FixtureStatus status,
        String phase) {

    public FixtureCanonicalFacts {
        competitionId = Objects.requireNonNull(competitionId, "competitionId");
        seasonId = Objects.requireNonNull(seasonId, "seasonId");
        homeTeamId = Objects.requireNonNull(homeTeamId, "homeTeamId");
        awayTeamId = Objects.requireNonNull(awayTeamId, "awayTeamId");
        if (homeTeamId.equals(awayTeamId)) {
            throw new IllegalArgumentException("home and away teams must be different");
        }
        kickoff = Objects.requireNonNull(kickoff, "kickoff");
        status = Objects.requireNonNull(status, "status");
        phase = phase == null ? "" : phase.trim();
    }

    public static FixtureCanonicalFacts from(CanonicalFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        return new FixtureCanonicalFacts(
                fixture.competitionId(),
                fixture.seasonId(),
                fixture.homeTeamId(),
                fixture.awayTeamId(),
                fixture.neutralVenue(),
                fixture.participantsUnordered(),
                fixture.kickoff(),
                fixture.status(),
                fixture.phase());
    }
}
