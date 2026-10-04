package com.bettingproject.collection.application.enrichment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bettingproject.enrichment.domain.EnrichmentFamily;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only projection; age is a measurement and is not a freshness verdict. */
@Service
@Profile("control-api")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class EnrichmentQualityQueryService {
    public static final int MAX_FIXTURES = 7;
    private static final int MAX_STEPS = MAX_FIXTURES * 6;
    private static final int MAX_OBSERVATIONS = MAX_FIXTURES * EnrichmentFamily.values().length;
    private static final int MAX_FINDING_GROUPS = MAX_FIXTURES * 7 * 5;
    private static final int MAX_ATTEMPTS = MAX_FIXTURES * 6 * EnrichmentFamily.values().length;

    public record ObservationAvailability(EnrichmentFamily family, String state, String provider,
            String providerFixtureId, String logicalCompetition, String logicalSeason, String logicalPhase,
            String sourceSeasonReference, String sourcePhaseReference, UUID observationId,
            UUID rawSnapshotId, String payloadSha256, String representationSha256, String parserVersion,
            Instant requestedAt, Instant receivedAt, Instant sourceObservedAt, Long ageSeconds,
            String lineupAssessmentStatus) { }

    public record FixtureQuality(UUID admissionId, UUID canonicalFixtureId, int admissionOrder,
            boolean priority, Instant kickoffAt, String competitionName, String season,
            String phase, String status, String homeTeam, String awayTeam,
            List<EnrichmentQualityQueryPort.PlannedStep> steps,
            List<ObservationAvailability> availability,
            List<EnrichmentQualityQueryPort.FindingCount> findingCounts,
            List<EnrichmentQualityQueryPort.LatestAttempt> latestAttempts) {
        public FixtureQuality {
            steps = List.copyOf(steps);
            availability = List.copyOf(availability);
            findingCounts = List.copyOf(findingCounts);
            latestAttempts = List.copyOf(latestAttempts);
        }
    }

    public record DailyQuality(LocalDate date, boolean planPresent, Instant generatedAt,
            UUID planId, UUID budgetWindowId, String registrySha256, Integer selectedFixtureCount,
            Instant evaluatedAt, Instant planCreatedAt, List<FixtureQuality> fixtures) {
        public DailyQuality { fixtures = List.copyOf(fixtures); }
    }

    private final EnrichmentQualityQueryPort data;
    private final Clock clock;

    public EnrichmentQualityQueryService(EnrichmentQualityQueryPort data, Clock clock) {
        this.data = Objects.requireNonNull(data);
        this.clock = Objects.requireNonNull(clock);
    }

    public DailyQuality daily(LocalDate date) {
        Objects.requireNonNull(date);
        Instant generatedAt = clock.instant();
        var plan = data.dailyPlan(date);
        if (plan.isEmpty()) {
            return new DailyQuality(date, false, generatedAt, null, null, null, null, null, null, List.of());
        }

        List<EnrichmentQualityQueryPort.AdmittedFixture> admissions = bounded(data.admittedFixtures(date), MAX_FIXTURES);
        var admissionIds = admissions.stream().map(EnrichmentQualityQueryPort.AdmittedFixture::admissionId).collect(Collectors.toSet());
        List<EnrichmentQualityQueryPort.PlannedStep> steps = bounded(data.planSteps(date), MAX_STEPS);
        List<EnrichmentQualityQueryPort.LatestObservation> observations = bounded(data.latestObservations(date), MAX_OBSERVATIONS);
        List<EnrichmentQualityQueryPort.FindingCount> findings = bounded(data.findingCounts(date), MAX_FINDING_GROUPS);
        List<EnrichmentQualityQueryPort.LatestAttempt> attempts = bounded(data.latestAttempts(date), MAX_ATTEMPTS);
        if (admissions.size() != plan.get().selectedFixtureCount()) {
            throw new IllegalStateException("Daily enrichment plan does not match its bounded admissions");
        }
        requireKnownAdmissions(admissionIds, steps.stream().map(EnrichmentQualityQueryPort.PlannedStep::admissionId).toList());
        requireKnownAdmissions(admissionIds, observations.stream().map(EnrichmentQualityQueryPort.LatestObservation::admissionId).toList());
        requireKnownAdmissions(admissionIds, findings.stream().map(EnrichmentQualityQueryPort.FindingCount::admissionId).toList());
        requireKnownAdmissions(admissionIds, attempts.stream().map(EnrichmentQualityQueryPort.LatestAttempt::admissionId).toList());
        requireUnique(observations.stream().map(item -> item.admissionId() + ":" + item.family()).toList());
        requireUnique(steps.stream().map(item -> item.admissionId() + ":" + item.code()).toList());

        Map<UUID, List<EnrichmentQualityQueryPort.PlannedStep>> stepsByAdmission = group(steps,
                EnrichmentQualityQueryPort.PlannedStep::admissionId);
        Map<UUID, List<EnrichmentQualityQueryPort.LatestObservation>> observationsByAdmission = group(observations,
                EnrichmentQualityQueryPort.LatestObservation::admissionId);
        Map<UUID, List<EnrichmentQualityQueryPort.FindingCount>> findingsByAdmission = group(findings,
                EnrichmentQualityQueryPort.FindingCount::admissionId);
        Map<UUID, List<EnrichmentQualityQueryPort.LatestAttempt>> attemptsByAdmission = group(attempts,
                EnrichmentQualityQueryPort.LatestAttempt::admissionId);

        List<FixtureQuality> fixtures = new ArrayList<>(admissions.size());
        for (var admission : admissions) {
            List<EnrichmentQualityQueryPort.LatestObservation> found = observationsByAdmission
                    .getOrDefault(admission.admissionId(), List.of());
            EnumMap<EnrichmentFamily, EnrichmentQualityQueryPort.LatestObservation> byFamily = new EnumMap<>(EnrichmentFamily.class);
            found.forEach(item -> byFamily.put(item.family(), item));
            List<ObservationAvailability> availability = new ArrayList<>(EnrichmentFamily.values().length);
            for (EnrichmentFamily family : EnrichmentFamily.values()) {
                var item = byFamily.get(family);
                availability.add(item == null ? notCollected(family)
                        : observed(item, Duration.between(item.receivedAt(), generatedAt).getSeconds()));
            }
            fixtures.add(new FixtureQuality(admission.admissionId(), admission.canonicalFixtureId(),
                    admission.admissionOrder(), admission.priority(), admission.kickoffAt(), admission.competitionName(),
                    admission.season(), admission.phase(), admission.status(), admission.homeTeam(), admission.awayTeam(),
                    stepsByAdmission.getOrDefault(admission.admissionId(), List.of()), availability,
                    findingsByAdmission.getOrDefault(admission.admissionId(), List.of()),
                    attemptsByAdmission.getOrDefault(admission.admissionId(), List.of())));
        }
        return new DailyQuality(date, true, generatedAt, plan.get().id(), plan.get().budgetWindowId(),
                plan.get().registrySha256(), plan.get().selectedFixtureCount(), plan.get().evaluatedAt(),
                plan.get().createdAt(), fixtures);
    }

    private static ObservationAvailability notCollected(EnrichmentFamily family) {
        return new ObservationAvailability(family, "NOT_COLLECTED", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static ObservationAvailability observed(EnrichmentQualityQueryPort.LatestObservation item, long ageSeconds) {
        return new ObservationAvailability(item.family(), item.state(), item.provider(), item.providerFixtureId(),
                item.logicalCompetition(), item.logicalSeason(), item.logicalPhase(), item.sourceSeasonReference(),
                item.sourcePhaseReference(), item.id(), item.rawSnapshotId(), item.payloadSha256(),
                item.representationSha256(), item.parserVersion(), item.requestedAt(), item.receivedAt(),
                item.sourceObservedAt(), ageSeconds, item.lineupAssessmentStatus());
    }

    private static <T> List<T> bounded(List<T> rows, int maximum) {
        List<T> safe = List.copyOf(Objects.requireNonNull(rows));
        if (safe.size() > maximum) { throw new IllegalStateException("Daily enrichment read exceeded its fixed bound"); }
        return safe;
    }

    private static void requireKnownAdmissions(java.util.Set<UUID> known, List<UUID> referenced) {
        if (!known.containsAll(referenced)) { throw new IllegalStateException("Daily enrichment read escaped its date scope"); }
    }

    private static void requireUnique(List<String> keys) {
        if (keys.stream().distinct().count() != keys.size()) { throw new IllegalStateException("Daily enrichment read contains duplicates"); }
    }

    private static <T> Map<UUID, List<T>> group(List<T> rows, java.util.function.Function<T, UUID> key) {
        Map<UUID, List<T>> values = new LinkedHashMap<>();
        rows.forEach(row -> values.computeIfAbsent(key.apply(row), ignored -> new ArrayList<>()).add(row));
        values.replaceAll((ignored, items) -> List.copyOf(items));
        return values;
    }
}
