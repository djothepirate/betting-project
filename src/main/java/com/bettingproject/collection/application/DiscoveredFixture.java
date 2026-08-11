package com.bettingproject.collection.application;

import java.time.Instant;
import java.util.Objects;

public record DiscoveredFixture(
        String providerFixtureId,
        DiscoveredCompetition competition,
        Instant kickoff,
        String status,
        DiscoveredTeam homeTeam,
        DiscoveredTeam awayTeam) {

    public DiscoveredFixture {
        if (providerFixtureId == null || providerFixtureId.isBlank()) {
            throw new IllegalArgumentException("providerFixtureId must not be blank");
        }
        providerFixtureId = providerFixtureId.trim();
        kickoff = Objects.requireNonNull(kickoff, "kickoff");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        status = status.trim().toUpperCase();
        homeTeam = Objects.requireNonNull(homeTeam, "homeTeam");
        awayTeam = Objects.requireNonNull(awayTeam, "awayTeam");
    }

    public DiscoveredFixture(String providerFixtureId, Instant kickoff, String homeTeam, String awayTeam) {
        this(
                providerFixtureId,
                null,
                kickoff,
                "SCHEDULED",
                new DiscoveredTeam(null, homeTeam, null),
                new DiscoveredTeam(null, awayTeam, null));
    }
}
