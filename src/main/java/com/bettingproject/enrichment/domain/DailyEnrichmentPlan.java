package com.bettingproject.enrichment.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable admission decision. It records a plan, never permission to send a provider call. */
public record DailyEnrichmentPlan(UUID id, String idempotencyKey, String commandSha256,
        UUID budgetWindowId, LocalDate competitionDate, String registrySha256,
        int estimatedCallsPerFixture, Instant evaluatedAt, Instant createdAt,
        List<DailyEnrichmentAdmission> admissions) {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    public DailyEnrichmentPlan {
        Objects.requireNonNull(id);
        if (idempotencyKey == null || idempotencyKey.isEmpty() || idempotencyKey.length() > 128
                || !idempotencyKey.chars().allMatch(c -> c >= 33 && c <= 126)) {
            throw new IllegalArgumentException("Invalid idempotency key");
        }
        if (commandSha256 == null || !HASH.matcher(commandSha256).matches()
                || registrySha256 == null || !HASH.matcher(registrySha256).matches()) {
            throw new IllegalArgumentException("Invalid plan fingerprint");
        }
        Objects.requireNonNull(budgetWindowId);
        Objects.requireNonNull(competitionDate);
        Objects.requireNonNull(evaluatedAt);
        Objects.requireNonNull(createdAt);
        admissions = List.copyOf(Objects.requireNonNull(admissions));
        if (estimatedCallsPerFixture < 1 || estimatedCallsPerFixture > 80
                || admissions.isEmpty() || admissions.size() > 7 || createdAt.isBefore(evaluatedAt)
                || admissions.stream().map(DailyEnrichmentAdmission::canonicalFixtureId).distinct().count() != admissions.size()) {
            throw new IllegalArgumentException("Invalid daily plan");
        }
        for (int index = 0; index < admissions.size(); index++) {
            DailyEnrichmentAdmission admission = admissions.get(index);
            if (admission.order() != index + 1 || admission.estimatedCalls() != estimatedCallsPerFixture) {
                throw new IllegalArgumentException("Invalid admission order or estimated cost");
            }
        }
    }
    public int estimatedCalls() { return admissions.size() * estimatedCallsPerFixture; }
}
