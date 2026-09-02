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
        Boolean neutralVenue,
        boolean participantsUnordered,
        Instant kickoff,
        FixtureStatus status,
        String phase,
        FixtureAuthorityStamp lastAuthority,
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

    public CanonicalFixture(
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
        this(
                id,
                competitionId,
                seasonId,
                homeTeamId,
                awayTeamId,
                null,
                false,
                kickoff,
                status,
                phase,
                null,
                createdAt,
                updatedAt);
    }

    public CanonicalFixture revise(Instant revisedKickoff, FixtureStatus revisedStatus, String revisedPhase, Instant revisedAt) {
        return revise(
                revisedKickoff,
                revisedStatus,
                revisedPhase,
                neutralVenue,
                participantsUnordered,
                revisedAt);
    }

    public CanonicalFixture revise(
            Instant revisedKickoff,
            FixtureStatus revisedStatus,
            String revisedPhase,
            Boolean revisedNeutralVenue,
            boolean revisedParticipantsUnordered,
            Instant revisedAt) {
        return new CanonicalFixture(
                id,
                competitionId,
                seasonId,
                homeTeamId,
                awayTeamId,
                revisedNeutralVenue,
                revisedParticipantsUnordered,
                revisedKickoff,
                revisedStatus,
                revisedPhase,
                lastAuthority,
                createdAt,
                revisedAt);
    }

    public CanonicalFixture apply(
            FixtureCanonicalFacts revisedFacts,
            FixtureAuthorityStamp revisedAuthority,
            Instant revisedAt) {
        Objects.requireNonNull(revisedFacts, "revisedFacts");
        Objects.requireNonNull(revisedAuthority, "revisedAuthority");
        requireSameIdentity(revisedFacts);
        return new CanonicalFixture(
                id,
                competitionId,
                seasonId,
                homeTeamId,
                awayTeamId,
                revisedFacts.neutralVenue(),
                revisedFacts.participantsUnordered(),
                revisedFacts.kickoff(),
                revisedFacts.status(),
                revisedFacts.phase(),
                revisedAuthority,
                createdAt,
                Objects.requireNonNull(revisedAt, "revisedAt"));
    }

    public CanonicalFixture advanceAuthority(
            FixtureAuthorityStamp revisedAuthority,
            Instant revisedAt) {
        return apply(FixtureCanonicalFacts.from(this), revisedAuthority, revisedAt);
    }

    private void requireSameIdentity(FixtureCanonicalFacts revisedFacts) {
        if (!competitionId.equals(revisedFacts.competitionId())
                || !seasonId.equals(revisedFacts.seasonId())
                || !homeTeamId.equals(revisedFacts.homeTeamId())
                || !awayTeamId.equals(revisedFacts.awayTeamId())) {
            throw new IllegalArgumentException("revised facts must preserve the canonical fixture identity");
        }
    }
}
