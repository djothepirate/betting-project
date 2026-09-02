package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FixtureApplicationLog(
        UUID id,
        UUID fixtureObservationId,
        UUID canonicalFixtureId,
        UUID previousAuthorityObservationId,
        FixtureApplicationOutcome outcome,
        String reasonCode,
        CalendarAuthorityRole authorityRole,
        String policyVersion,
        Instant evaluatedAt) {

    public FixtureApplicationLog {
        id = Objects.requireNonNull(id, "id");
        fixtureObservationId = Objects.requireNonNull(fixtureObservationId, "fixtureObservationId");
        outcome = Objects.requireNonNull(outcome, "outcome");
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        reasonCode = normalizeOptionalText(reasonCode);
        policyVersion = normalizeOptionalText(policyVersion);

        if ((authorityRole == null) != (policyVersion == null)) {
            throw new IllegalArgumentException("authorityRole and policyVersion must either both be present or both be absent");
        }
        if (outcome.canonicalFixtureRequired() && canonicalFixtureId == null) {
            throw new IllegalArgumentException("canonicalFixtureId is required for outcome " + outcome);
        }
        if (outcome.reasonCodeRequired() && reasonCode == null) {
            throw new IllegalArgumentException("reasonCode is required for outcome " + outcome);
        }
        if (!outcome.reasonCodeRequired() && reasonCode != null) {
            throw new IllegalArgumentException("reasonCode must be absent for outcome " + outcome);
        }
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
