package com.bettingproject.catalog.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;

public final class ConfiguredCalendarAuthorityPolicy implements CalendarAuthorityPolicy {

    private final String policyVersion;
    private final Map<CalendarAuthorityKey, CalendarAuthorityRole> assignments;

    public ConfiguredCalendarAuthorityPolicy(
            String policyVersion,
            List<CalendarAuthorityAssignment> assignments) {
        this.policyVersion = validatePolicyVersion(policyVersion);
        Objects.requireNonNull(assignments, "assignments");

        Map<CalendarAuthorityKey, CalendarAuthorityRole> indexedAssignments = new LinkedHashMap<>();
        for (CalendarAuthorityAssignment assignment : assignments) {
            Objects.requireNonNull(assignment, "assignments must not contain null");
            CalendarAuthorityRole previousRole = indexedAssignments.putIfAbsent(
                    assignment.key(),
                    assignment.role());
            if (previousRole != null) {
                if (previousRole == assignment.role()) {
                    throw new IllegalArgumentException(
                            "duplicate calendar authority assignment for key " + assignment.key());
                }
                throw new IllegalArgumentException(
                        "conflicting calendar authority roles for key " + assignment.key());
            }
        }
        this.assignments = Map.copyOf(indexedAssignments);
    }

    @Override
    public CalendarAuthorityResolution resolve(CalendarAuthorityKey key) {
        Objects.requireNonNull(key, "key");
        CalendarAuthorityRole role = assignments.getOrDefault(key, CalendarAuthorityRole.UNASSIGNED);
        return new CalendarAuthorityResolution(role, policyVersion);
    }

    private static String validatePolicyVersion(String policyVersion) {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        if (!policyVersion.equals(policyVersion.trim())) {
            throw new IllegalArgumentException("policyVersion must not have leading or trailing whitespace");
        }
        return policyVersion;
    }
}
