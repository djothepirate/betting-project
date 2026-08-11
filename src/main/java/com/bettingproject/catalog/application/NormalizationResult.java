package com.bettingproject.catalog.application;

import java.util.Objects;
import java.util.UUID;

public record NormalizationResult(
        UUID snapshotId,
        String snapshotSha256,
        boolean snapshotInserted,
        boolean compatible,
        int fixturesCreated,
        int fixturesUpdated,
        int fixturesUnchanged,
        int fixturesBlocked,
        int anomalies) {

    public NormalizationResult {
        snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        if (snapshotSha256 == null || snapshotSha256.isBlank()) {
            throw new IllegalArgumentException("snapshotSha256 must not be blank");
        }
        if (fixturesCreated < 0 || fixturesUpdated < 0 || fixturesUnchanged < 0
                || fixturesBlocked < 0 || anomalies < 0) {
            throw new IllegalArgumentException("normalization counters must not be negative");
        }
    }
}
