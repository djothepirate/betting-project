package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepStatus;

/** Conditional plan-step writes and state needed by the bounded worker. */
public interface EnrichmentPlanExecutionStore {
    void lockAdmission(UUID admissionId);

    boolean hasPrematchCompleteLineup(UUID admissionId, Instant kickoffAt);

    Optional<EnrichmentPlanStepStatus> stepStatus(UUID admissionId, EnrichmentPlanStepCode code);

    boolean markStep(UUID admissionId, EnrichmentPlanStepCode code,
            EnrichmentPlanStepStatus status, String resultCode);

    List<EnrichmentPlanStepCode> armPostmatch(UUID admissionId, UUID observationId,
            Instant finalObservedAt, String policyVersion);
}
