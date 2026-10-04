package com.bettingproject.collection.application.enrichment;

import com.bettingproject.enrichment.domain.EnrichmentFamily;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bounded port for earlier structured observations; it never reads raw payload bytes. */
public interface EnrichmentQualityEvidenceReader {
    Optional<StoredEnrichmentQualityEvidence> latestForProviderFixture(UUID canonicalFixtureId,
            String logicalCompetition, String logicalSeason, String logicalPhase, String provider,
            String providerFixtureId, EnrichmentFamily family, Instant receivedAtOrBefore);

    List<StoredEnrichmentQualityEvidence> latestPerOtherProvider(UUID canonicalFixtureId,
            String logicalCompetition, String logicalSeason, String logicalPhase, String excludedProvider,
            EnrichmentFamily family, Instant receivedAtOrBefore);
}
