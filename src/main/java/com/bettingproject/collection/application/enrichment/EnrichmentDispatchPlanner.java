package com.bettingproject.collection.application.enrichment;

import java.util.List;
import java.util.UUID;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;

/** Application port for atomically turning admitted or final-triggered steps into durable jobs. */
public interface EnrichmentDispatchPlanner {
    void planAdmission(UUID admissionId);
    void planArmedPostmatch(UUID admissionId, List<EnrichmentPlanStepCode> armedSteps);
}
