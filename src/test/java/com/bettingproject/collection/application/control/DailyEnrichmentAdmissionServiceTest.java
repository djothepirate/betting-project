package com.bettingproject.collection.application.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.bettingproject.collection.domain.budget.BudgetModel.Availability;
import com.bettingproject.collection.domain.selection.DailySelectionPolicy;
import com.bettingproject.enrichment.domain.DailyEnrichmentPlan;
import com.bettingproject.enrichment.domain.DailyEnrichmentAdmission;
import com.bettingproject.collection.application.enrichment.EnrichmentDispatchPlanner;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import org.junit.jupiter.api.Test;

class DailyEnrichmentAdmissionServiceTest {
    private static final Instant NOW = Instant.parse("2030-08-10T12:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2030-08-10");
    private static final UUID WINDOW = new UUID(0, 1);
    private static final String HASH = "a".repeat(64);
    private final MemoryStore store = new MemoryStore();
    private final RecordingDispatch dispatch = new RecordingDispatch();

    @Test void admissionIsDurableIdempotentAndContainsOnlyBoundedPlannedSteps() {
        var service = service(selection(List.of(candidate(2, false), candidate(3, true))));
        var command = command();
        var first = service.admit(command, "daily-plan-20300810");
        var duplicate = service.admit(command, "daily-plan-20300810");

        assertThat(first.code()).isEqualTo(DailyEnrichmentAdmissionResult.Code.CREATED);
        assertThat(first.plan().admissions()).hasSize(2);
        assertThat(first.plan().estimatedCalls()).isEqualTo(20);
        assertThat(duplicate.code()).isEqualTo(DailyEnrichmentAdmissionResult.Code.ALREADY_CREATED);
        assertThat(duplicate.plan().id()).isEqualTo(first.plan().id());
        assertThat(store.lockOrder).containsExactly("key:daily-plan-20300810", "date:2030-08-10",
                "key:daily-plan-20300810", "date:2030-08-10");
        assertThat(dispatch.admissions).containsExactlyElementsOf(first.plan().admissions().stream()
                .map(DailyEnrichmentAdmission::id).toList());

        var prioritySteps = first.plan().admissions().get(1).steps();
        assertThat(prioritySteps).extracting(step -> step.code().name()).containsExactlyInAnyOrder(
                "LINEUP_T_MINUS_30", "LINEUP_T_MINUS_15", "DETAIL_AT_KICKOFF", "DETAIL_PLUS_45",
                "POSTMATCH_AFTER_FINAL", "POSTMATCH_RECHECK_FINAL_PLUS_60");
        assertThat(prioritySteps.stream().filter(step -> step.code().name().startsWith("POSTMATCH"))
                .map(step -> step.scheduledAt()).toList()).containsOnly((Instant) null);
        assertThat(first.plan().admissions().getFirst().steps()).noneMatch(step ->
                step.code().name().equals("POSTMATCH_RECHECK_FINAL_PLUS_60"));
    }

    @Test void keyCollisionAndSecondPlanForTheSameUtcDateAreRefusedWithoutOverwrite() {
        var service = service(selection(List.of(candidate(2, false))));
        var command = command();
        var created = service.admit(command, "same-key");
        var contentConflict = service.admit(new DailySelectionCommand(WINDOW, DAY,
                List.of(new UUID(0, 22), new UUID(0, 3), new UUID(0, 4), new UUID(0, 5)), 10, Set.of()), "same-key");
        var dateConflict = service.admit(command, "another-key");
        assertThat(created.code()).isEqualTo(DailyEnrichmentAdmissionResult.Code.CREATED);
        assertThat(contentConflict.code()).isEqualTo(DailyEnrichmentAdmissionResult.Code.IDEMPOTENCY_CONFLICT);
        assertThat(dateConflict.code()).isEqualTo(DailyEnrichmentAdmissionResult.Code.DAY_ALREADY_PLANNED);
        assertThat(store.byDate).hasSize(1);
    }

    @Test void invalidKeyAndEmptyPreviewCreateNoPlan() {
        var service = service(selection(List.of()));
        assertThatThrownBy(() -> service.admit(command(), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.admit(command(), "empty").code()).isEqualTo(DailyEnrichmentAdmissionResult.Code.NO_ELIGIBLE_FIXTURES);
        assertThat(store.byDate).isEmpty();
    }

    private DailyEnrichmentAdmissionService service(DailySelectionService selection) {
        return new DailyEnrichmentAdmissionService(selection, store, dispatch, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DailySelectionService selection(List<DailySelectionPolicy.Candidate> selected) {
        var result = new DailySelectionPolicy.Selection(selected, List.of(), selected.size() * 10L);
        return new DailySelectionService(null, null, null, null, Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override public Preview preview(DailySelectionCommand command) {
                return new Preview(Code.OK, NOW, WINDOW, HASH,
                        new Availability(com.bettingproject.collection.domain.budget.BudgetModel.ResultCode.OK, 80, null),
                        10, 7, false, result);
            }
        };
    }

    private static DailySelectionPolicy.Candidate candidate(long id, boolean priority) {
        return new DailySelectionPolicy.Candidate(new UUID(0, id), "PPL", NOW.plusSeconds(3600 + id), "SCHEDULED");
    }

    private static DailySelectionCommand command() {
        return new DailySelectionCommand(WINDOW, DAY,
                List.of(new UUID(0, 2), new UUID(0, 3), new UUID(0, 4), new UUID(0, 5)), 10, Set.of(new UUID(0, 3)));
    }

    private static final class MemoryStore implements EnrichmentAdmissionStore {
        private final Map<String, DailyEnrichmentPlan> byKey = new HashMap<>();
        private final Map<LocalDate, DailyEnrichmentPlan> byDate = new HashMap<>();
        private final List<String> lockOrder = new ArrayList<>();
        public void lockIdempotencyKey(String key) { lockOrder.add("key:" + key); }
        public void lockDate(LocalDate date) { lockOrder.add("date:" + date); }
        public Optional<DailyEnrichmentPlan> findByIdempotencyKey(String key) { return Optional.ofNullable(byKey.get(key)); }
        public Optional<DailyEnrichmentPlan> findByDate(LocalDate date) { return Optional.ofNullable(byDate.get(date)); }
        public boolean insert(DailyEnrichmentPlan plan) {
            if (byDate.containsKey(plan.competitionDate()) || byKey.containsKey(plan.idempotencyKey())) { return false; }
            byDate.put(plan.competitionDate(), plan); byKey.put(plan.idempotencyKey(), plan); return true;
        }
    }

    private static final class RecordingDispatch implements EnrichmentDispatchPlanner {
        private final List<UUID> admissions = new ArrayList<>();
        @Override public void planAdmission(UUID admissionId) { admissions.add(admissionId); }
        @Override public void planArmedPostmatch(UUID admissionId, List<EnrichmentPlanStepCode> armedSteps) { }
    }
}
