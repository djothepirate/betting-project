package com.bettingproject.collection.application.enrichment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentDispatchWindow;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepStatus;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import com.bettingproject.operations.application.jobs.JobLeaseLostException;
import com.bettingproject.operations.application.jobs.JobTransactions;
import com.bettingproject.operations.domain.JobModel.Claim;
import com.bettingproject.operations.domain.JobModel.Outcome;
import com.bettingproject.operations.domain.JobModel.Type;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Executes a durable plan step serially; each provider attempt receives a separate committed budget permit. */
@Service
@Profile("batch-worker")
@Transactional(propagation = Propagation.NEVER)
public class EnrichmentJobExecutor {
    private final EnrichmentJobInputStore inputs;
    private final EnrichmentCollectionStore contexts;
    private final EnrichmentPlanExecutionStore stepStore;
    private final EnrichmentPlanExecutionTransactions stepTransactions;
    private final ProviderCapabilityRegistry registry;
    private final EnrichmentCollectionService collection;
    private final JobTransactions jobs;
    private final Clock clock;

    public EnrichmentJobExecutor(EnrichmentJobInputStore inputs, EnrichmentCollectionStore contexts,
            EnrichmentPlanExecutionStore stepStore, EnrichmentPlanExecutionTransactions stepTransactions,
            ProviderCapabilityRegistry registry, EnrichmentCollectionService collection,
            JobTransactions jobs, Clock clock) {
        this.inputs = Objects.requireNonNull(inputs);
        this.contexts = Objects.requireNonNull(contexts);
        this.stepStore = Objects.requireNonNull(stepStore);
        this.stepTransactions = Objects.requireNonNull(stepTransactions);
        this.registry = Objects.requireNonNull(registry);
        this.collection = Objects.requireNonNull(collection);
        this.jobs = Objects.requireNonNull(jobs);
        this.clock = Objects.requireNonNull(clock);
    }

