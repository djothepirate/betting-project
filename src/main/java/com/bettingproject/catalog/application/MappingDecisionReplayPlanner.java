package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bettingproject.identity.domain.ProviderMappingDecision;

public interface MappingDecisionReplayPlanner {

    List<UUID> createRequests(
            ProviderMappingDecision decision,
            List<MappingDecisionAnomalyReference> correlatedAnomalies,
            Instant createdAt);

    List<UUID> findRequestIds(UUID decisionId);
}
