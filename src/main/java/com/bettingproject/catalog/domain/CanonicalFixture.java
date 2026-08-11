package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CanonicalFixture(
        UUID id,
        UUID competitionId,
        UUID seasonId,
        UUID homeTeamId,
        UUID awayTeamId,
        Instant kickoff,
        FixtureStatus status,
        String phase,
        Instant createdAt,
        Instant updatedAt) {

    public CanonicalFixture {
        id = Objects.requireNonNull(id, "id");
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
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public CanonicalFixture revise(Instant revisedKickoff, FixtureStatus revisedStatus, String revisedPhase, Instant revisedAt) {
        return new CanonicalFixture(
                id,
                competitionId,
                seasonId,
                homeTeamId,
                awayTeamId,
                revisedKickoff,
                revisedStatus,
                revisedPhase,
                createdAt,
                revisedAt);
    }
}
