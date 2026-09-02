package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import com.bettingproject.catalog.domain.FixtureApplicationOutcome;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyEventType;

public interface ReplayQueryPort {

    List<ReplayRequestView> fetchRequests(
            ReplayQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int fetchLimit);

    Optional<ReplayRequestView> findRequest(UUID requestId);

    List<ReplayAttemptView> fetchAttempts(
            UUID requestId,
            AttemptKeysetAnchor anchor,
            int fetchLimit);

    List<ReplayApplicationView> fetchApplications(
            UUID requestId,
            UUID attemptId,
            CorrelationKeysetAnchor anchor,
            int fetchLimit);

    List<ReplayAnomalyEventView> fetchAnomalyEvents(
            UUID requestId,
            UUID attemptId,
            CorrelationKeysetAnchor anchor,
            int fetchLimit);

    record ReplayQueryFilter(
            NormalizationReplayStatus status,
            NormalizationReplayOrigin origin,
            UUID rawSnapshotId,
            UUID providerMappingDecisionId) {
    }

    record ReplayRequestView(
            UUID id,
            UUID rawSnapshotId,
            String expectedPayloadSha256,
            UUID providerMappingDecisionId,
            NormalizationReplayOrigin origin,
            NormalizationReplaySelectorType selectorType,
            String selectorValue,
            NormalizationReplayStatus status,
            long version,
            int attemptCount,
            String lastErrorCode,
            String lastErrorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            SnapshotProvenance snapshot) {
    }

    record ReplayAttemptView(
            UUID id,
            UUID requestId,
            int attemptNumber,
            NormalizationReplayAttemptOutcome outcome,
            String expectedPayloadSha256,
            String actualPayloadSha256,
            Boolean compatible,
            Integer fixturesCreated,
            Integer fixturesUpdated,
            Integer fixturesUnchanged,
            Integer fixturesBlocked,
            Integer anomalies,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) {
    }

    record ReplayApplicationView(
            UUID id,
            UUID fixtureObservationId,
            UUID canonicalFixtureId,
            UUID previousAuthorityObservationId,
            FixtureApplicationOutcome outcome,
            String reasonCode,
            CalendarAuthorityRole authorityRole,
            String policyVersion,
            Instant evaluatedAt,
            Instant correlatedAt) {
    }

    record ReplayAnomalyEventView(
            UUID id,
            UUID anomalyId,
            NormalizationAnomalyEventType eventType,
            AnomalyStatus previousStatus,
            AnomalyStatus resultingStatus,
            UUID fixtureApplicationLogId,
            String details,
            Instant eventCreatedAt,
            Instant correlatedAt) {
    }
}
