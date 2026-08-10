package com.bettingproject.collection.application;

import java.util.Objects;

public record ReplayResult<T>(String snapshotSha256, T value) {

    public ReplayResult {
        if (snapshotSha256 == null || snapshotSha256.isBlank()) {
            throw new IllegalArgumentException("snapshotSha256 must not be blank");
        }
        Objects.requireNonNull(value, "value");
    }
}
