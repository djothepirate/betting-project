package com.bettingproject.collection.application.enrichment;

import com.bettingproject.enrichment.domain.EnrichmentFamily;

/** Exactly one transport attempt; implementations must not retry or follow redirects. */
public interface EnrichmentProviderClient {
    String provider();
    String connectorVersion();
    boolean available();
    boolean supports(EnrichmentFamily family);
    EnrichmentProviderResponse fetch(EnrichmentProviderRequest request);
}
