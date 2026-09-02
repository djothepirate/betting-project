package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.NormalizationAnomalyEventType;
import com.bettingproject.identity.domain.ProviderEntityType;

public interface AnomalyQueryPort {

    List<AnomalyView> fetchAnomalies(
            AnomalyQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int fetchLimit);

    Optional<AnomalyView> findAnomaly(UUID anomalyId);

    List<AnomalyEventView> fetchEvents(
            UUID anomalyId,
            TimestampKeysetAnchor anchor,
            int fetchLimit);

    record AnomalyQueryFilter(
            AnomalyStatus status,
            String provider,
            NormalizationAnomalyCode code,
            ProviderEntityType entityType,
            UUID rawSnapshotId,
            String providerEntityId) {

        public AnomalyQueryFilter {
            status = status == null ? AnomalyStatus.OPEN : status;
            provider = normalizeOptional(provider);
            providerEntityId = normalizeOptional(providerEntityId);
        }
    }

    record AnomalyView(
            UUID id,
            UUID rawSnapshotId,
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            NormalizationAnomalyCode code,
            String details,
            AnomalyStatus status,
            long version,
            Instant createdAt,
            Instant lastSeenAt,
            Instant updatedAt,
            Instant resolvedAt,
            long occurrenceCount,
            SnapshotProvenance snapshot) {
    }

    record AnomalyEventView(
            UUID id,
            UUID anomalyId,
            NormalizationAnomalyEventType eventType,
            AnomalyStatus previousStatus,
            AnomalyStatus resultingStatus,
            UUID fixtureApplicationLogId,
            String details,
            Instant createdAt) {
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
