package com.bettingproject.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record NormalizationAnomalyKey(
        UUID rawSnapshotId,
        String provider,
        ProviderEntityType entityType,
        String providerEntityId,
        String season,
        String phase,
        NormalizationAnomalyCode code) {

    public NormalizationAnomalyKey {
        rawSnapshotId = Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
        provider = requireText(provider, "provider");
        entityType = Objects.requireNonNull(entityType, "entityType");
        providerEntityId = requireText(providerEntityId, "providerEntityId");
        season = normalizeOptionalContext(season);
        phase = normalizeOptionalContext(phase);
        code = Objects.requireNonNull(code, "code");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalContext(String value) {
        return value == null ? null : value.trim();
    }
}
