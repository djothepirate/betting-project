package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TimestampKeysetAnchor(Instant timestamp, UUID id) {

    public TimestampKeysetAnchor {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        id = Objects.requireNonNull(id, "id");
    }
}
