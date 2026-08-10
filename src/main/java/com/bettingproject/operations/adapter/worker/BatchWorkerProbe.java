package com.bettingproject.operations.adapter.worker;

import com.bettingproject.operations.application.RuntimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("batch-worker")
public class BatchWorkerProbe implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchWorkerProbe.class);

    private final RuntimeProperties runtimeProperties;

    public BatchWorkerProbe(RuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("Batch worker profile ready; mode={}", runtimeProperties.mode());
    }

    public RuntimeProperties runtimeProperties() {
        return runtimeProperties;
    }
}
