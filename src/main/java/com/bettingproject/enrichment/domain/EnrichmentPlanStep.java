package com.bettingproject.enrichment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EnrichmentPlanStep(UUID id, EnrichmentPlanStepCode code, String familyScope,
        Instant scheduledAt, String conditionCode, Instant triggerObservedAt, UUID triggerObservationId) {
    public EnrichmentPlanStep {
        Objects.requireNonNull(id);
        Objects.requireNonNull(code);
        Objects.requireNonNull(familyScope);
        Objects.requireNonNull(conditionCode);
        boolean finalDriven = code == EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL
                || code == EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60;
        boolean evidenceComplete = (triggerObservedAt == null) == (triggerObservationId == null);
        if (!evidenceComplete || finalDriven && (scheduledAt == null) != (triggerObservedAt == null)
                || !finalDriven && (scheduledAt == null || triggerObservedAt != null)) {
            throw new IllegalArgumentException("post-match steps require explicit final evidence, not a guessed timestamp");
        }
        if (finalDriven && scheduledAt != null) {
            Instant expected = code == EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL
                    ? triggerObservedAt : triggerObservedAt.plusSeconds(3600);
            if (!scheduledAt.equals(expected)) {
                throw new IllegalArgumentException("post-match schedule must derive from the explicit final observation");
            }
        }
    }

    public EnrichmentPlanStep(UUID id, EnrichmentPlanStepCode code, String familyScope,
            Instant scheduledAt, String conditionCode) {
        this(id, code, familyScope, scheduledAt, conditionCode, null, null);
    }
}
