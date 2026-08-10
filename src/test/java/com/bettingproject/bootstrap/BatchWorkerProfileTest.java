package com.bettingproject.bootstrap;

import com.bettingproject.operations.adapter.worker.BatchWorkerProbe;
import com.bettingproject.operations.application.RuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER"
        })
@ActiveProfiles("batch-worker")
@Import(NoDatabaseTestConfiguration.class)
class BatchWorkerProfileTest {

    @Autowired
    private BatchWorkerProbe workerProbe;

    @Test
    void batchWorkerStartsFromTheMainApplication() {
        assertThat(workerProbe.runtimeProperties().mode()).isEqualTo(RuntimeMode.BATCH_WORKER);
    }
}
