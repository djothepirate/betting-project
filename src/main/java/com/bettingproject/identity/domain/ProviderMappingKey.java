package com.bettingproject.identity.domain;

import java.util.Objects;

public record ProviderMappingKey(
        String provider,
        ProviderEntityType entityType,
        String providerEntityId,
        String season,
        String phase) {

    public ProviderMappingKey {
        provider = requireText(provider, "provider");
        entityType = Objects.requireNonNull(entityType, "entityType");
        if (entityType == ProviderEntityType.SNAPSHOT) {
            throw new IllegalArgumentException("SNAPSHOT is not a mappable entity type");
        }
        providerEntityId = requireText(providerEntityId, "providerEntityId");
        season = normalizeContext(season);
        phase = normalizeContext(phase);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeContext(String value) {
        return value == null ? "" : value.trim();
    }
}
