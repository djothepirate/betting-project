package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface NormalizationReplayAnomalyEventStore {

    Set<UUID> findAnomalyEventIdsBySnapshotId(UUID snapshotId);

    void correlate(UUID attemptId, Collection<UUID> anomalyEventIds, Instant createdAt);
}
