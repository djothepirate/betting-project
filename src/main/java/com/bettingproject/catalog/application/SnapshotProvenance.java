package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SnapshotProvenance(
        UUID id,
        String provider,
        String endpoint,
        Instant requestedAt,
        Instant receivedAt,
        Instant sourceObservedAt,
        Integer httpStatus,
        Long latencyMs,
        Long quotaRemaining,
        String payloadSha256,
        String payloadCompression,
        String connectorVersion,
        Instant createdAt) {

    public SnapshotProvenance {
        id = Objects.requireNonNull(id, "id");
        provider = Objects.requireNonNull(provider, "provider");
        endpoint = Objects.requireNonNull(endpoint, "endpoint");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256");
        payloadCompression = Objects.requireNonNull(payloadCompression, "payloadCompression");
        connectorVersion = Objects.requireNonNull(connectorVersion, "connectorVersion");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
