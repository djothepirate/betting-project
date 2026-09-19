package com.bettingproject.collection.application.budget;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.bettingproject.collection.application.budget.ProviderBudgetTransactions.*;
import static com.bettingproject.collection.domain.budget.BudgetModel.*;

@Service
@Profile("control-api")
@Transactional
public class ProviderBudgetAdministrationTransactions {
    private final ProviderBudgetRepository repository;
    private final ProviderBudgetTransactions operations;
    private final BudgetOperatorIdentityProvider identity;
    private final BudgetJustificationSanitizer sanitizer;

    public ProviderBudgetAdministrationTransactions(ProviderBudgetRepository repository,
            ProviderBudgetTransactions operations, BudgetOperatorIdentityProvider identity,
            BudgetJustificationSanitizer sanitizer) {
        this.repository = repository;
        this.operations = operations;
        this.identity = identity;
        this.sanitizer = sanitizer;
    }

    public BudgetActionResult initialize(BudgetCommands.Initialize command) {
        Objects.requireNonNull(command);
        String justification = justification(command.justification());
        Optional<String> operator = identity.currentOperator();
        if (operator.isEmpty()) {
            return refused(ResultCode.OPERATOR_UNAVAILABLE, command.windowId());
        }
        Instant now = operations.now();
        requireMicros(command.startsAt());
        requireMicros(command.endsAt());
        if (command.capacity() != null) {
            requireMicros(command.observedAt());
            requireMicros(command.validUntil());
        }
        // Validate before creating a durable scope; no identifiers of real accounts are configured by default.
        Scope definition = new Scope(UUID.randomUUID(), command.provider(), command.accountRef(), now);
        Window candidate = definition(command, definition.id(), now);
        QuotaObservation initial = initialObservation(command, now);
        Scope scope = repository.createAndLockScope(definition.provider(), definition.accountRef(), now);
        candidate = definition(command, scope.id(), now);
        Optional<Window> sameId = repository.findWindow(candidate.id());
        if (sameId.isPresent()) {
            Window existing = sameId.get();
            boolean identical = sameDefinition(existing, candidate);
            if (initial != null) {
                identical &= repository.findObservation(initial.id()).map(stored ->
                        stored.windowId().equals(initial.windowId())
                        && stored.remaining() == initial.remaining()
                        && stored.observedAt().equals(initial.observedAt())
                        && stored.validUntil().equals(initial.validUntil())
                        && stored.proof().equals(initial.proof())).orElse(false);
            }
            return refused(identical ? ResultCode.OK : ResultCode.IDEMPOTENCY_CONFLICT, candidate.id());
        }
        now = operations.now();
        candidate = definition(command, scope.id(), now);
        if (initial != null && !now.isBefore(initial.validUntil())) {
            throw new IllegalArgumentException("initial quota evidence has expired");
        }
        List<Window> previous = repository.findWindows(scope.id());
        for (Window window : previous) {
            if (candidate.startsAt().isBefore(window.endsAt()) && window.startsAt().isBefore(candidate.endsAt())
                    || window.state() != WindowState.CLOSED && now.isBefore(window.endsAt())) {
                return refused(ResultCode.VERSION_CONFLICT, candidate.id());
            }
        }
        // Closing an expired predecessor is part of this explicit initialization, never a clock-triggered reset.
        for (Window window : previous) {
            if (window.state() != WindowState.CLOSED) {
                repository.lockWindow(window.id());
                operations.updateWindow(window.close(now), window.version());
                operations.event(window, null, "WINDOW_CLOSED", null, operator.get(), justification,
                        command.proof(), now);
            }
        }
        repository.insertWindow(candidate);
        if (initial != null) {
            repository.insertObservation(initial);
            operations.updateWindow(candidate.withObservation(initial.id(), false, now), candidate.version());
        }
        operations.event(candidate, null, "WINDOW_INITIALIZED", null, operator.get(), justification,
                command.proof(), now);
        return new BudgetActionResult(ResultCode.OK, candidate.id(), null, true);
    }