    public Outcome execute(Claim claim, Type expectedType) {
        EnrichmentExecution execution = new EnrichmentExecution() {
            @Override public <T> T atomic(Supplier<T> work) { return jobs.fenced(claim, work); }
        };
        var input = inputs.find(claim.job().id()).orElse(null);
        if (input == null || input.jobType() != expectedType || claim.job().type() != expectedType) {
            return Outcome.failed("JOB_INPUT_UNAVAILABLE");
        }
        if (!input.registrySha256().equals(registry.documentSha256())) {
            mark(claim, input.admissionId(), input.stepCode(), EnrichmentPlanStepStatus.SKIPPED,
                    "REGISTRY_CHANGED");
            return Outcome.failed("REGISTRY_CHANGED");
        }
        var context = contexts.findContext(input.admissionId(), input.stepCode()).orElse(null);
        if (context == null || !context.step().id().equals(input.stepId())
                || !context.planRegistrySha256().equals(input.registrySha256())) {
            mark(claim, input.admissionId(), input.stepCode(), EnrichmentPlanStepStatus.SKIPPED,
                    "PLAN_CONTEXT_CHANGED");
            return Outcome.failed("PLAN_CONTEXT_CHANGED");
        }
        var status = stepStore.stepStatus(input.admissionId(), input.stepCode()).orElse(null);
        if (status == EnrichmentPlanStepStatus.COMPLETED || status == EnrichmentPlanStepStatus.SKIPPED
                || status == EnrichmentPlanStepStatus.MISSED_WINDOW) {
            return Outcome.success();
        }
        if (status != EnrichmentPlanStepStatus.QUEUED) {
            return Outcome.failed("PLAN_STEP_NOT_QUEUED");
        }

        EnrichmentDispatchWindow window = EnrichmentDispatchWindow.forStep(context.step(), context.kickoffAt());
        var decision = window.decide(clock.instant());
        if (decision == EnrichmentDispatchWindow.Decision.BEFORE_WINDOW) {
            return Outcome.retry("DISPATCH_NOT_OPEN");
        }
        if (decision == EnrichmentDispatchWindow.Decision.MISSED_WINDOW) {
            stepTransactions.mark(input.admissionId(), input.stepCode(),
                    EnrichmentPlanStepStatus.MISSED_WINDOW, "MISSED_WINDOW");
            return Outcome.success();
        }
        if (input.stepCode() == com.bettingproject.enrichment.domain.EnrichmentPlanStepCode.LINEUP_T_MINUS_30
                || input.stepCode() == com.bettingproject.enrichment.domain.EnrichmentPlanStepCode.LINEUP_T_MINUS_15) {
            if (completeIfLineupAlreadyComplete(claim, input.admissionId(), context.kickoffAt())) {
                return Outcome.success();
            }
        }

        boolean anyProcessed = false;
        String firstRefusal = null;
        String firstFailure = null;
        for (EnrichmentJobRoute route : input.routes()) {
            if (route.family() == EnrichmentFamily.LINEUP && completeIfLineupAlreadyComplete(
                    claim, input.admissionId(), context.kickoffAt())) {
                mark(claim, input.admissionId(), input.stepCode(), EnrichmentPlanStepStatus.SKIPPED,
                        "LINEUP_ALREADY_COMPLETE");
                return Outcome.success();
            }
            // Refresh the bounded claim in a committed transaction; never hold its row lock across HTTP.
            jobs.heartbeat(claim);
            ProviderCapabilityKey key = route.capability();
            EnrichmentCollectionResult result = collection.collect(new EnrichmentCollectionCommand(
                    input.admissionId(), input.stepCode(), route.family(), key, input.budgetWindowId()),
                    route.parserVersion(), execution);
            if (result.code() == EnrichmentCollectionResult.Code.REFUSED) {
                if (firstRefusal == null) { firstRefusal = safeCode(result.reasonCode(), "COLLECTION_REFUSED"); }
                if ("LINEUP_ALREADY_COMPLETE".equals(result.reasonCode())) {
                    mark(claim, input.admissionId(), input.stepCode(), EnrichmentPlanStepStatus.SKIPPED,
                            "LINEUP_ALREADY_COMPLETE");
                    return Outcome.success();
                }
            }
            else {
                anyProcessed = true;
                if (result.code() == EnrichmentCollectionResult.Code.UNCERTAIN
                        || result.code() == EnrichmentCollectionResult.Code.HTTP_ERROR) {
                    if (firstFailure == null) {
                        firstFailure = result.code() == EnrichmentCollectionResult.Code.UNCERTAIN
                                ? "UNCERTAIN_SEND" : "HTTP_ERROR";
                    }
                }
            }
        }

        if (anyProcessed) {
            mark(claim, input.admissionId(), input.stepCode(), EnrichmentPlanStepStatus.COMPLETED,
                    firstFailure == null ? "PROCESSED" : firstFailure);
            return Outcome.success();
        }
        mark(claim, input.admissionId(), input.stepCode(), EnrichmentPlanStepStatus.SKIPPED,
                firstRefusal == null ? "COLLECTION_REFUSED" : firstRefusal);
        return Outcome.failed(firstRefusal == null ? "COLLECTION_REFUSED" : firstRefusal);
    }

    private static String safeCode(String value, String fallback) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}") ? value : fallback;
    }

    private boolean completeIfLineupAlreadyComplete(Claim claim, UUID admissionId, Instant kickoffAt) {
        return jobs.fenced(claim, () -> stepTransactions.completeIfLineupAlreadyComplete(admissionId, kickoffAt));
    }

    private void mark(Claim claim, UUID admissionId, EnrichmentPlanStepCode stepCode,
            EnrichmentPlanStepStatus status, String reason) {
        jobs.fenced(claim, () -> {
            stepTransactions.mark(admissionId, stepCode, status, reason);
            return null;
        });
    }
}
