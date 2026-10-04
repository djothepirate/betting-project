package com.bettingproject.enrichment.domain;

import java.util.Objects;

/** A source-named metric; unknown names and their order remain unchanged. */
public record ProviderStatistic(String sourceName, ObservedScalar value) {
    public ProviderStatistic {
        sourceName = requireText(sourceName, "sourceName", 128);
        value = Objects.requireNonNull(value, "value");
    }

    private static String requireText(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
