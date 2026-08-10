package com.bettingproject.collection.domain;

import java.time.Instant;
import java.util.Objects;

public record RawSnapshot(
        String provider,
        String endpoint,
        Instant receivedAt,
        byte[] payload,
        String sha256,
        String connectorVersion) {

    public RawSnapshot {
        provider = requireText(provider, "provider");
        endpoint = requireText(endpoint, "endpoint");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        payload = Objects.requireNonNull(payload, "payload").clone();
        sha256 = requireText(sha256, "sha256");
        connectorVersion = requireText(connectorVersion, "connectorVersion");
    }

    public static RawSnapshot capture(
            String provider,
            String endpoint,
            Instant receivedAt,
            byte[] payload,
            String connectorVersion) {
        return new RawSnapshot(
                provider,
                endpoint,
                receivedAt,
                payload,
                SnapshotHasher.sha256(payload),
                connectorVersion);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
