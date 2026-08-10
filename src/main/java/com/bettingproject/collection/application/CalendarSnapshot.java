package com.bettingproject.collection.application;

import java.util.List;

public record CalendarSnapshot(
        String schemaVersion,
        String provider,
        List<DiscoveredFixture> fixtures) {

    public CalendarSnapshot {
        fixtures = List.copyOf(fixtures);
    }
}
