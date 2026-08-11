package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NormalizationAnomaly(
        UUID id,
        UUID rawSnapshotId,
        String provider,
        ProviderEntityType entityType,
        String providerEntityId,
        NormalizationAnomalyCode code,
        String details,
        AnomalyStatus status,
        Instant createdAt,
        Instant resolvedAt) {

    public NormalizationAnomaly {
        id = Objects.requireNonNull(id, "id");
        rawSnapshotId = Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
        provider = requireText(provider, "provider");
        entityType = Objects.requireNonNull(entityType, "entityType");
        providerEntityId = requireText(providerEntityId, "providerEntityId");
        code = Objects.requireNonNull(code, "code");
        details = requireText(details, "details");
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static NormalizationAnomaly open(
            UUID rawSnapshotId,
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            NormalizationAnomalyCode code,
            String details,
            Instant now) {
        return new NormalizationAnomaly(
                UUID.randomUUID(), rawSnapshotId, provider, entityType, providerEntityId,
                code, details, AnomalyStatus.OPEN, now, null);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public enum AnomalyStatus {
        OPEN,
        RESOLVED,
        IGNORED
    }
}
