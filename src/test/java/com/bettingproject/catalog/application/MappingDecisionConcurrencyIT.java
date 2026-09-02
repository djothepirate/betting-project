package com.bettingproject.catalog.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.ProviderMapping;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                "betting.operator.id=test-operator"
        })
@ActiveProfiles("control-api")
class MappingDecisionConcurrencyIT {

    private static final String PROVIDER = "synthetic-concurrent-decision-provider";

    @Autowired
    private MappingDecisionService service;

    @Autowired
    private CatalogCommandService catalogCommandService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ProviderMappingRepository mappingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        jdbcClient.sql("""
                DELETE FROM provider_mapping_decision_anomaly
                WHERE provider_mapping_decision_id IN (
                    SELECT decision.id
                    FROM provider_mapping_decision decision
                    JOIN provider_mapping mapping
                      ON mapping.id = decision.provider_mapping_id
                    WHERE mapping.provider = :provider
                )
                """)
                .param("provider", PROVIDER)
                .update();
        jdbcClient.sql("""
                DELETE FROM provider_mapping_decision
                WHERE provider_mapping_id IN (
                    SELECT id FROM provider_mapping WHERE provider = :provider
                )
                """)
                .param("provider", PROVIDER)
                .update();
        jdbcClient.sql("""
                DELETE FROM control_command_receipt
                WHERE idempotency_key LIKE 'concurrent-mapping-%%'
                """).update();
        jdbcClient.sql("DELETE FROM provider_mapping WHERE provider = :provider")
                .param("provider", PROVIDER)
                .update();
        jdbcClient.sql("""
                DELETE FROM canonical_team
                WHERE canonical_name LIKE 'CAT002 Concurrent Decision %%'
                """).update();
    }

    @Test
    void twoDecisionsAtTheSameVersionYieldOneWinnerAndOneConflict() throws Exception {
        UUID firstTeam = catalogCommandService.registerTeam(
                "CAT002 Concurrent Decision First", "FRA");
        UUID secondTeam = catalogCommandService.registerTeam(
                "CAT002 Concurrent Decision Second", "FRA");
        ProviderMappingKey key = key("same-version-team");
        MappingDecisionResult initial = service.decide(MappingDecisionCommand.confirm(
                key,
                firstTeam,
                0L,
                "concurrent-mapping-initial",
                "Initial confirmation"));
        assertThat(initial).isInstanceOf(MappingDecisionResult.Applied.class);

        List<MappingDecisionResult> results = runConcurrently(
                () -> service.decide(MappingDecisionCommand.confirm(
                        key,
                        secondTeam,
                        1L,
                        "concurrent-mapping-confirm",
                        "Concurrent confirmation")),
                () -> service.decide(MappingDecisionCommand.reject(
                        key,
                        1L,
                        "concurrent-mapping-reject",
                        "Concurrent rejection")));

        assertThat(results.stream()
                .filter(MappingDecisionResult.Applied.class::isInstance))
                .hasSize(1);
        assertThat(results.stream()
                .filter(MappingDecisionResult.VersionConflict.class::isInstance))
                .hasSize(1);
        MappingDecisionResult.VersionConflict conflict = results.stream()
                .filter(MappingDecisionResult.VersionConflict.class::isInstance)
                .map(MappingDecisionResult.VersionConflict.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(conflict.expectedVersion()).isEqualTo(1);
        assertThat(conflict.actualVersion()).isEqualTo(2);
        assertThat(singleLong("SELECT version FROM provider_mapping")).isEqualTo(2);
        assertThat(count("provider_mapping")).isEqualTo(1);
        assertThat(count("control_command_receipt")).isEqualTo(2);
        assertThat(count("provider_mapping_decision")).isEqualTo(2);
    }

    @Test
    void twoConcurrentRepetitionsOfTheSameKeyReturnAppliedAndAlreadyApplied()
            throws Exception {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "CAT002 Concurrent Decision Idempotent", "FRA");
        ProviderMappingKey key = key("same-idempotency-team");
        MappingDecisionCommand command = MappingDecisionCommand.confirm(
                key,
                canonicalTeamId,
                0L,
                "concurrent-mapping-same-key",
                "Same concurrent command");

        List<MappingDecisionResult> results = runConcurrently(
                () -> service.decide(command),
                () -> service.decide(command));

        assertThat(results.stream()
                .filter(MappingDecisionResult.Applied.class::isInstance))
                .hasSize(1);
        assertThat(results.stream()
                .filter(MappingDecisionResult.AlreadyApplied.class::isInstance))
                .hasSize(1);
        UUID appliedId = results.stream()
                .filter(MappingDecisionResult.Applied.class::isInstance)
                .map(MappingDecisionResult.Applied.class::cast)
                .map(result -> result.decision().id())
                .findFirst()
                .orElseThrow();
        UUID repeatedId = results.stream()
                .filter(MappingDecisionResult.AlreadyApplied.class::isInstance)
                .map(MappingDecisionResult.AlreadyApplied.class::cast)
                .map(result -> result.decision().id())
                .findFirst()
                .orElseThrow();
        assertThat(repeatedId).isEqualTo(appliedId);
        assertThat(count("provider_mapping")).isEqualTo(1);
        assertThat(count("control_command_receipt")).isEqualTo(1);
        assertThat(count("provider_mapping_decision")).isEqualTo(1);
    }

    @Test
    void humanDecisionAndAutomaticInsertionNeverOverwriteEachOther() throws Exception {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "CAT002 Concurrent Decision Automatic", "FRA");
        ProviderMappingKey key = key("human-versus-automatic-team");
        ProviderMapping automaticCandidate = ProviderMapping.ambiguous(
                key.provider(),
                key.entityType(),
                key.providerEntityId(),
                key.season(),
                key.phase(),
                0.5,
                java.time.Instant.parse("2026-09-01T18:00:00Z"));

        ConcurrentPair<MappingDecisionResult, StoredProviderMapping> results =
                runDifferentConcurrently(
                        () -> service.decide(MappingDecisionCommand.confirm(
                                key,
                                canonicalTeamId,
                                0L,
                                "concurrent-mapping-human-automatic",
                                "Human confirmation competing with automatic insertion")),
                        () -> new TransactionTemplate(transactionManager).execute(status ->
                                mappingRepository.insertIfAbsentAndResolve(automaticCandidate)));

        assertThat(count("provider_mapping")).isEqualTo(1);
        assertThat(singleLong("SELECT version FROM provider_mapping")).isEqualTo(1);
        assertThat(results.second()).isNotNull();
        if (results.first() instanceof MappingDecisionResult.Applied) {
            assertThat(results.second().inserted()).isFalse();
            assertThat(singleString("SELECT mapping_status FROM provider_mapping"))
                    .isEqualTo("CONFIRMED");
            assertThat(count("control_command_receipt")).isEqualTo(1);
            assertThat(count("provider_mapping_decision")).isEqualTo(1);
        }
        else {
            assertThat(results.first()).isEqualTo(
                    new MappingDecisionResult.VersionConflict(0, 1L));
            assertThat(results.second().inserted()).isTrue();
            assertThat(singleString("SELECT mapping_status FROM provider_mapping"))
                    .isEqualTo("AMBIGUOUS");
            assertThat(count("control_command_receipt")).isZero();
            assertThat(count("provider_mapping_decision")).isZero();
        }
    }

    private List<MappingDecisionResult> runConcurrently(
            Callable<MappingDecisionResult> first,
            Callable<MappingDecisionResult> second) throws Exception {
        ConcurrentPair<MappingDecisionResult, MappingDecisionResult> results =
                runDifferentConcurrently(first, second);
        return List.of(results.first(), results.second());
    }

    private <F, S> ConcurrentPair<F, S> runDifferentConcurrently(
            Callable<F> first,
            Callable<S> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<F> firstFuture = executor.submit(
                    waitingCall(first, ready, start));
            Future<S> secondFuture = executor.submit(
                    waitingCall(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return new ConcurrentPair<>(
                    firstFuture.get(20, TimeUnit.SECONDS),
                    secondFuture.get(20, TimeUnit.SECONDS));
        }
        finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private <T> Callable<T> waitingCall(
            Callable<T> delegate,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent decision start timed out");
            }
            return delegate.call();
        };
    }

    private ProviderMappingKey key(String providerEntityId) {
        return new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                providerEntityId,
                "2026/2027",
                "REGULAR");
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private long singleLong(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }

    private String singleString(String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }

    private record ConcurrentPair<F, S>(F first, S second) {
    }
}
