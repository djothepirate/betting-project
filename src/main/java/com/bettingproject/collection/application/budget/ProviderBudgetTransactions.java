package com.bettingproject.collection.application.budget;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.collection.domain.budget.BudgetCalculator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.bettingproject.collection.domain.budget.BudgetModel.*;

/** Short database transactions only; this component never sends a supplier request. */
@Service
@Profile({"control-api", "batch-worker"})
@Transactional
public class ProviderBudgetTransactions {
    private final ProviderBudgetRepository repository;
    private final Clock clock;
    private final BudgetCalculator calculator = new BudgetCalculator();

    public ProviderBudgetTransactions(ProviderBudgetRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public BudgetActionResult reserve(BudgetCommands.Reserve command) {
        Objects.requireNonNull(command);
        Instant now = now();
        Intent candidate = new Intent(UUID.randomUUID(), command.windowId(), command.idempotencyKey(),
                command.logicalEndpoint(), command.requestSha256(), IntentState.RESERVED,
                1, now, now, null, null, null, null);
        Optional<Intent> priorKey = repository.findIntentByKey(command.idempotencyKey());
        if (priorKey.isPresent() && !priorKey.get().windowId().equals(command.windowId())) {
            return refused(ResultCode.IDEMPOTENCY_CONFLICT, command.windowId());
        }
        Optional<Window> found = lockedWindow(command.windowId());
        if (found.isEmpty()) {
            return refused(ResultCode.WINDOW_UNINITIALIZED, command.windowId());
        }
        Window window = found.get();
        Optional<Intent> existing = repository.findIntentByKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return sameIntent(existing.get(), candidate);
        }
        now = now();
        candidate = new Intent(candidate.id(), candidate.windowId(), candidate.idempotencyKey(),
                candidate.logicalEndpoint(), candidate.requestSha256(), IntentState.RESERVED,
                1, now, now, null, null, null, null);
        Availability available = availability(window, now, null);
        if (available.code() != ResultCode.OK) {
            return refused(available.code(), window.id());
        }
        Intent stored = repository.insertIntentIfAbsentAndResolve(candidate);
        if (!stored.id().equals(candidate.id())) {
            return sameIntent(stored, candidate);
        }
        event(window, stored.id(), "RESERVED", null, null, null, null, now);
        return new BudgetActionResult(ResultCode.OK, window.id(), stored, true);
    }

    public BudgetActionResult authorizeSend(UUID intentId) {
        LockedIntent locked = lockedIntent(intentId);
        if (locked == null) {
            return refused(ResultCode.NOT_FOUND, null);
        }
        Intent intent = locked.intent();
        Window window = locked.window();
        if (intent.consumed()) {
            return new BudgetActionResult(ResultCode.ALREADY_COMMITTED, window.id(), intent, false);
        }
        if (!intent.held()) {
            return refused(ResultCode.INVALID_TRANSITION, window.id());
        }
        Instant now = now();
        // This intention already holds one unit. Check that its unit is still affordable.
        Availability available = availability(window, now, intent.id());
        if (available.code() != ResultCode.OK) {
            return refused(available.code(), window.id());
        }
        Availability cadence = calculator.checkCadence(window,
                repository.findScopeIntents(window.scopeId()), now);
        if (cadence.code() != ResultCode.OK) {
            return new BudgetActionResult(cadence.code(), window.id(), null, false, cadence.retryAt());
        }
        Intent committed = intent.transition(IntentState.COMMITTED_FOR_SEND, now, null, null);
        updateIntent(committed, intent.version());
        event(window, intent.id(), "COMMITTED_FOR_SEND", null, null, null, null, now);
        return new BudgetActionResult(ResultCode.OK, window.id(), committed, true);
    }

    public BudgetActionResult release(UUID intentId) {
        LockedIntent locked = lockedIntent(intentId);
        if (locked == null) {
            return refused(ResultCode.NOT_FOUND, null);
        }
        Intent intent = locked.intent();
        if (intent.state() == IntentState.RELEASED) {
            return success(locked.window(), intent);
        }
        if (!intent.held()) {
            return refused(ResultCode.INVALID_TRANSITION, intent.windowId());
        }
        Instant now = now();
        Intent released = intent.transition(IntentState.RELEASED, now, null, null);
        updateIntent(released, intent.version());
        event(locked.window(), intent.id(), "RELEASED", null, null, null, null, now);
        return success(locked.window(), released);
    }

