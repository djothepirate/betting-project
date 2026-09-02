package com.bettingproject.catalog.application;

import java.util.Objects;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;

public record CalendarAuthorityAssignment(
        CalendarAuthorityKey key,
        CalendarAuthorityRole role) {

    public CalendarAuthorityAssignment {
        key = Objects.requireNonNull(key, "key");
        role = Objects.requireNonNull(role, "role");
        rejectConfigurationWildcards(key.provider(), "provider");
        rejectConfigurationWildcards(key.providerCompetitionId(), "providerCompetitionId");
        rejectConfigurationWildcards(key.season(), "season");
        rejectConfigurationWildcards(key.phase(), "phase");
    }

    private static void rejectConfigurationWildcards(String value, String name) {
        if (value.indexOf('*') >= 0 || value.indexOf('?') >= 0 || value.indexOf('%') >= 0) {
            throw new IllegalArgumentException(
                    "calendar authority assignments must not contain wildcards: " + name);
        }
    }
}
