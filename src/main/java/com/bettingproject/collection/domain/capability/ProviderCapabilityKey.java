package com.bettingproject.collection.domain.capability;

import java.util.Objects;

public record ProviderCapabilityKey(
        String provider, String providerCompetitionId, String sourceSeason, String sourcePhase,
        CapabilityDataType dataType) {
    public ProviderCapabilityKey {
        provider = CapabilityText.exact(provider, "provider", 64);
        providerCompetitionId = CapabilityText.exact(providerCompetitionId, "providerCompetitionId", 200);
        sourceSeason = CapabilityText.exact(sourceSeason, "sourceSeason", 64);
        sourcePhase = CapabilityText.exact(sourcePhase, "sourcePhase", 64);
        dataType = Objects.requireNonNull(dataType, "dataType");
    }
}
