package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.enrichment.domain.EnrichmentFamily;

/** Bounded, safe read port for the internal daily enrichment view. */
public interface EnrichmentQualityQueryPort {
    record DailyPlan(UUID id, LocalDate date, UUID budgetWindowId, String registrySha256,
            int selectedFixtureCount, Instant evaluatedAt, Instant createdAt) { }

    record AdmittedFixture(UUID admissionId, UUID canonicalFixtureId, int admissionOrder,
            boolean priority, Instant kickoffAt, String competitionName, String season,
            String phase, String status, String homeTeam, String awayTeam) { }

    record PlannedStep(UUID id, UUID admissionId, String code, String familyScope,
            String conditionCode, String status, String resultCode, Instant scheduledAt,
            Instant triggerObservedAt, UUID triggerEvidenceObservationId,
            String triggerPolicyVersion) { }

    /** The raw and derived JSON bodies are deliberately not part of this projection. */
    record LatestObservation(UUID id, UUID admissionId, UUID canonicalFixtureId,
            EnrichmentFamily family, String state, String provider, String providerFixtureId,
            String logicalCompetition, String logicalSeason, String logicalPhase,
            String sourceSeasonReference, String sourcePhaseReference, String payloadSha256,
            String representationSha256, String parserVersion, UUID rawSnapshotId,
            Instant requestedAt, Instant receivedAt, Instant sourceObservedAt,
            String lineupAssessmentStatus) { }

    record FindingCount(UUID admissionId, String issueCode, String entityScope,
            long count, Instant lastDetectedAt) { }

    /** Latest attempt per admission, planned step, and family; no request body or credential data. */
    record LatestAttempt(UUID id, UUID admissionId, String stepCode, EnrichmentFamily family,
            String provider, String logicalEndpoint, String state, String reasonCode,
            Integer httpStatus, String connectorVersion, String parserVersion,
            UUID rawSnapshotId, String payloadSha256, Long quotaRemaining,
            Instant requestedAt, Instant receivedAt, Instant resultRecordedAt) { }

    Optional<DailyPlan> dailyPlan(LocalDate date);
    List<AdmittedFixture> admittedFixtures(LocalDate date);
    List<PlannedStep> planSteps(LocalDate date);
    List<LatestObservation> latestObservations(LocalDate date);
    List<FindingCount> findingCounts(LocalDate date);
    List<LatestAttempt> latestAttempts(LocalDate date);
}
