package com.bettingproject.collection.domain.budget;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bettingproject.collection.domain.budget.BudgetModel.*;

/** Conservative arithmetic over durable state, deliberately independent of cadence at reservation. */
public final class BudgetCalculator {
    public Availability calculate(Window window, List<Intent> intents, QuotaObservation observation, Instant now) {
        Objects.requireNonNull(now, "budget evaluation time");
        if (window == null) {
            return refused(ResultCode.WINDOW_UNINITIALIZED);
        }
        if (window.state() == WindowState.CLOSED || now.isBefore(window.startsAt()) || !now.isBefore(window.endsAt())) {
            return refused(ResultCode.OUTSIDE_WINDOW);
        }
        if (window.state() == WindowState.SUSPENDED) {
            return refused(ResultCode.SUSPENDED);
        }
        Objects.requireNonNull(intents, "budget intents");
        if (intents.stream().anyMatch(intent -> !window.id().equals(intent.windowId()))
                || intents.stream().map(Intent::id).distinct().count() != intents.size()) {
            throw new IllegalArgumentException("inconsistent budget projection");
        }
        long held = intents.stream().filter(Intent::held).count();
        long consumed = intents.stream().filter(Intent::consumed).count();
        long available = window.projectLimit() - window.projectInitial() - consumed - held;
        if (window.capacity() != null) {
            if (window.quotaInconsistent()) {
                return refused(ResultCode.CONTRADICTORY_OBSERVATION);
            }
            if (observation == null || window.currentObservationId() == null) {
                return refused(ResultCode.QUOTA_OBSERVATION_MISSING);
            }
            if (!window.id().equals(observation.windowId())
                    || !window.currentObservationId().equals(observation.id())
                    || observation.disposition() != ObservationDisposition.ACCEPTED
                    || observation.remaining() > window.capacity()
                    || observation.observedAt().isBefore(window.startsAt())
                    || observation.observedAt().isAfter(now)
                    || observation.validUntil().isAfter(window.endsAt())) {
                return refused(ResultCode.CONTRADICTORY_OBSERVATION);
            }
            if (!now.isBefore(observation.validUntil())) {
                return refused(ResultCode.STALE_OBSERVATION);
            }
            Set<java.util.UUID> consumedIds = intents.stream().filter(Intent::consumed)
                    .map(Intent::id).collect(Collectors.toSet());
            if (!consumedIds.containsAll(observation.coveredIntentIds())) {
                return refused(ResultCode.CONTRADICTORY_OBSERVATION);
            }
            long uncovered = consumed - observation.coveredIntentIds().size();
            available = Math.min(available, window.capacity() - window.reserve()
                    - window.sharedInitial() - consumed - held);
            available = Math.min(available, observation.remaining() - window.reserve() - uncovered - held);
        }
        return available <= 0 ? refused(ResultCode.EXHAUSTED) : new Availability(ResultCode.OK, available, null);
    }

    /** The caller supplies all intentions for the quota scope, including preceding windows. */
    public Availability checkCadence(Window window, List<Intent> allScopeIntents, Instant now) {
        Objects.requireNonNull(window, "budget window");
        Objects.requireNonNull(allScopeIntents, "budget intents");
        Objects.requireNonNull(now, "budget evaluation time");
        if (window.cadenceLimit() == null) {
            return new Availability(ResultCode.OK, MAX_UNITS, null);
        }
        List<Instant> expirations = new ArrayList<>();
        long unresolved = 0;
        for (Intent intent : allScopeIntents) {
            if (!intent.consumed()) {
                continue;
            }
            if (intent.resultAt() == null) {
                unresolved++;
            }
            else {
                Instant expiration;
                try {
                    expiration = intent.resultAt().plus(window.cadencePeriod());
                }
                catch (DateTimeException exception) {
                    expiration = Instant.MAX;
                }
                if (expiration.isAfter(now)) {
                    expirations.add(expiration);
                }
            }
        }
        long occupied = unresolved + expirations.size();
        if (occupied < window.cadenceLimit()) {
            return new Availability(ResultCode.OK, window.cadenceLimit() - occupied, null);
        }
        long expirationsRequired = occupied - window.cadenceLimit() + 1;
        expirations.sort(Comparator.naturalOrder());
        Instant retry = expirationsRequired <= expirations.size()
                ? expirations.get((int) expirationsRequired - 1) : null;
        return new Availability(ResultCode.RATE_LIMITED, 0, retry);
    }

    private Availability refused(ResultCode code) {
        return new Availability(code, 0, null);
    }
}
