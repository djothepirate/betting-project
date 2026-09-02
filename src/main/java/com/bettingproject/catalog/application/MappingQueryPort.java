package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMappingKey;

public interface MappingQueryPort {

    List<MappingView> fetchMappings(
            MappingQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int fetchLimit);

    Optional<MappingView> findMapping(UUID mappingId);

    List<MappingDecisionView> fetchDecisions(
            MappingDecisionQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int fetchLimit);

    Optional<MappingDecisionView> findDecision(UUID decisionId);

    List<DecisionAnomalyView> fetchCorrelatedAnomalies(
            UUID decisionId,
            CorrelationKeysetAnchor anchor,
            int fetchLimit);

    record MappingQueryFilter(
            MappingStatus status,
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            UUID canonicalEntityId) {

        public MappingQueryFilter {
            provider = normalizeOptional(provider);
            providerEntityId = normalizeOptional(providerEntityId);
            season = preserveEmptyFilter(season);
            phase = preserveEmptyFilter(phase);
        }
    }

    record MappingDecisionQueryFilter(UUID mappingId) {
    }

    record MappingView(
            UUID id,
            ProviderMappingKey key,
            UUID canonicalEntityId,
            Double confidence,
            MappingStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    record MappingDecisionView(
            UUID id,
            UUID providerMappingId,
            ProviderMappingKey mappingKey,
            MappingDecisionType decisionType,
            long expectedVersion,
            long resultingVersion,
            MappingStatus previousStatus,
            UUID previousCanonicalEntityId,
            Double previousConfidence,
            MappingStatus resultingStatus,
            UUID resultingCanonicalEntityId,
            Double resultingConfidence,
            String operatorId,
            String justification,
            Instant createdAt) {
    }

    record DecisionAnomalyView(
            AnomalyQueryPort.AnomalyView anomaly,
            Instant correlatedAt) {
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String preserveEmptyFilter(String value) {
        return value == null ? null : value.trim();
    }
}
