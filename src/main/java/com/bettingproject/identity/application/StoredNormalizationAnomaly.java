package com.bettingproject.identity.application;

import java.util.Objects;

import com.bettingproject.identity.domain.NormalizationAnomaly;

public record StoredNormalizationAnomaly(NormalizationAnomaly anomaly, boolean inserted) {

    public StoredNormalizationAnomaly {
        anomaly = Objects.requireNonNull(anomaly, "anomaly");
    }
}
