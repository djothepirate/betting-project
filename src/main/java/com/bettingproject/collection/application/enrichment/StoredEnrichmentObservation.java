package com.bettingproject.collection.application.enrichment;

import java.util.UUID;
public record StoredEnrichmentObservation(UUID id, boolean inserted, int findingsInserted) { }
