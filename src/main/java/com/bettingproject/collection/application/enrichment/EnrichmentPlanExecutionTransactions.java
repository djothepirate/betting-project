package com.bettingproject.collection.application.enrichment;

import java.util.UUID;
import java.time.Instant;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short row-locked state transitions for a planned collection step. */
@Service
@Profile({"control-api", "batch-worker"})
public class EnrichmentPlanExecutionTransactions {
    private final EnrichmentPlanExecutionStore steps;

    public EnrichmentPlanExecutionTransactions(EnrichmentPlanExecutionStore steps) { this.steps = steps; }

    @Transactional
    public boolean completeIfLineupAlreadyComplete(UUID admissionId, Instant kickoffAt) {
        steps.lockAdmission(admissionId);
        if (!steps.hasPrematchCompleteLineup(admissionId, kickoffAt)) { return false; }
        for (EnrichmentPlanStepCode code : new EnrichmentPlanStepCode[] {
                EnrichmentPlanStepCode.LINEUP_T_MINUS_30, EnrichmentPlanStepCode.LINEUP_T_MINUS_15 }) {
            steps.markStep(admissionId, code, EnrichmentPlanStepStatus.SKIPPED, "LINEUP_ALREADY_COMPLETE");
        }
        return true;
    }

    @Transactional
    public void mark(UUID admissionId, EnrichmentPlanStepCode code,
            EnrichmentPlanStepStatus status, String resultCode) {
        steps.lockAdmission(admissionId);
        var current = steps.stepStatus(admissionId, code).orElseThrow();
        if (current == status) { return; }
        if (current != EnrichmentPlanStepStatus.PLANNED && current != EnrichmentPlanStepStatus.QUEUED) { return; }
        if (!steps.markStep(admissionId, code, status, resultCode)) {
            throw new IllegalStateException("enrichment plan step changed concurrently");
        }
    }
}
