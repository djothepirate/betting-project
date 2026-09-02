package com.bettingproject.collection.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CalendarSnapshot(
        String schemaVersion,
        String provider,
        Instant observedAt,
        List<DiscoveredFixture> fixtures) {

    public CalendarSnapshot {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        schemaVersion = schemaVersion.trim();
        provider = provider.trim();
        if (CalendarSnapshotSchemas.isNormalizable(schemaVersion)) {
            observedAt = Objects.requireNonNull(
                    observedAt,
                    "observedAt must be present for canonical calendar schemas");
        }
        Objects.requireNonNull(fixtures, "fixtures");
        fixtures = List.copyOf(fixtures);
    }

    public CalendarSnapshot(String schemaVersion, String provider, List<DiscoveredFixture> fixtures) {
        this(schemaVersion, provider, null, fixtures);
    }
}
