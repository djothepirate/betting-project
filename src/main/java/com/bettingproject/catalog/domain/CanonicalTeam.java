package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CanonicalTeam(
        UUID id,
        String name,
        String countryCode,
        Instant createdAt,
        Instant updatedAt) {

    public CanonicalTeam {
        id = Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.trim();
        if (countryCode == null || countryCode.isBlank() || countryCode.trim().length() != 3) {
            throw new IllegalArgumentException("countryCode must contain three characters");
        }
        countryCode = countryCode.trim().toUpperCase();
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
