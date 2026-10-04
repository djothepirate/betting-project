package com.bettingproject.collection.application.enrichment;

import java.util.Objects;
import java.util.UUID;
import com.bettingproject.enrichment.domain.EnrichmentFamily;

/** Provider-safe request description; transport endpoints and credentials are adapter-owned. */
public record EnrichmentProviderRequest(UUID attemptId, String provider, String providerFixtureId,
        EnrichmentFamily family, String logicalEndpoint, String connectorVersion) {
    public EnrichmentProviderRequest {
        Objects.requireNonNull(attemptId);
        if (provider == null || provider.isBlank() || !provider.equals(provider.trim())) {
            throw new IllegalArgumentException("provider is invalid");
        }
        if (providerFixtureId == null || !providerFixtureId.matches("[0-9]+")) {
            throw new IllegalArgumentException("provider fixture reference is invalid");
        }
        Objects.requireNonNull(family);
        if (logicalEndpoint == null || !logicalEndpoint.matches("enrichment/[a-z-]{1,64}")) {
            throw new IllegalArgumentException("logical endpoint is invalid");
        }
        if (connectorVersion == null || connectorVersion.isBlank()) {
            throw new IllegalArgumentException("connector version is required");
        }
    }
}
