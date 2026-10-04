package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentFamily;

/** Persistence port for collection attempts and their immutable derivation history. */
public interface EnrichmentCollectionStore {
    Optional<EnrichmentCollectionContext> findContext(UUID admissionId,
            com.bettingproject.enrichment.domain.EnrichmentPlanStepCode stepCode);

    List<String> confirmedFixtureMappings(UUID canonicalFixtureId, ProviderCapabilityKey capability);

    boolean budgetWindowMatchesProvider(UUID budgetWindowId, String provider);

    StoredEnrichmentAttempt createAndResolve(EnrichmentCollectionAttempt candidate);

    Optional<EnrichmentCollectionAttempt> findAttempt(UUID attemptId);

    Optional<EnrichmentCollectionAttempt> findAttemptByIntent(UUID budgetIntentId);

    void markCommitted(UUID attemptId, Instant authorizedAt);

    void markReleased(UUID attemptId, Instant releasedAt, String reasonCode);

    void markUncertain(UUID attemptId, Instant detectedAt, String reasonCode);

    UUID recordResponse(UUID attemptId, RawSnapshot rawSnapshot, EnrichmentProviderResponse response,
            String outcomeCode);

    void appendDerivation(EnrichmentDerivationRecord derivation);
}
