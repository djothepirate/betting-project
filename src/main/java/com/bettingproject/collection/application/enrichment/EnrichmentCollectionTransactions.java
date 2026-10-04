package com.bettingproject.collection.application.enrichment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.bettingproject.collection.application.budget.BudgetActionResult;
import com.bettingproject.collection.application.budget.BudgetCommands;
import com.bettingproject.collection.application.budget.ProviderBudgetTransactions;
import com.bettingproject.collection.application.budget.ProviderBudgetRepository;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.budget.BudgetModel.Proof;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;
import com.bettingproject.collection.domain.budget.BudgetModel.Intent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short atomic persistence operations only; no provider transport or parsing runs here. */
@Service
@Profile({"control-api", "batch-worker"})
public class EnrichmentCollectionTransactions {
    private final ProviderBudgetTransactions budget;
    private final EnrichmentCollectionStore attempts;
    private final EnrichmentObservationStore observations;
    private final EnrichmentDerivationStore derivations;
    private final ProviderBudgetRepository budgets;
    private final EnrichmentPlanExecutionStore planSteps;
    private final EnrichmentDispatchPlanner dispatch;
    private final Clock clock;

    public EnrichmentCollectionTransactions(ProviderBudgetTransactions budget,
            EnrichmentCollectionStore attempts, EnrichmentObservationStore observations,
            EnrichmentDerivationStore derivations, ProviderBudgetRepository budgets,
            EnrichmentPlanExecutionStore planSteps, EnrichmentDispatchPlanner dispatch, Clock clock) {
        this.budget = budget;
        this.attempts = attempts;
        this.observations = observations;
        this.derivations = derivations;
        this.budgets = budgets;
        this.planSteps = planSteps;
        this.dispatch = dispatch;
        this.clock = clock;
    }

    @Transactional
    public EnrichmentPreparation prepare(BudgetCommands.Reserve command,
            Function<Intent, EnrichmentCollectionAttempt> candidateFactory) {
        BudgetActionResult reserved = budget.reserve(command);
        if (reserved.code() != ResultCode.OK || reserved.intent() == null) {
            return new EnrichmentPreparation(reserved.code(), null, false);
        }
        EnrichmentCollectionAttempt candidate = candidateFactory.apply(reserved.intent());
        if (!reserved.intent().id().equals(candidate.budgetIntentId())) {
            throw new IllegalStateException("Budget intent identity changed during preparation");
        }
        var stored = attempts.createAndResolve(candidate);
        return new EnrichmentPreparation(ResultCode.OK, stored.attempt(), stored.inserted());
    }

    /** The authorization is returned only after this transaction commits to the caller. */
    @Transactional
    public EnrichmentAuthorization authorize(UUID attemptId) {
        EnrichmentCollectionAttempt attempt = attempts.findAttempt(attemptId).orElse(null);
        if (attempt == null) {
            return new EnrichmentAuthorization(ResultCode.NOT_FOUND, false);
        }
        if (attempt.state() == EnrichmentAttemptState.COMMITTED_FOR_SEND) {
            // A concurrent collector may still be executing the one authorized transport.
            // Keep the committed unit fenced; never turn a duplicate invocation into a state
            // transition that could make the original response fail its compare-and-set.
            return new EnrichmentAuthorization(ResultCode.ALREADY_COMMITTED, false);
        }
        if (attempt.state() != EnrichmentAttemptState.RESERVED) {
            return new EnrichmentAuthorization(ResultCode.ALREADY_COMMITTED, false);
        }
        if (attempt.family() == com.bettingproject.enrichment.domain.EnrichmentFamily.LINEUP) {
            planSteps.lockAdmission(attempt.admissionId());
            if (planSteps.hasPrematchCompleteLineup(attempt.admissionId(), attempt.kickoffAt())) {
                BudgetActionResult released = budget.release(attempt.budgetIntentId());
                if (released.code() != ResultCode.OK) {
                    throw new IllegalStateException("completed lineup reservation could not be released");
                }
                attempts.markReleased(attemptId, now(), "LINEUP_ALREADY_COMPLETE");
                planSteps.markStep(attempt.admissionId(), attempt.stepCode(),
                        com.bettingproject.enrichment.domain.EnrichmentPlanStepStatus.SKIPPED,
                        "LINEUP_ALREADY_COMPLETE");
                return new EnrichmentAuthorization(ResultCode.INVALID_TRANSITION, false);
            }
        }
        BudgetActionResult result = budget.authorizeSend(attempt.budgetIntentId());
        if (result.code() == ResultCode.OK && result.created()) {
            attempts.markCommitted(attemptId, now());
            return new EnrichmentAuthorization(ResultCode.OK, true);
        }
        if (result.code() == ResultCode.ALREADY_COMMITTED) {
            throw new IllegalStateException("Budget intent is committed while enrichment attempt remains reserved");
        }
        return new EnrichmentAuthorization(result.code(), false);
    }

