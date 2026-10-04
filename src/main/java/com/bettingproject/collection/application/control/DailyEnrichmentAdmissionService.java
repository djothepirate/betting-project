package com.bettingproject.collection.application.control;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.bettingproject.enrichment.domain.*;
import com.bettingproject.collection.application.enrichment.EnrichmentDispatchPlanner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admission records a bounded plan; budget reservation and every provider send remain later steps. */
@Service
@Profile("control-api")
public class DailyEnrichmentAdmissionService {
    private final DailySelectionService selections;
    private final EnrichmentAdmissionStore store;
    private final EnrichmentDispatchPlanner dispatch;
    private final Clock clock;

    public DailyEnrichmentAdmissionService(DailySelectionService selections,
            EnrichmentAdmissionStore store, EnrichmentDispatchPlanner dispatch, Clock clock) {
        this.selections = Objects.requireNonNull(selections);
        this.store = Objects.requireNonNull(store);
        this.dispatch = Objects.requireNonNull(dispatch);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public DailyEnrichmentAdmissionResult admit(DailySelectionCommand command, String idempotencyKey) {
        Objects.requireNonNull(command);
        validateKey(idempotencyKey);
        String fingerprint = fingerprint(command);
        store.lockIdempotencyKey(idempotencyKey);
        store.lockDate(command.date());

        var priorKey = store.findByIdempotencyKey(idempotencyKey);
        if (priorKey.isPresent()) {
            DailyEnrichmentPlan prior = priorKey.get();
            return prior.commandSha256().equals(fingerprint)
                    ? new DailyEnrichmentAdmissionResult(DailyEnrichmentAdmissionResult.Code.ALREADY_CREATED, prior, null, false)
                    : new DailyEnrichmentAdmissionResult(DailyEnrichmentAdmissionResult.Code.IDEMPOTENCY_CONFLICT, null, null, false);
        }
        if (store.findByDate(command.date()).isPresent()) {
            return new DailyEnrichmentAdmissionResult(DailyEnrichmentAdmissionResult.Code.DAY_ALREADY_PLANNED, null, null, false);
        }

        DailySelectionService.Preview preview = selections.preview(command);
        if (preview.code() != DailySelectionService.Code.OK) {
            return new DailyEnrichmentAdmissionResult(DailyEnrichmentAdmissionResult.Code.PREVIEW_REFUSED, null, preview.code(), false);
        }
        List<com.bettingproject.collection.domain.selection.DailySelectionPolicy.Candidate> selected = preview.selection().selected();
        if (selected.isEmpty()) {
            return new DailyEnrichmentAdmissionResult(DailyEnrichmentAdmissionResult.Code.NO_ELIGIBLE_FIXTURES, null, preview.code(), false);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<DailyEnrichmentAdmission> admissions = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            var candidate = selected.get(index);
            boolean priority = command.priorityFixtureIds().contains(candidate.id());
            admissions.add(new DailyEnrichmentAdmission(UUID.randomUUID(), candidate.id(), index + 1,
                    priority, candidate.kickoffAt(), command.estimatedCallsPerMatch(), steps(candidate.kickoffAt(), priority)));
        }
        DailyEnrichmentPlan plan = new DailyEnrichmentPlan(UUID.randomUUID(), idempotencyKey, fingerprint,
                command.windowId(), command.date(), preview.registrySha256(), command.estimatedCallsPerMatch(),
                preview.evaluatedAt(), now, admissions);
        if (!store.insert(plan)) {
            return new DailyEnrichmentAdmissionResult(DailyEnrichmentAdmissionResult.Code.DAY_ALREADY_PLANNED,
                    store.findByDate(command.date()).orElse(null), null, false);
        }
        for (DailyEnrichmentAdmission admission : admissions) {
            dispatch.planAdmission(admission.id());
        }
        return new DailyEnrichmentAdmissionResult(DailyEnrichmentAdmissionResult.Code.CREATED, plan, preview.code(), true);
    }

    private static List<EnrichmentPlanStep> steps(Instant kickoff, boolean priority) {
        List<EnrichmentPlanStep> result = new ArrayList<>();
        result.add(step(EnrichmentPlanStepCode.LINEUP_T_MINUS_30, "LINEUP", kickoff.minus(30, ChronoUnit.MINUTES), "LINEUP_NOT_COMPLETE"));
        result.add(step(EnrichmentPlanStepCode.LINEUP_T_MINUS_15, "LINEUP", kickoff.minus(15, ChronoUnit.MINUTES), "LINEUP_NOT_COMPLETE"));
        result.add(step(EnrichmentPlanStepCode.DETAIL_AT_KICKOFF, "DETAIL", kickoff, "SCHEDULED_KICKOFF_WINDOW"));
        result.add(step(EnrichmentPlanStepCode.DETAIL_PLUS_45, "DETAIL", kickoff.plus(45, ChronoUnit.MINUTES), "SCHEDULED_KICKOFF_WINDOW"));
        result.add(step(EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL, "POSTMATCH", null, "EXPLICIT_FINAL_STATUS"));
        if (priority) {
            result.add(step(EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60, "POSTMATCH_RECHECK", null,
                    "PRIORITY_AND_FINAL_STATUS"));
        }
        return List.copyOf(result);
    }

    private static EnrichmentPlanStep step(EnrichmentPlanStepCode code, String family, Instant at, String condition) {
        return new EnrichmentPlanStep(UUID.randomUUID(), code, family, at, condition);
    }

    private static void validateKey(String key) {
        if (key == null || key.length() < 1 || key.length() > 128
                || !key.chars().allMatch(c -> c >= 33 && c <= 126)) {
            throw new IllegalArgumentException("Invalid idempotency key");
        }
    }

    static String fingerprint(DailySelectionCommand command) {
        String canonical = "enrichment-daily-admission-v1\n" + command.windowId() + "\n" + command.date() + "\n"
                + command.estimatedCallsPerMatch() + "\n"
                + command.calendarCollectionIds().stream().map(UUID::toString).sorted().reduce("", (a, b) -> a + b + "\n")
                + "--priority--\n"
                + command.priorityFixtureIds().stream().map(UUID::toString).sorted().reduce("", (a, b) -> a + b + "\n");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
