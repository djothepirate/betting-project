package com.bettingproject.enrichment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DailyEnrichmentAdmission(UUID id, UUID canonicalFixtureId, int order,
        boolean priority, Instant kickoffAt, int estimatedCalls, List<EnrichmentPlanStep> steps) {
    public DailyEnrichmentAdmission {
        Objects.requireNonNull(id);
        Objects.requireNonNull(canonicalFixtureId);
        Objects.requireNonNull(kickoffAt);
        steps = List.copyOf(Objects.requireNonNull(steps));
        if (order < 1 || order > 7 || estimatedCalls < 1 || estimatedCalls > 80 || steps.isEmpty()
                || steps.stream().map(EnrichmentPlanStep::code).distinct().count() != steps.size()) {
            throw new IllegalArgumentException("Invalid daily enrichment admission");
        }
    }
}