    public BudgetActionResult markUncertain(UUID intentId) {
        LockedIntent locked = lockedIntent(intentId);
        if (locked == null) {
            return refused(ResultCode.NOT_FOUND, null);
        }
        Intent intent = locked.intent();
        if (intent.state() == IntentState.UNCERTAIN) {
            return success(locked.window(), intent);
        }
        if (intent.state() != IntentState.COMMITTED_FOR_SEND) {
            return refused(ResultCode.INVALID_TRANSITION, intent.windowId());
        }
        Instant now = now();
        Intent uncertain = intent.transition(IntentState.UNCERTAIN, now, null, null);
        updateIntent(uncertain, intent.version());
        repository.insertIncident(new Incident(UUID.randomUUID(), intent.windowId(), intent.id(),
                "UNCERTAIN_SEND", now));
        event(locked.window(), intent.id(), "UNCERTAIN", "UNCERTAIN_SEND", null, null, null, now);
        return success(locked.window(), uncertain);
    }

    public BudgetActionResult recordOutcome(BudgetCommands.Outcome command) {
        Objects.requireNonNull(command);
        LockedIntent locked = lockedIntent(command.intentId());
        if (locked == null) {
            return refused(ResultCode.NOT_FOUND, null);
        }
        Intent intent = locked.intent();
        Window window = locked.window();
        String fingerprint = outcomeFingerprint(command);
        if (intent.state() == IntentState.RESULT_RECORDED) {
            boolean identical = Objects.equals(intent.httpStatus(), command.httpStatus())
                    && Objects.equals(intent.resultFingerprint(), fingerprint);
            if (command.quotaReading() != null) {
                QuotaObservation prior = repository.findObservation(command.quotaReading().id()).orElse(null);
                identical &= sameReading(prior, window.id(), command.quotaReading());
            }
            return identical ? success(window, intent) : refused(ResultCode.IDEMPOTENCY_CONFLICT, window.id());
        }
        if (intent.state() != IntentState.COMMITTED_FOR_SEND) {
            return refused(ResultCode.INVALID_TRANSITION, window.id());
        }
        Instant now = now();
        Intent completed = intent.transition(IntentState.RESULT_RECORDED, now,
                command.httpStatus(), fingerprint);
        updateIntent(completed, intent.version());
        if (command.quotaReading() != null) {
            // Invalid quota input must throw: the enclosing transaction rolls back the result too.
            applyObservation(window, command.quotaReading(), false, null, null, now);
            window = repository.findWindow(window.id()).orElseThrow();
        }
        if (Set.of(401, 403, 429).contains(command.httpStatus())) {
            String code = "HTTP_" + command.httpStatus();
            repository.insertIncident(new Incident(UUID.randomUUID(), window.id(), intent.id(), code, now));
            if (window.state() == WindowState.ACTIVE) {
                Window suspended = window.suspend(now);
                updateWindow(suspended, window.version());
                window = suspended;
            }
            event(window, intent.id(), "SUSPENDED", code, null, null, null, now);
        }
        event(window, intent.id(), "RESULT_RECORDED", null, null, null, null, now);
        return success(window, completed);
    }

    public BudgetActionResult observeQuota(UUID windowId, BudgetCommands.QuotaReading reading) {
        Optional<Window> window = lockedWindow(windowId);
        if (window.isEmpty()) {
            return refused(ResultCode.WINDOW_UNINITIALIZED, windowId);
        }
        return applyObservation(window.get(), reading, false, null, null, now());
    }

    /** A received counter outside its declared envelope is evidence, not permission to spend. */
    public void recordUnusableQuota(UUID windowId, Proof proof) {
        Window window = lockedWindow(windowId).orElseThrow();
        Instant now = now();
        updateWindow(window.withObservation(window.currentObservationId(), true, now), window.version());
        repository.insertIncident(new Incident(UUID.randomUUID(), window.id(), null, "CONTRADICTORY_QUOTA", now));
        event(window, null, "QUOTA_OBSERVED", "CONTRADICTORY", null, null, proof, now);
    }