    @Transactional
    public EnrichmentAuthorization markUncertain(UUID attemptId, String reasonCode) {
        EnrichmentCollectionAttempt attempt = attempts.findAttempt(attemptId).orElse(null);
        if (attempt == null) {
            return new EnrichmentAuthorization(ResultCode.NOT_FOUND, false);
        }
        if (attempt.state() == EnrichmentAttemptState.UNCERTAIN) {
            return new EnrichmentAuthorization(ResultCode.ALREADY_COMMITTED, false);
        }
        if (attempt.state() != EnrichmentAttemptState.COMMITTED_FOR_SEND) {
            return new EnrichmentAuthorization(ResultCode.INVALID_TRANSITION, false);
        }
        markUncertainInside(attempt, reasonCode);
        return new EnrichmentAuthorization(ResultCode.ALREADY_COMMITTED, false);
    }

    private void markUncertainInside(EnrichmentCollectionAttempt attempt, String reasonCode) {
        BudgetActionResult result = budget.markUncertain(attempt.budgetIntentId());
        if (result.code() != ResultCode.OK) {
            throw new IllegalStateException("Committed enrichment send could not be marked uncertain");
        }
        attempts.markUncertain(attempt.id(), now(), reasonCode);
    }

    @Transactional
    public UUID recordResponse(UUID attemptId, RawSnapshot raw, EnrichmentProviderResponse response,
            String outcomeCode) {
        EnrichmentCollectionAttempt attempt = attempts.findAttempt(attemptId).orElseThrow();
        if (response.httpStatus() == null) {
            markUncertainInside(attempt, "UNCERTAIN_SEND");
            return null;
        }
        String bodyHash = sha256(response.body());
        String fingerprint = sha256((response.httpStatus() + ":" + response.quotaRemaining() + ":" + outcomeCode
                + ":" + bodyHash).getBytes(StandardCharsets.UTF_8));
        BudgetActionResult outcome = budget.recordOutcome(new BudgetCommands.Outcome(
                attempt.budgetIntentId(), response.httpStatus(), fingerprint, null));
        if (outcome.code() != ResultCode.OK) {
            throw new IllegalStateException("Enrichment budget rejected a recorded provider outcome");
        }
        UUID rawId = attempts.recordResponse(attemptId, raw, response, outcomeCode);
        if (response.quotaRemaining() != null) {
            var window = budgets.findWindow(attempt.budgetWindowId()).orElseThrow();
            if (window.capacity() != null) {
                Instant observed = response.completedAt().truncatedTo(ChronoUnit.MICROS);
                Proof proof = new Proof("enrichment-response:" + attempt.id(),
                        sha256((response.httpStatus() + ":" + response.quotaRemaining() + ":" + outcomeCode)
                                .getBytes(StandardCharsets.UTF_8)));
                var previous = window.currentObservationId() == null ? null
                        : budgets.findObservation(window.currentObservationId()).orElseThrow();
                if (response.quotaRemaining() > window.capacity() || observed.isBefore(window.startsAt())
                        || previous == null || !observed.isBefore(previous.validUntil())
                        || !observed.isBefore(window.endsAt())) {
                    budget.recordUnusableQuota(window.id(), proof);
                }
                else {
                    budget.observeQuota(window.id(), new BudgetCommands.QuotaReading(UUID.randomUUID(),
                            response.quotaRemaining(), observed, previous.validUntil(), proof, Set.of()));
                }
            }
        }
        return rawId;
    }

    @Transactional
    public UUID appendDerivation(EnrichmentObservationWrite write, EnrichmentDerivationRecord derivation) {
        return appendDerivation(write, derivation, null);
    }

    @Transactional
    public UUID appendDerivation(EnrichmentObservationWrite write, EnrichmentDerivationRecord derivation,
            EnrichmentFinalStatusEvidence finalEvidence) {
        EnrichmentCollectionAttempt attempt = attempts.findAttempt(derivation.attemptId()).orElseThrow();
        planSteps.lockAdmission(attempt.admissionId());
        var stored = observations.storeAndResolve(write);
        EnrichmentDerivationRecord resolved = new EnrichmentDerivationRecord(derivation.id(), derivation.attemptId(),
                derivation.family(), derivation.parserVersion(), derivation.outcome(), stored.id(),
                derivation.rawSha256(), derivation.evaluatedAt());
        derivations.appendDerivation(resolved);
        if (finalEvidence != null) {
            var armed = planSteps.armPostmatch(attempt.admissionId(), stored.id(),
                    finalEvidence.observedAt(), finalEvidence.policyVersion());
            dispatch.planArmedPostmatch(attempt.admissionId(), armed);
        }
        return stored.id();
    }

    @Transactional
    public void appendRejectedDerivation(EnrichmentDerivationRecord derivation) {
        derivations.appendDerivation(derivation);
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
