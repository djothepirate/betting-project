package com.bettingproject.identity.application;

import com.bettingproject.identity.domain.NormalizationAnomaly;

public interface NormalizationAnomalyRepository {

    boolean save(NormalizationAnomaly anomaly);
}
