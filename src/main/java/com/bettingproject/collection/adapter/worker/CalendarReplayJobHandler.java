package com.bettingproject.collection.adapter.worker;

import com.bettingproject.collection.application.calendar.*;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.operations.application.jobs.*;
import com.bettingproject.operations.domain.JobModel.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("batch-worker")
public class CalendarReplayJobHandler implements JobHandler {
    private final CalendarJobInputStore inputs;
    private final CalendarCollectionStore collections;
    private final CalendarDerivationService derivation;
    private final JobTransactions jobs;
    private final ProviderCapabilityRegistry registry;
    public CalendarReplayJobHandler(CalendarJobInputStore inputs, CalendarCollectionStore collections,
            CalendarDerivationService derivation, JobTransactions jobs, ProviderCapabilityRegistry registry) {
        this.inputs = inputs; this.collections = collections; this.derivation = derivation; this.jobs = jobs; this.registry = registry;
    }
    @Override public Type type() { return Type.REPLAY_NORMALIZATION; }
    @Override public Outcome execute(Claim claim) {
        var input = inputs.find(claim.job().id()).orElseThrow();
        if (!input.registrySha256().equals(registry.documentSha256())) { return Outcome.failed("REGISTRY_CHANGED"); }
        var page = collections.findPage(input.replayPageId()).orElseThrow();
        var collection = collections.findCollection(page.collectionId()).orElseThrow();
        if (!input.parserVersion().equals(derivation.parserVersion(collection.capability().provider()))) {
            return Outcome.failed("PARSER_CHANGED");
        }
        String result = jobs.once(claim, "replay-page:" + page.id(), () -> {
            try {
                var parsed = derivation.parse(collection, page);
                derivation.apply(collection, page, parsed);
                return "APPLIED";
            }
            catch (CalendarPageParseException failure) { return failure.code(); }
        });
        return "APPLIED".equals(result) ? Outcome.success() : Outcome.failed(result);
    }
}
