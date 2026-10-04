package com.bettingproject.collection.application.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.application.enrichment.EnrichmentQualityQueryPort.*;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import org.junit.jupiter.api.Test;

class EnrichmentQualityQueryServiceTest {
    private static final LocalDate DATE = LocalDate.parse("2030-08-10");
    private static final Instant NOW = Instant.parse("2030-08-10T12:02:05Z");
    private static final UUID ADMISSION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FIXTURE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void returnsExplicitAbsenceInsteadOfInventingQualityForAnUnplannedDate() {
        var view = new EnrichmentQualityQueryService(new FakePort(), Clock.fixed(NOW, ZoneOffset.UTC)).daily(DATE);

        assertThat(view.date()).isEqualTo(DATE);
        assertThat(view.planPresent()).isFalse();
        assertThat(view.generatedAt()).isEqualTo(NOW);
        assertThat(view.fixtures()).isEmpty();
        assertThat(view.planId()).isNull();
    }

    @Test
    void measuresAgeAndKeepsUncollectedFamiliesDistinctWithoutClassifyingFreshness() {
        FakePort port = new FakePort();
        port.plan = Optional.of(new DailyPlan(UUID.randomUUID(), DATE, UUID.randomUUID(), "a".repeat(64), 1,
                NOW.minusSeconds(3600), NOW.minusSeconds(3590)));
        port.admissions = List.of(new AdmittedFixture(ADMISSION, FIXTURE, 1, true, NOW.plusSeconds(600),
                "Synthetic League", "2030/2031", "LEAGUE", "SCHEDULED", "Synthetic Home", "Synthetic Away"));
        port.observations = List.of(new LatestObservation(UUID.randomUUID(), ADMISSION, FIXTURE,
                EnrichmentFamily.LINEUP, "PARTIAL", "highlightly", "synthetic-fixture", "PPL", "2030/2031",
                "LEAGUE", "2030", "Round 1", "b".repeat(64), "c".repeat(64), "lineup-v1", UUID.randomUUID(),
                NOW.minusSeconds(125), NOW.minusSeconds(120), null, null));

        var fixture = new EnrichmentQualityQueryService(port, Clock.fixed(NOW, ZoneOffset.UTC)).daily(DATE)
                .fixtures().getFirst();

        assertThat(fixture.availability()).hasSize(EnrichmentFamily.values().length);
        assertThat(fixture.availability()).filteredOn(item -> item.family() == EnrichmentFamily.LINEUP)
                .singleElement().satisfies(item -> {
                    assertThat(item.state()).isEqualTo("PARTIAL");
                    assertThat(item.ageSeconds()).isEqualTo(120);
                    assertThat(item.lineupAssessmentStatus()).isNull();
                    assertThat(item.payloadSha256()).isEqualTo("b".repeat(64));
                });
        assertThat(fixture.availability()).filteredOn(item -> item.family() == EnrichmentFamily.EVENTS)
                .singleElement().satisfies(item -> {
                    assertThat(item.state()).isEqualTo("NOT_COLLECTED");
                    assertThat(item.observationId()).isNull();
                    assertThat(item.ageSeconds()).isNull();
                });
    }

    @Test
    void refusesAnOverBoundedProjectionOrRowsOutsideTheRequestedDateScope() {
        FakePort tooMany = new FakePort();
        tooMany.plan = Optional.of(new DailyPlan(UUID.randomUUID(), DATE, UUID.randomUUID(), "a".repeat(64), 8,
                NOW, NOW));
        tooMany.admissions = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new AdmittedFixture(UUID.randomUUID(), UUID.randomUUID(), index + 1,
                        false, NOW, "Synthetic", "2030", "LEAGUE", "SCHEDULED", "Home", "Away"))
                .toList();
        assertThatThrownBy(() -> new EnrichmentQualityQueryService(tooMany, Clock.fixed(NOW, ZoneOffset.UTC)).daily(DATE))
                .isInstanceOf(IllegalStateException.class);

        FakePort escaped = new FakePort();
        escaped.plan = Optional.of(new DailyPlan(UUID.randomUUID(), DATE, UUID.randomUUID(), "a".repeat(64), 1,
                NOW, NOW));
        escaped.admissions = List.of(new AdmittedFixture(ADMISSION, FIXTURE, 1, false, NOW, "Synthetic",
                "2030", "LEAGUE", "SCHEDULED", "Home", "Away"));
        escaped.steps = List.of(new PlannedStep(UUID.randomUUID(), UUID.randomUUID(), "DETAIL_AT_KICKOFF", "DETAIL",
                "SCHEDULED_KICKOFF_WINDOW", "PLANNED", null, NOW, null, null, null));
        assertThatThrownBy(() -> new EnrichmentQualityQueryService(escaped, Clock.fixed(NOW, ZoneOffset.UTC)).daily(DATE))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class FakePort implements EnrichmentQualityQueryPort {
        private Optional<DailyPlan> plan = Optional.empty();
        private List<AdmittedFixture> admissions = List.of();
        private List<PlannedStep> steps = List.of();
        private List<LatestObservation> observations = List.of();
        private List<FindingCount> findings = List.of();
        private List<LatestAttempt> attempts = List.of();

        @Override public Optional<DailyPlan> dailyPlan(LocalDate date) { return plan; }
        @Override public List<AdmittedFixture> admittedFixtures(LocalDate date) { return admissions; }
        @Override public List<PlannedStep> planSteps(LocalDate date) { return steps; }
        @Override public List<LatestObservation> latestObservations(LocalDate date) { return observations; }
        @Override public List<FindingCount> findingCounts(LocalDate date) { return findings; }
        @Override public List<LatestAttempt> latestAttempts(LocalDate date) { return attempts; }
    }
}
