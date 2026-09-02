package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bettingproject.identity.domain.ProviderMappingKey;

public interface MappingDecisionAnomalyStore {

    List<MappingDecisionAnomalyReference> findOpenAnomalies(ProviderMappingKey mappingKey);

    default List<UUID> findOpenAnomalyIds(ProviderMappingKey mappingKey) {
        return findOpenAnomalies(mappingKey).stream()
                .map(MappingDecisionAnomalyReference::anomalyId)
                .toList();
    }

    void correlate(UUID decisionId, List<UUID> anomalyIds, Instant createdAt);

    List<UUID> findAnomalyIds(UUID decisionId);

    List<MappingDecisionAnomalyReference> findAnomalies(UUID decisionId);
}
