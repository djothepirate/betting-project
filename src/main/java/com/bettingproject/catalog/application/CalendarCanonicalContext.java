package com.bettingproject.catalog.application;

/** Explicit canonical vocabulary, never a replacement for the provider's recorded context. */
public record CalendarCanonicalContext(String season, String phase) {
    public CalendarCanonicalContext {
        requireExact(season);
        requireExact(phase);
    }

    private static void requireExact(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("Canonical calendar context requires exact nonblank values");
        }
    }
}
