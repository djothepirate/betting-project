package com.bettingproject.collection.application;

import java.time.Instant;

public record DiscoveredFixture(
        String providerFixtureId,
        Instant kickoff,
        String homeTeam,
        String awayTeam) {
}
