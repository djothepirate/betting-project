package com.bettingproject.catalog.application;

import java.util.Objects;
import java.util.UUID;

public record AttemptKeysetAnchor(int attemptNumber, UUID id) {

    public AttemptKeysetAnchor {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be at least 1");
        }
        id = Objects.requireNonNull(id, "id");
    }
}
