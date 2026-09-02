package com.bettingproject.catalog.application;

import java.util.Objects;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;

public record CalendarAuthorityAssignment(
        CalendarAuthorityKey key,
        CalendarAuthorityRole role) {

    public CalendarAuthorityAssignment {
        key = Objects.requireNonNull(key, "key");
        role = Objects.requireNonNull(role, "role");
    }
}
