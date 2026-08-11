package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CanonicalSeason(
        UUID id,
        UUID competitionId,
        String label,
        LocalDate startsOn,
        LocalDate endsOn,
        Instant createdAt,
        Instant updatedAt) {

    public CanonicalSeason {
        id = Objects.requireNonNull(id, "id");
        competitionId = Objects.requireNonNull(competitionId, "competitionId");
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        label = label.trim();
        if (startsOn != null && endsOn != null && startsOn.isAfter(endsOn)) {
            throw new IllegalArgumentException("startsOn must not be after endsOn");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
