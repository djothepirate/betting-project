package com.bettingproject.catalog.application;

import java.util.Objects;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;

public record CalendarAuthorityResolution(
        CalendarAuthorityRole role,
        String policyVersion) {

    public CalendarAuthorityResolution {
        role = Objects.requireNonNull(role, "role");
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        if (!policyVersion.equals(policyVersion.trim())) {
            throw new IllegalArgumentException("policyVersion must not have leading or trailing whitespace");
        }
    }
}
