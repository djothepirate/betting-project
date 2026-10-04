package com.bettingproject.collection.application.enrichment;

import java.util.Objects;
import java.util.UUID;

import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;

/** One exact, budgeted request for one admitted fixture and one scheduled step. */
public record EnrichmentCollectionCommand(UUID admissionId, EnrichmentPlanStepCode stepCode,
        EnrichmentFamily family, ProviderCapabilityKey capability, UUID budgetWindowId) {
    public EnrichmentCollectionCommand {
        Objects.requireNonNull(admissionId, "admissionId");
        Objects.requireNonNull(stepCode, "stepCode");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(budgetWindowId, "budgetWindowId");
    }
}
