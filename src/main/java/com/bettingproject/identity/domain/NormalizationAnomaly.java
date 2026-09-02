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
        String season,
        String phase,
        NormalizationAnomalyCode code,
        String details,
        AnomalyStatus status,
        long version,
        Instant createdAt,
        Instant lastSeenAt,
        Instant updatedAt,
        Instant resolvedAt,
        long occurrenceCount) {

    public NormalizationAnomaly {
        id = Objects.requireNonNull(id, "id");
        rawSnapshotId = Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
        provider = requireText(provider, "provider");
        entityType = Objects.requireNonNull(entityType, "entityType");
        providerEntityId = requireText(providerEntityId, "providerEntityId");
        season = normalizeOptionalContext(season);
        phase = normalizeOptionalContext(phase);
        code = Objects.requireNonNull(code, "code");
        details = requireText(details, "details");
        status = Objects.requireNonNull(status, "status");
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (lastSeenAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastSeenAt must not be before createdAt");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (occurrenceCount < 1) {
            throw new IllegalArgumentException("occurrenceCount must be at least 1");
        }
    }

    public static NormalizationAnomaly open(NormalizationAnomalyKey key, String details, Instant now) {
        Objects.requireNonNull(key, "key");
        return new NormalizationAnomaly(
                UUID.randomUUID(), key.rawSnapshotId(), key.provider(), key.entityType(),
                key.providerEntityId(), key.season(), key.phase(), key.code(), details,
                AnomalyStatus.OPEN, 1, now, now, now, null, 1);
    }

    public NormalizationAnomaly observe(String latestDetails, Instant now) {
        Objects.requireNonNull(now, "now");
        AnomalyStatus resultingStatus = status == AnomalyStatus.RESOLVED
                ? AnomalyStatus.OPEN
                : status;
        return new NormalizationAnomaly(
                id, rawSnapshotId, provider, entityType, providerEntityId, season, phase,
                code, latestDetails, resultingStatus, version + 1, createdAt, now, now,
                resultingStatus == AnomalyStatus.OPEN ? null : resolvedAt,
                occurrenceCount + 1);
    }

    public NormalizationAnomaly resolve(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != AnomalyStatus.OPEN) {
            throw new IllegalStateException("only an open anomaly can be resolved");
        }
        return new NormalizationAnomaly(
                id, rawSnapshotId, provider, entityType, providerEntityId, season, phase,
                code, details, AnomalyStatus.RESOLVED, version + 1, createdAt, lastSeenAt,
                now, now, occurrenceCount);
    }

    public NormalizationAnomalyKey key() {
        return new NormalizationAnomalyKey(
                rawSnapshotId, provider, entityType, providerEntityId, season, phase, code);
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

    public enum AnomalyStatus {
        OPEN,
        RESOLVED,
        IGNORED
    }
}