    public Availability availability(UUID windowId) {
        return lockedWindow(windowId).map(window -> availability(window, now(), null))
                .orElseGet(() -> new Availability(ResultCode.WINDOW_UNINITIALIZED, 0, null));
    }

    @Transactional(readOnly = true)
    public Optional<Intent> findIntent(UUID intentId) {
        return repository.findIntent(intentId);
    }

    @Transactional(readOnly = true)
    public List<Incident> incidents(UUID windowId) {
        return repository.findIncidents(windowId);
    }

    private Availability availability(Window window, Instant now, UUID heldIntentToExclude) {
        List<Intent> intents = repository.findWindowIntents(window.id()).stream()
                .filter(intent -> !intent.id().equals(heldIntentToExclude)).toList();
        QuotaObservation observation = window.currentObservationId() == null ? null
                : repository.findObservation(window.currentObservationId()).orElse(null);
        return calculator.calculate(window, intents, observation, now);
    }

    Optional<Window> lockedWindow(UUID id) {
        Objects.requireNonNull(id);
        Optional<Window> found = repository.findWindow(id);
        if (found.isEmpty()) {
            return found;
        }
        repository.lockScope(found.get().scopeId());
        repository.lockWindow(id);
        return repository.findWindow(id);
    }

    LockedIntent lockedIntent(UUID id) {
        Objects.requireNonNull(id);
        Optional<Intent> found = repository.findIntent(id);
        if (found.isEmpty()) {
            return null;
        }
        Window window = lockedWindow(found.get().windowId()).orElseThrow();
        repository.lockIntent(id);
        return new LockedIntent(window, repository.findIntent(id).orElseThrow());
    }

    BudgetActionResult applyObservation(Window window, BudgetCommands.QuotaReading reading,
            boolean reconciliation, String operator, String justification, Instant now) {
        Objects.requireNonNull(reading);
        requireMicros(reading.observedAt());
        requireMicros(reading.validUntil());
        if (window.capacity() == null) {
            throw new IllegalArgumentException("quota observation is not applicable");
        }
        QuotaObservation candidate = new QuotaObservation(reading.id(), window.id(), reading.remaining(),
                reading.observedAt(), reading.validUntil(), reading.proof(), reading.coveredIntentIds(),
                ObservationDisposition.ACCEPTED, now);
        if (candidate.remaining() > window.capacity() || candidate.observedAt().isBefore(window.startsAt())
                || candidate.validUntil().isAfter(window.endsAt())) {
            throw new IllegalArgumentException("invalid quota evidence bounds");
        }
        List<Intent> intents = repository.findWindowIntents(window.id());
        for (UUID covered : candidate.coveredIntentIds()) {
            Intent intent = intents.stream().filter(item -> item.id().equals(covered)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("invalid quota coverage"));
            if (!intent.consumed() || intent.committedAt().isAfter(candidate.observedAt())) {
                throw new IllegalArgumentException("invalid quota coverage");
            }
        }
        Optional<QuotaObservation> duplicate = repository.findObservation(candidate.id());
        if (duplicate.isPresent()) {
            if (reconciliation) {
                return refused(ResultCode.IDEMPOTENCY_CONFLICT, window.id());
            }
            if (!sameReading(duplicate.get(), window.id(), reading)) {
                throw new IllegalArgumentException("conflicting quota evidence identity");
            }
            return refused(duplicate.get().disposition() == ObservationDisposition.CONTRADICTORY
                    ? ResultCode.CONTRADICTORY_OBSERVATION : ResultCode.OK, window.id());
        }
        QuotaObservation previous = window.currentObservationId() == null ? null
                : repository.findObservation(window.currentObservationId()).orElseThrow();
        ObservationDisposition disposition = ObservationDisposition.ACCEPTED;
        if (previous != null && candidate.observedAt().isBefore(previous.observedAt())) {
            disposition = ObservationDisposition.STALE;
        }
        else if (!reconciliation && (window.quotaInconsistent() || previous != null
                && (candidate.remaining() > previous.remaining()
                || candidate.observedAt().equals(previous.observedAt())
                && (candidate.remaining() != previous.remaining()
                || !candidate.coveredIntentIds().equals(previous.coveredIntentIds()))))) {
            disposition = ObservationDisposition.CONTRADICTORY;
        }
        if (reconciliation && (disposition == ObservationDisposition.STALE
                || !now.isBefore(candidate.validUntil()))) {
            throw new IllegalArgumentException("reconciliation requires current evidence");
        }
        QuotaObservation observation = new QuotaObservation(candidate.id(), window.id(), candidate.remaining(),
                candidate.observedAt(), candidate.validUntil(), candidate.proof(), candidate.coveredIntentIds(),
                disposition, now);
        repository.insertObservation(observation);
        if (disposition == ObservationDisposition.ACCEPTED) {
            updateWindow(window.withObservation(observation.id(), false, now), window.version());
        }
        else if (disposition == ObservationDisposition.CONTRADICTORY) {
            updateWindow(window.withObservation(window.currentObservationId(), true, now), window.version());
            repository.insertIncident(new Incident(UUID.randomUUID(), window.id(), null,
                    "CONTRADICTORY_QUOTA", now));
        }
        event(window, null, reconciliation ? "QUOTA_RECONCILED" : "QUOTA_OBSERVED",
                disposition.name(), operator, justification, observation.proof(), now);
        return refused(disposition == ObservationDisposition.CONTRADICTORY
                ? ResultCode.CONTRADICTORY_OBSERVATION : ResultCode.OK, window.id());
    }

