package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;

public record NormalizationAnomalyEvent(
        UUID id,
        UUID anomalyId,
        NormalizationAnomalyEventType eventType,
        AnomalyStatus previousStatus,
        AnomalyStatus resultingStatus,
        UUID fixtureApplicationLogId,
        String details,
        Instant createdAt) {

    public NormalizationAnomalyEvent {
        id = Objects.requireNonNull(id, "id");
        anomalyId = Objects.requireNonNull(anomalyId, "anomalyId");
        eventType = Objects.requireNonNull(eventType, "eventType");
        resultingStatus = Objects.requireNonNull(resultingStatus, "resultingStatus");
        details = requireText(details, "details");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        validateTransition(eventType, previousStatus, resultingStatus);
    }

    private static void validateTransition(
            NormalizationAnomalyEventType eventType,
            AnomalyStatus previousStatus,
            AnomalyStatus resultingStatus) {
        boolean valid = switch (eventType) {
            case OPENED -> previousStatus == null && resultingStatus == AnomalyStatus.OPEN;
            case OBSERVED -> (previousStatus == AnomalyStatus.OPEN
                    || previousStatus == AnomalyStatus.IGNORED)
                    && previousStatus == resultingStatus;
            case REOPENED -> previousStatus == AnomalyStatus.RESOLVED
                    && resultingStatus == AnomalyStatus.OPEN;
            case RESOLVED -> previousStatus == AnomalyStatus.OPEN
                    && resultingStatus == AnomalyStatus.RESOLVED;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "invalid anomaly event transition: " + eventType + " "
                            + previousStatus + " -> " + resultingStatus);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
