package com.bettingproject.collection.adapter.worker;

import java.util.UUID;
import java.util.function.Supplier;
import com.bettingproject.collection.application.calendar.*;
import com.bettingproject.collection.application.budget.*;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.operations.application.jobs.*;
import com.bettingproject.operations.domain.JobModel.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("batch-worker")
public class CalendarDiscoveryJobHandler implements JobHandler {
    private final CalendarJobInputStore inputs;
    private final ProviderCapabilityRegistry registry;
    private final CalendarCollectionService collection;
    private final ProviderBudgetTransactions budget;
    private final JobTransactions jobs;
    private final CalendarDerivationService derivation;
    public CalendarDiscoveryJobHandler(CalendarJobInputStore inputs, ProviderCapabilityRegistry registry,
            CalendarCollectionService collection, ProviderBudgetTransactions budget, JobTransactions jobs, CalendarDerivationService derivation) {
        this.inputs = inputs; this.registry = registry; this.collection = collection; this.budget = budget; this.jobs = jobs;
        this.derivation = derivation;
    }
    @Override public Type type() { return Type.CALENDAR_DISCOVERY; }
    @Override public Outcome execute(Claim claim) {
        var input = inputs.find(claim.job().id()).orElseThrow();
        if (!input.registrySha256().equals(registry.documentSha256())) { return Outcome.failed("REGISTRY_CHANGED"); }
        if (!input.parserVersion().equals(derivation.parserVersion(input.discovery().capability().provider()))) {
            return Outcome.failed("PARSER_CHANGED");
        }
        var result = collection.collect(input.discovery(), new CalendarExecution() {
            public boolean resumable() { return true; }
            public <T> T atomic(Supplier<T> work) { return jobs.fenced(claim, work); }
            public BudgetActionResult reserve(BudgetCommands.Reserve command) { return budget.reserve(command); }
            public BudgetActionResult authorize(UUID id) { return budget.authorizeSend(id); }
            public BudgetActionResult uncertain(UUID id) { return budget.markUncertain(id); }
        });
        if ("COMPLETED".equals(result.status())) { return Outcome.success(); }
        if ("DEFERRED".equals(result.status())) { return Outcome.retry(result.reasonCode()); }
        return Outcome.failed(result.reasonCode() == null ? "COLLECTION_INCOMPLETE" : result.reasonCode());
    }
}