    private boolean sameReading(QuotaObservation stored, UUID windowId, BudgetCommands.QuotaReading reading) {
        return stored != null && stored.windowId().equals(windowId)
                && stored.remaining() == reading.remaining() && stored.observedAt().equals(reading.observedAt())
                && stored.validUntil().equals(reading.validUntil()) && stored.proof().equals(reading.proof())
                && stored.coveredIntentIds().equals(reading.coveredIntentIds());
    }

    private BudgetActionResult sameIntent(Intent stored, Intent candidate) {
        boolean same = stored.windowId().equals(candidate.windowId())
                && stored.logicalEndpoint().equals(candidate.logicalEndpoint())
                && stored.requestSha256().equals(candidate.requestSha256());
        return same ? new BudgetActionResult(ResultCode.OK, stored.windowId(), stored, false)
                : refused(ResultCode.IDEMPOTENCY_CONFLICT, candidate.windowId());
    }

    void updateWindow(Window window, long expectedVersion) {
        if (!repository.updateWindow(window, expectedVersion)) {
            throw new IllegalStateException("budget window compare-and-set failed");
        }
    }

    void updateIntent(Intent intent, long expectedVersion) {
        if (!repository.updateIntent(intent, expectedVersion)) {
            throw new IllegalStateException("budget intention compare-and-set failed");
        }
    }

    void event(Window window, UUID intentId, String type, String reason, String operator,
            String justification, Proof proof, Instant now) {
        repository.appendEvent(new Event(UUID.randomUUID(), window.scopeId(), window.id(), intentId,
                type, reason, operator, justification, proof, now));
    }

    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static void requireMicros(Instant value) {
        if (value == null || value.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("budget evidence requires microsecond precision");
        }
    }

    private String outcomeFingerprint(BudgetCommands.Outcome command) {
        new Proof("outcome", command.fingerprint());
        StringBuilder canonical = new StringBuilder("budget-outcome-v1\n")
                .append(command.httpStatus()).append('\n').append(command.fingerprint()).append('\n');
        BudgetCommands.QuotaReading reading = command.quotaReading();
        if (reading != null) {
            canonical.append(reading.id()).append('\n').append(reading.remaining()).append('\n')
                    .append(reading.observedAt()).append('\n').append(reading.validUntil()).append('\n')
                    .append(reading.proof().logicalId()).append('\n').append(reading.proof().sha256()).append('\n');
            reading.coveredIntentIds().stream().sorted().forEach(id -> canonical.append(id).append('\n'));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static BudgetActionResult refused(ResultCode code, UUID windowId) {
        return BudgetActionResult.refused(code, windowId);
    }

    static BudgetActionResult success(Window window, Intent intent) {
        return new BudgetActionResult(ResultCode.OK, window.id(), intent, false);
    }

    record LockedIntent(Window window, Intent intent) { }
}
