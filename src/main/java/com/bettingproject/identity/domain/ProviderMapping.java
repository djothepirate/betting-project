package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProviderMapping(
        UUID id,
        String provider,
        ProviderEntityType entityType,
        String providerEntityId,
        UUID canonicalEntityId,
        String season,
        String phase,
        Double confidence,
        MappingStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public ProviderMapping {
        id = Objects.requireNonNull(id, "id");
        provider = requireText(provider, "provider");
        entityType = Objects.requireNonNull(entityType, "entityType");
        if (entityType == ProviderEntityType.SNAPSHOT) {
            throw new IllegalArgumentException("SNAPSHOT is not a mappable entity type");
        }
        providerEntityId = requireText(providerEntityId, "providerEntityId");
        season = season == null ? "" : season.trim();
        phase = phase == null ? "" : phase.trim();
        status = Objects.requireNonNull(status, "status");
        if (status == MappingStatus.CONFIRMED && canonicalEntityId == null) {
            throw new IllegalArgumentException("confirmed mapping requires a canonical entity");
        }
        if (status != MappingStatus.CONFIRMED && canonicalEntityId != null) {
            throw new IllegalArgumentException("unconfirmed mapping must not carry a canonical entity");
        }
        if (confidence != null && (confidence < 0 || confidence > 1)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ProviderMapping confirmed(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            UUID canonicalEntityId,
            String season,
            String phase,
            Instant now) {
        return new ProviderMapping(
                UUID.randomUUID(), provider, entityType, providerEntityId, canonicalEntityId,
                season, phase, 1.0, MappingStatus.CONFIRMED, now, now);
    }

    public static ProviderMapping ambiguous(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            Double confidence,
            Instant now) {
        return new ProviderMapping(
                UUID.randomUUID(), provider, entityType, providerEntityId, null,
                season, phase, confidence, MappingStatus.AMBIGUOUS, now, now);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
