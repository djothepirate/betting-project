package com.bettingproject.operations.adapter.worker;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.bettingproject.operations.application.jobs.JobWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.*;

class CollectionWorkerLoopTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test void loopIsAbsentWithoutExplicitOptIn() {
        runner.withPropertyValues("spring.profiles.active=batch-worker").run(context ->
                assertThat(context.getBeansOfType(CollectionWorkerLoop.class)).isEmpty());
    }
    @Test void controlApiDoesNotStartLoopEvenWithTheFlag() {
        runner.withPropertyValues("spring.profiles.active=control-api", "betting.collection.worker.enabled=true")
                .run(context -> assertThat(context.getBeansOfType(CollectionWorkerLoop.class)).isEmpty());
    }
    @Test void replayDoesNotStartLoopEvenWithTheFlag() {
        runner.withPropertyValues("spring.profiles.active=replay", "betting.collection.worker.enabled=true")
                .run(context -> assertThat(context.getBeansOfType(CollectionWorkerLoop.class)).isEmpty());
    }
    @Test void enabledBatchLoopActuallyInvokesBoundedTicks() {
        runner.withPropertyValues("spring.profiles.active=batch-worker", "betting.collection.worker.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(CollectionWorkerLoop.class)).hasSize(1);
                    assertThat(context.getBean(StubWorker.class).tick.await(5, TimeUnit.SECONDS)).isTrue();
                });
    }
    @Configuration(proxyBeanMethods = false) @Import(CollectionWorkerLoop.class)
    static class Config { @Bean StubWorker worker() { return new StubWorker(); } }
    static class StubWorker extends JobWorker {
        final CountDownLatch tick = new CountDownLatch(1);
        StubWorker() { super(null, List.of()); }
        @Override public boolean tick() { tick.countDown(); return false; }
    }
}
