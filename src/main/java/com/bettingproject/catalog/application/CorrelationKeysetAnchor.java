package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CorrelationKeysetAnchor(Instant createdAt, UUID targetId) {

    public CorrelationKeysetAnchor {
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        targetId = Objects.requireNonNull(targetId, "targetId");
    }
}
