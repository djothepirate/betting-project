package com.bettingproject.collection.application.budget;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.collection.domain.budget.BudgetModel.Proof;

public final class BudgetCommands {
    private BudgetCommands() { }

    public record Initialize(UUID windowId, String provider, String accountRef,
            Instant startsAt, Instant endsAt, Long capacity, long projectLimit,
            long reserve, long sharedInitial, long projectInitial,
            Integer cadenceLimit, Duration cadencePeriod, Long initialRemaining,
            Instant observedAt, Instant validUntil, Proof proof, String justification) { }

    public record Reserve(UUID windowId, String idempotencyKey,
            String logicalEndpoint, String requestSha256) { }

    public record QuotaReading(UUID id, long remaining, Instant observedAt,
            Instant validUntil, Proof proof, Set<UUID> coveredIntentIds) { }

    public record Outcome(UUID intentId, int httpStatus, String fingerprint,
            QuotaReading quotaReading) { }

    public record Reconcile(UUID intentId, long expectedVersion,
            Proof proof, String justification) { }

    public record ReconcileQuota(UUID windowId, long expectedVersion,
            QuotaReading reading, String justification) { }
}
