package com.bettingproject.collection.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ProviderCallResult(
        String provider,
        String endpoint,
        Instant startedAt,
        Instant completedAt,
        int httpStatus,
        Map<String, String> responseHeaders,
        byte[] payload,
        String connectorVersion) {

    public ProviderCallResult {
        provider = requireText(provider, "provider");
        endpoint = requireSafeEndpoint(endpoint);
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not precede startedAt");
        }
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus must be a valid HTTP status");
        }
        responseHeaders = Map.copyOf(Objects.requireNonNull(responseHeaders, "responseHeaders"));
        payload = Objects.requireNonNull(payload, "payload").clone();
        connectorVersion = requireText(connectorVersion, "connectorVersion");
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    public long latencyMs() {
        return Duration.between(startedAt, completedAt).toMillis();
    }

    private static String requireSafeEndpoint(String value) {
        value = requireText(value, "endpoint");
        if (!value.startsWith("/") || value.contains("?") || value.contains("#")) {
            throw new IllegalArgumentException("endpoint must be a relative path without query parameters");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
