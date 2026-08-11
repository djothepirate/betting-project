package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CanonicalCompetition(
        UUID id,
        String name,
        String countryCode,
        CompetitionType type,
        Instant createdAt,
        Instant updatedAt) {

    public CanonicalCompetition {
        id = Objects.requireNonNull(id, "id");
        name = requireText(name, "name");
        countryCode = requireCountryCode(countryCode);
        type = Objects.requireNonNull(type, "type");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String requireCountryCode(String value) {
        String normalized = requireText(value, "countryCode").toUpperCase();
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("countryCode must contain three characters");
        }
        return normalized;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
