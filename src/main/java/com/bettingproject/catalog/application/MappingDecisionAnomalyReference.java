package com.bettingproject.catalog.application;

import java.util.Objects;
import java.util.UUID;

public record MappingDecisionAnomalyReference(
        UUID anomalyId,
        UUID rawSnapshotId) {

    public MappingDecisionAnomalyReference {
        anomalyId = Objects.requireNonNull(anomalyId, "anomalyId");
        rawSnapshotId = Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
    }
}
