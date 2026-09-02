package com.bettingproject.catalog.application;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.bettingproject.BettingProjectApplication;
import com.bettingproject.collection.application.SnapshotStore;
import com.bettingproject.collection.application.StoredSnapshot;
import com.bettingproject.collection.domain.RawSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ControlApiRestartIT {

    private static final String FIXTURE_PATH =
            "/fixtures/cat002/calendar-v3-ordered-neutral.json";

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void pendingReplaySurvivesRealControlApiRestartsWithoutDuplicateEffects()
            throws IOException {
        UUID snapshotId;
        UUID requestId;

        try (ConfigurableApplicationContext firstContext = startContext(
                DeferredAfterCommitConfiguration.class)) {
            byte[] payload = fixture(FIXTURE_PATH);
            RawSnapshot rawSnapshot = RawSnapshot.capture(
                    "synthetic-provider",
                    "calendar/control-api-restart",
                    Instant.parse("2026-09-01T16:00:00Z"),
                    payload,
                    "cat-002-lot8-restart-proof-v1");
            StoredSnapshot stored = firstContext.getBean(SnapshotStore.class)
                    .storeAndResolve(rawSnapshot);
            assertThat(stored.inserted()).isTrue();
            snapshotId = stored.id();

            NormalizationReplayRequestResult result = firstContext
                    .getBean(NormalizationReplayRequestService.class)
                    .request(new NormalizationReplayCommand(
                            new NormalizationReplaySelector.BySnapshotId(snapshotId),
                            "cat002-lot8-control-api-restart"));

            assertThat(result).isInstanceOf(
                    NormalizationReplayRequestResult.Created.class);
            requestId = ((NormalizationReplayRequestResult.Created) result).request().id();

            DeferredAfterCommitExecutor deferredExecutor = firstContext
                    .getBean(DeferredAfterCommitExecutor.class);
            assertThat(deferredExecutor.committedRequestIds())
                    .containsExactly(requestId);

            NormalizationReplayRequestRepository requestRepository = firstContext
                    .getBean(NormalizationReplayRequestRepository.class);
            NormalizationReplayRequest pending = requestRepository.findById(requestId)
                    .orElseThrow();
            JdbcClient jdbcClient = firstContext.getBean(JdbcClient.class);

            assertThat(pending.status()).isEqualTo(NormalizationReplayStatus.PENDING);
            assertThat(pending.version()).isEqualTo(1);
            assertThat(pending.attemptCount()).isZero();
            assertThat(count(jdbcClient, "raw_snapshot")).isEqualTo(1);
            assertThat(count(jdbcClient, "control_command_receipt")).isEqualTo(1);
            assertThat(count(jdbcClient, "normalization_replay_request")).isEqualTo(1);
            assertThat(count(jdbcClient, "normalization_replay_attempt")).isZero();
        }

        ReplayState completedState;
        try (ConfigurableApplicationContext secondContext = startContext()) {
            NormalizationReplayRequestRepository requestRepository = secondContext
                    .getBean(NormalizationReplayRequestRepository.class);
            NormalizationReplayRequest pending = requestRepository.findById(requestId)
                    .orElseThrow();

            assertThat(pending.status()).isEqualTo(NormalizationReplayStatus.PENDING);
            assertThat(pending.rawSnapshotId()).isEqualTo(snapshotId);
            assertThat(pending.attemptCount()).isZero();

            NormalizationReplayExecutionResult resumed = secondContext
                    .getBean(NormalizationReplayExecutionService.class)
                    .resume(requestId, pending.version());

            assertThat(resumed).isInstanceOf(
                    NormalizationReplayExecutionResult.Completed.class);
            NormalizationReplayRequest completed = requestRepository.findById(requestId)
                    .orElseThrow();
            JdbcClient jdbcClient = secondContext.getBean(JdbcClient.class);

            assertThat(completed.status()).isEqualTo(NormalizationReplayStatus.COMPLETED);
            assertThat(completed.attemptCount()).isEqualTo(1);
            assertThat(count(jdbcClient, "raw_snapshot")).isEqualTo(1);
            assertThat(count(jdbcClient, "control_command_receipt")).isEqualTo(1);
            assertThat(count(jdbcClient, "normalization_replay_request")).isEqualTo(1);
            assertThat(count(jdbcClient, "normalization_replay_attempt")).isEqualTo(1);
            assertThat(count(jdbcClient, "fixture_observation")).isEqualTo(1);
            assertThat(count(jdbcClient, "fixture_application_log")).isEqualTo(1);
            assertThat(count(jdbcClient, "normalization_anomaly")).isEqualTo(1);
            assertThat(count(jdbcClient, "normalization_anomaly_event")).isEqualTo(1);
            assertThat(count(jdbcClient,
                    "normalization_replay_attempt_application")).isEqualTo(1);
            assertThat(count(jdbcClient,
                    "normalization_replay_attempt_anomaly_event")).isEqualTo(1);

            assertThat(singleString(jdbcClient, """
                    SELECT outcome
                    FROM normalization_replay_attempt
                    """)).isEqualTo("COMPLETED");
            assertThat(singleString(jdbcClient, """
                    SELECT outcome
                    FROM fixture_application_log
                    """)).isEqualTo("UNASSIGNED");
            assertThat(singleString(jdbcClient, """
                    SELECT anomaly_code
                    FROM normalization_anomaly
                    """)).isEqualTo("UNASSIGNED_AUTHORITY");
            assertThat(singleString(jdbcClient, """
                    SELECT event_type
                    FROM normalization_anomaly_event
                    """)).isEqualTo("OPENED");
            assertThat(singleUuid(jdbcClient, """
                    SELECT replay_application.fixture_application_log_id
                    FROM normalization_replay_attempt_application AS replay_application
                    """)).isEqualTo(singleUuid(jdbcClient, """
                    SELECT id
                    FROM fixture_application_log
                    """));
            assertThat(singleUuid(jdbcClient, """
                    SELECT replay_event.normalization_anomaly_event_id
                    FROM normalization_replay_attempt_anomaly_event AS replay_event
                    """)).isEqualTo(singleUuid(jdbcClient, """
                    SELECT id
                    FROM normalization_anomaly_event
                    """));

            assertThat(count(jdbcClient, "canonical_fixture")).isZero();
            assertThat(count(jdbcClient, "canonical_season")).isZero();
            assertThat(count(jdbcClient, "provider_mapping")).isZero();
            assertThat(count(jdbcClient, "canonical_competition")).isZero();
            assertThat(count(jdbcClient, "canonical_team")).isZero();

            completedState = replayState(jdbcClient);
        }

        try (ConfigurableApplicationContext thirdContext = startContext()) {
            NormalizationReplayRequestRepository requestRepository = thirdContext
                    .getBean(NormalizationReplayRequestRepository.class);
            NormalizationReplayRequest terminal = requestRepository.findById(requestId)
                    .orElseThrow();
            JdbcClient jdbcClient = thirdContext.getBean(JdbcClient.class);

            assertThat(terminal.status()).isEqualTo(NormalizationReplayStatus.COMPLETED);
            assertThat(thirdContext.getBean(NormalizationReplayExecutionService.class)
                    .resume(requestId, terminal.version()))
                    .isInstanceOf(NormalizationReplayExecutionResult.AlreadyTerminal.class);
            assertThat(replayState(jdbcClient)).isEqualTo(completedState);
        }
    }

    private ConfigurableApplicationContext startContext(Class<?>... additionalSources) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(
                BettingProjectApplication.class);
        if (additionalSources.length > 0) {
            builder.sources(additionalSources);
        }
        return builder
                .profiles("control-api")
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run(
                        "--spring.datasource.url=" + POSTGRESQL.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRESQL.getUsername(),
                        "--spring.datasource.password=" + POSTGRESQL.getPassword(),
                        "--BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                        "--betting.operator.id=test-operator");
    }

    private byte[] fixture(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test fixture: " + path);
            }
            return input.readAllBytes();
        }
    }

    private ReplayState replayState(JdbcClient jdbcClient) {
        return new ReplayState(
                count(jdbcClient, "raw_snapshot"),
                count(jdbcClient, "control_command_receipt"),
                count(jdbcClient, "normalization_replay_request"),
                count(jdbcClient, "normalization_replay_attempt"),
                count(jdbcClient, "fixture_observation"),
                count(jdbcClient, "fixture_application_log"),
                count(jdbcClient, "normalization_anomaly"),
                count(jdbcClient, "normalization_anomaly_event"),
                count(jdbcClient, "normalization_replay_attempt_application"),
                count(jdbcClient, "normalization_replay_attempt_anomaly_event"),
                count(jdbcClient, "canonical_fixture"),
                count(jdbcClient, "canonical_season"),
                count(jdbcClient, "provider_mapping"));
    }

    private long count(JdbcClient jdbcClient, String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private String singleString(JdbcClient jdbcClient, String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }

    private UUID singleUuid(JdbcClient jdbcClient, String sql) {
        return jdbcClient.sql(sql).query(UUID.class).single();
    }

    private record ReplayState(
            long snapshots,
            long receipts,
            long requests,
            long attempts,
            long observations,
            long applications,
            long anomalies,
            long anomalyEvents,
            long replayApplications,
            long replayAnomalyEvents,
            long canonicalFixtures,
            long canonicalSeasons,
            long providerMappings) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeferredAfterCommitConfiguration {

        @Bean
        @Primary
        DeferredAfterCommitExecutor deferredAfterCommitExecutor() {
            return new DeferredAfterCommitExecutor();
        }
    }

    static final class DeferredAfterCommitExecutor
            implements NormalizationReplayAfterCommitExecutor {

        private final List<UUID> committedRequestIds = new ArrayList<>();

        @Override
        public void executeAfterCommit(Collection<UUID> requestIds) {
            List<UUID> scheduled = List.copyOf(requestIds);
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException(
                        "Replay execution can only be deferred from an active transaction");
            }
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            committedRequestIds.addAll(scheduled);
                        }
                    });
        }

        List<UUID> committedRequestIds() {
            return List.copyOf(committedRequestIds);
        }
    }
}
