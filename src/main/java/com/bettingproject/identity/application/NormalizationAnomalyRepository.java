package com.bettingproject.identity.application;

import java.util.List;
import java.util.UUID;

import com.bettingproject.identity.domain.NormalizationAnomaly;

public interface NormalizationAnomalyRepository {

    StoredNormalizationAnomaly insertOrResolveForUpdate(NormalizationAnomaly candidate);

    boolean updateIfVersion(NormalizationAnomaly anomaly, long expectedVersion);

    List<NormalizationAnomaly> findOpenBySnapshotForUpdate(UUID rawSnapshotId);
}
