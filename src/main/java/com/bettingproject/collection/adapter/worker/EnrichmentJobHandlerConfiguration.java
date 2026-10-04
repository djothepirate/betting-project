package com.bettingproject.collection.adapter.worker;

import com.bettingproject.collection.application.enrichment.EnrichmentJobExecutor;
import com.bettingproject.operations.application.jobs.JobHandler;
import com.bettingproject.operations.domain.JobModel.Claim;
import com.bettingproject.operations.domain.JobModel.Outcome;
import com.bettingproject.operations.domain.JobModel.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("batch-worker")
public class EnrichmentJobHandlerConfiguration {
    @Bean JobHandler prematchEnrichmentJobHandler(EnrichmentJobExecutor executor) {
        return handler(Type.PREMATCH_ENRICHMENT, executor);
    }
    @Bean JobHandler postmatchEnrichmentJobHandler(EnrichmentJobExecutor executor) {
        return handler(Type.POSTMATCH_ENRICHMENT, executor);
    }
    @Bean JobHandler postmatchRecheckJobHandler(EnrichmentJobExecutor executor) {
        return handler(Type.POSTMATCH_RECHECK, executor);
    }

    private static JobHandler handler(Type type, EnrichmentJobExecutor executor) {
        return new JobHandler() {
            @Override public Type type() { return type; }
            @Override public Outcome execute(Claim claim) { return executor.execute(claim, type); }
        };
    }
}
