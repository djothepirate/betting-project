package com.bettingproject.operations.adapter.worker;

import com.bettingproject.operations.application.jobs.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Explicit opt-in; starting batch-worker alone does not dispatch collection jobs. */
@Configuration(proxyBeanMethods = false)
@Profile("batch-worker")
@ConditionalOnProperty(name = "betting.collection.worker.enabled", havingValue = "true")
@EnableScheduling
public class CollectionWorkerLoop {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionWorkerLoop.class);
    private final JobWorker worker;
    public CollectionWorkerLoop(JobWorker worker) { this.worker = worker; }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public void tick() {
        try { worker.tick(); }
        catch (RuntimeException failure) { LOGGER.warn("Collection worker tick failed; code=EXECUTION_ERROR"); }
    }
}