    public BudgetActionResult reconcile(BudgetCommands.Reconcile command) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(command.proof());
        requireVersion(command.expectedVersion());
        String justification = justification(command.justification());
        Optional<String> operator = identity.currentOperator();
        if (operator.isEmpty()) {
            return refused(ResultCode.OPERATOR_UNAVAILABLE, null);
        }
        LockedIntent locked = operations.lockedIntent(command.intentId());
        if (locked == null) {
            return refused(ResultCode.NOT_FOUND, null);
        }
        Intent intent = locked.intent();
        if (intent.version() != command.expectedVersion()) {
            return refused(ResultCode.VERSION_CONFLICT, intent.windowId());
        }
        if (intent.state() != IntentState.UNCERTAIN) {
            return refused(ResultCode.INVALID_TRANSITION, intent.windowId());
        }
        Instant now = operations.now();
        Intent reconciled = intent.transition(IntentState.RECONCILED, now, null, command.proof().sha256());
        operations.updateIntent(reconciled, intent.version());
        operations.event(locked.window(), intent.id(), "RECONCILED", null, operator.get(), justification,
                command.proof(), now);
        return success(locked.window(), reconciled);
    }

    public BudgetActionResult reconcileQuota(BudgetCommands.ReconcileQuota command) {
        Objects.requireNonNull(command);
        requireVersion(command.expectedVersion());
        String justification = justification(command.justification());
        Optional<String> operator = identity.currentOperator();
        if (operator.isEmpty()) {
            return refused(ResultCode.OPERATOR_UNAVAILABLE, command.windowId());
        }
        Optional<Window> found = operations.lockedWindow(command.windowId());
        if (found.isEmpty()) {
            return refused(ResultCode.WINDOW_UNINITIALIZED, command.windowId());
        }
        Window window = found.get();
        if (window.version() != command.expectedVersion()) {
            return refused(ResultCode.VERSION_CONFLICT, window.id());
        }
        // A quota reconciliation never lifts an HTTP authorization/rate suspension.
        return operations.applyObservation(window, command.reading(), true, operator.get(),
                justification, operations.now());
    }

    private Window definition(BudgetCommands.Initialize command, UUID scopeId, Instant now) {
        return new Window(command.windowId(), scopeId, command.startsAt(), command.endsAt(),
                command.capacity(), command.projectLimit(), command.reserve(), command.sharedInitial(),
                command.projectInitial(), command.cadenceLimit(), command.cadencePeriod(),
                WindowState.ACTIVE, null, false, 1, now, now, command.proof());
    }

    private QuotaObservation initialObservation(BudgetCommands.Initialize command, Instant now) {
        if (command.capacity() == null) {
            if (command.initialRemaining() != null || command.observedAt() != null || command.validUntil() != null) {
                throw new IllegalArgumentException("periodic quota is not applicable");
            }
            return null;
        }
        Objects.requireNonNull(command.initialRemaining());
        QuotaObservation initial = new QuotaObservation(UUID.nameUUIDFromBytes(
                ("budget-initial:" + command.windowId()).getBytes(StandardCharsets.UTF_8)),
                command.windowId(), command.initialRemaining(), command.observedAt(), command.validUntil(),
                command.proof(), Set.of(), ObservationDisposition.ACCEPTED, now);
        if (initial.remaining() > command.capacity() - command.sharedInitial()
                || initial.observedAt().isBefore(command.startsAt())
                || initial.validUntil().isAfter(command.endsAt())) {
            throw new IllegalArgumentException("invalid initial quota evidence");
        }
        return initial;
    }

    private boolean sameDefinition(Window left, Window right) {
        return left.scopeId().equals(right.scopeId()) && left.startsAt().equals(right.startsAt())
                && left.endsAt().equals(right.endsAt()) && Objects.equals(left.capacity(), right.capacity())
                && left.projectLimit() == right.projectLimit() && left.reserve() == right.reserve()
                && left.sharedInitial() == right.sharedInitial() && left.projectInitial() == right.projectInitial()
                && Objects.equals(left.cadenceLimit(), right.cadenceLimit())
                && Objects.equals(left.cadencePeriod(), right.cadencePeriod()) && left.proof().equals(right.proof());
    }

    private String justification(String value) {
        if (value == null || value.strip().isEmpty() || value.strip().length() > 1000) {
            throw new IllegalArgumentException("invalid budget justification");
        }
        String sanitized = sanitizer.sanitize(value.strip());
        if (sanitized == null || sanitized.isBlank() || sanitized.length() > 1000) {
            throw new IllegalArgumentException("invalid sanitized justification");
        }
        return sanitized;
    }

    private void requireVersion(long version) {
        if (version < 1) {
            throw new IllegalArgumentException("invalid expected version");
        }
    }
}
