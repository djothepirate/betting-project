package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.bettingproject.collection.domain.RawSnapshot;

public record StoredRawSnapshot(
        UUID id,
        String provider,
        String endpoint,
        Instant receivedAt,
        String payloadSha256,
        String payloadCompression,
        byte[] payload,
        String connectorVersion) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public StoredRawSnapshot {
        id = Objects.requireNonNull(id, "id");
        provider = requireText(provider, "provider");
        endpoint = requireText(endpoint, "endpoint");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        if (payloadSha256 == null || !SHA_256.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be a lowercase SHA-256");
        }
        payloadCompression = requireText(payloadCompression, "payloadCompression");
        payload = Objects.requireNonNull(payload, "payload").clone();
        connectorVersion = requireText(connectorVersion, "connectorVersion");
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    public RawSnapshot asRawSnapshot() {
        return new RawSnapshot(
                provider,
                endpoint,
                receivedAt,
                payload,
                payloadSha256,
                connectorVersion);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
