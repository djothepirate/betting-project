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
        Instant updatedAt,
        long version) {

    public ProviderMapping {
        id = Objects.requireNonNull(id, "id");
        ProviderMappingKey key = new ProviderMappingKey(
                provider, entityType, providerEntityId, season, phase);
        provider = key.provider();
        entityType = key.entityType();
        providerEntityId = key.providerEntityId();
        season = key.season();
        phase = key.phase();
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
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1");
        }
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
                season, phase, 1.0, MappingStatus.CONFIRMED, now, now, 1);
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
                season, phase, confidence, MappingStatus.AMBIGUOUS, now, now, 1);
    }

    public static ProviderMapping rejected(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            Instant now) {
        return new ProviderMapping(
                UUID.randomUUID(), provider, entityType, providerEntityId, null,
                season, phase, null, MappingStatus.REJECTED, now, now, 1);
    }

    public ProviderMappingKey key() {
        return new ProviderMappingKey(provider, entityType, providerEntityId, season, phase);
    }

    public ProviderMapping confirm(UUID confirmedCanonicalEntityId, Instant now) {
        return new ProviderMapping(
                id,
                provider,
                entityType,
                providerEntityId,
                Objects.requireNonNull(confirmedCanonicalEntityId, "confirmedCanonicalEntityId"),
                season,
                phase,
                1.0,
                MappingStatus.CONFIRMED,
                createdAt,
                Objects.requireNonNull(now, "now"),
                version + 1);
    }

    public ProviderMapping reject(Instant now) {
        return new ProviderMapping(
                id,
                provider,
                entityType,
                providerEntityId,
                null,
                season,
                phase,
                null,
                MappingStatus.REJECTED,
                createdAt,
                Objects.requireNonNull(now, "now"),
                version + 1);
    }
}
