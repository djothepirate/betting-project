package com.bettingproject.collection.application.budget;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import com.bettingproject.BettingProjectApplication;
import com.bettingproject.collection.domain.budget.BudgetModel.Intent;
import com.bettingproject.collection.domain.budget.BudgetModel.IntentState;
import com.bettingproject.collection.domain.budget.BudgetModel.ObservationDisposition;
import com.bettingproject.collection.domain.budget.BudgetModel.Proof;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;
import com.bettingproject.collection.domain.budget.BudgetModel.Window;
import com.bettingproject.collection.domain.budget.BudgetModel.WindowState;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real transactions: deliberately no transaction enclosing a test or an authorization. */
@Testcontainers
class ProviderBudgetServiceIT {
    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final String SHA256 = "a".repeat(64);
    private static final Proof PROOF = new Proof("synthetic-budget-proof-v1", SHA256);

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    private static ConfigurableApplicationContext context;
    private static ProviderBudgetService service;
    private static ProviderBudgetAdministration administration;
    private static ProviderBudgetRepository repository;
    private static JdbcClient jdbc;
    private static BudgetTestClock clock;

    @BeforeAll
    static void start() {
        openContext();
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetClock() {
        clock.set(NOW);
    }

    @Test
    void permitsEightyReservationsFromOneHundredWithoutSubtractingTheReserveTwice() {
        UUID window = initialize(0, 80);
        List<Intent> reservations = new ArrayList<>();
        for (int index = 0; index < 80; index++) {
            reservations.add(reserve(window));
        }
        assertThat(service.availability(window).code()).isEqualTo(ResultCode.EXHAUSTED);
        assertThat(service.reserve(command(window)).code()).isEqualTo(ResultCode.EXHAUSTED);
        assertThat(repository.findWindowIntents(window)).hasSize(80);

        // A reservation owns its unit even when no unreserved unit remains.
        assertThat(service.authorizeSend(reservations.get(79).id()).code()).isEqualTo(ResultCode.OK);
        assertThat(service.availability(window).available()).isZero();
    }

    @Test
    void includesTwelveExternalCallsAtInitializationWithoutInventingTwelveLocalIntents() {
        UUID window = initialize(12, 80);
        assertThat(service.availability(window).available()).isEqualTo(68);
        assertThat(repository.findWindowIntents(window)).isEmpty();
        assertThat(repository.findWindow(window).orElseThrow().sharedInitial()).isEqualTo(12);
        reserve(window);
        assertThat(service.availability(window).available()).isEqualTo(67);
    }

    @Test
    void oneOfTwoConcurrentTransactionsWinsTheLastUnit() throws Exception {
        UUID window = initialize(0, 1);
        List<BudgetActionResult> results = race(
                () -> service.reserve(command(window)),
                () -> service.reserve(command(window)));
        assertThat(results).extracting(BudgetActionResult::code)
                .containsExactlyInAnyOrder(ResultCode.OK, ResultCode.EXHAUSTED);
        assertThat(repository.findWindowIntents(window)).hasSize(1);
        assertThat(service.availability(window).available()).isZero();
    }

    @Test
    void reservationWaitingForTheScopeLockRechecksTimeAfterTheWindowExpires() throws Exception {
        UUID window = initialize(0, 80);
        Window definition = repository.findWindow(window).orElseThrow();
        BudgetCommands.Reserve command = command(window);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch unlock = new CountDownLatch(1);
        AtomicInteger blockingPid = new AtomicInteger();
        TransactionTemplate transaction = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var blocker = executor.submit(() -> transaction.executeWithoutResult(status -> {
                repository.lockScope(definition.scopeId());
                blockingPid.set(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single());
                locked.countDown();
                try {
                    if (!unlock.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Budget lock release timed out");
                    }
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Budget lock holder interrupted", exception);
                }
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var reservation = executor.submit(() -> service.reserve(command));
                awaitBlockedBy(blockingPid.get());
                clock.set(definition.endsAt());
                unlock.countDown();
                assertThat(reservation.get(10, TimeUnit.SECONDS).code()).isEqualTo(ResultCode.OUTSIDE_WINDOW);
                blocker.get(10, TimeUnit.SECONDS);
            }
            finally {
                unlock.countDown();
            }
        }
        assertThat(repository.findIntentByKey(command.idempotencyKey())).isEmpty();
        assertThat(repository.findWindowIntents(window)).isEmpty();
        assertThat(repository.findWindow(window)).contains(definition);
    }

    @Test
    void concurrentIdenticalReservationsHaveOneIdentityAndOneDebit() throws Exception {
        UUID window = initialize(0, 80);
        BudgetCommands.Reserve command = command(window);
        List<BudgetActionResult> results = race(
                () -> service.reserve(command), () -> service.reserve(command));
        assertThat(results).allSatisfy(result -> assertThat(result.code()).isEqualTo(ResultCode.OK));
        assertThat(results).extracting(result -> result.intent().id()).containsOnly(results.get(0).intent().id());
        assertThat(results.stream().filter(BudgetActionResult::created).count()).isEqualTo(1);
        assertThat(repository.findWindowIntents(window)).hasSize(1);
        assertThat(service.availability(window).available()).isEqualTo(79);
    }

    @Test
    void contentCollisionNeverChangesTheOriginalIntentOrDebitsAnotherWindow() {
        UUID firstWindow = initialize(0, 80);
        UUID secondWindow = initialize(0, 80);
        BudgetCommands.Reserve command = command(firstWindow);
        Intent original = service.reserve(command).intent();

        assertThat(service.reserve(new BudgetCommands.Reserve(firstWindow,
                command.idempotencyKey(), "different-endpoint", SHA256)).code())
                .isEqualTo(ResultCode.IDEMPOTENCY_CONFLICT);
        assertThat(service.reserve(new BudgetCommands.Reserve(secondWindow,
                command.idempotencyKey(), command.logicalEndpoint(), command.requestSha256())).code())
                .isEqualTo(ResultCode.IDEMPOTENCY_CONFLICT);
        assertThat(service.findIntent(original.id())).contains(original);
        assertThat(service.availability(firstWindow).available()).isEqualTo(79);
        assertThat(service.availability(secondWindow).available()).isEqualTo(80);
    }

    @Test
    void exactlyOneConcurrentAuthorizationGrantsTheRightToSend() throws Exception {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        List<BudgetActionResult> results = race(
                () -> service.authorizeSend(intent.id()), () -> service.authorizeSend(intent.id()));
        assertThat(results).extracting(BudgetActionResult::code)
                .containsExactlyInAnyOrder(ResultCode.OK, ResultCode.ALREADY_COMMITTED);
        assertThat(service.findIntent(intent.id()).orElseThrow().state())
                .isEqualTo(IntentState.COMMITTED_FOR_SEND);
        assertThat(service.availability(window).available()).isEqualTo(79);
    }

    @Test
    void authorizationCannotBeNestedInAnUncommittedCallerTransaction() {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        TransactionTemplate transaction = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> transaction.executeWithoutResult(
                status -> service.authorizeSend(intent.id())))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(service.findIntent(intent.id())).contains(intent);
    }

    @Test
    void releaseRacingAuthorizationHasOnlyOneLegalWinner() throws Exception {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        List<BudgetActionResult> results = race(
                () -> service.release(intent.id()), () -> service.authorizeSend(intent.id()));
        assertThat(results.stream().filter(result -> result.code() == ResultCode.OK).count()).isEqualTo(1);
        Intent persisted = service.findIntent(intent.id()).orElseThrow();
        assertThat(persisted.state()).isIn(IntentState.RELEASED, IntentState.COMMITTED_FOR_SEND);
        assertThat(service.availability(window).available())
                .isEqualTo(persisted.state() == IntentState.RELEASED ? 80 : 79);
        assertThat(results.stream().filter(result -> result.code() != ResultCode.OK))
                .allSatisfy(result -> assertThat(result.code())
                        .isIn(ResultCode.INVALID_TRANSITION, ResultCode.ALREADY_COMMITTED));
    }

    @Test
    void responseAndDuplicateResponseDoNotDebitTheSameAttemptAgain() {
        UUID window = initialize(0, 80);
        Intent first = reserve(window);
        reserve(window);
        assertThat(service.authorizeSend(first.id()).code()).isEqualTo(ResultCode.OK);
        clock.set(NOW.plusSeconds(1));
        BudgetCommands.Outcome outcome = new BudgetCommands.Outcome(first.id(), 200, SHA256,
                reading(99, Set.of(first.id())));
        assertThat(service.recordOutcome(outcome).code()).isEqualTo(ResultCode.OK);
        assertThat(service.recordOutcome(outcome).code()).isEqualTo(ResultCode.OK);
        assertThat(service.recordOutcome(new BudgetCommands.Outcome(first.id(), 200, SHA256, null)).code())
                .isEqualTo(ResultCode.IDEMPOTENCY_CONFLICT);
        assertThat(service.recordOutcome(new BudgetCommands.Outcome(first.id(), 200, SHA256,
                reading(99, Set.of(first.id())))).code()).isEqualTo(ResultCode.IDEMPOTENCY_CONFLICT);
        assertThat(service.availability(window).available()).isEqualTo(78);
        assertThat(service.findIntent(first.id()).orElseThrow().state()).isEqualTo(IntentState.RESULT_RECORDED);
        assertThat(repository.findWindowIntents(window)).hasSize(2);
        assertThat(service.release(first.id()).code()).isEqualTo(ResultCode.INVALID_TRANSITION);
    }

    @Test
    void uncertainAttemptRetainsCostAndOnlyBlocksItsOwnResend() {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        assertThat(service.authorizeSend(intent.id()).code()).isEqualTo(ResultCode.OK);
        assertThat(service.markUncertain(intent.id()).code()).isEqualTo(ResultCode.OK);
        assertThat(service.authorizeSend(intent.id()).code()).isEqualTo(ResultCode.ALREADY_COMMITTED);
        assertThat(service.release(intent.id()).code()).isEqualTo(ResultCode.INVALID_TRANSITION);
        assertThat(service.availability(window).available()).isEqualTo(79);
        assertThat(service.incidents(window)).extracting(incident -> incident.code()).containsExactly("UNCERTAIN_SEND");
        Intent other = reserve(window);
        assertThat(service.authorizeSend(other.id()).code()).isEqualTo(ResultCode.OK);
        assertThat(repository.findWindow(window).orElseThrow().state()).isEqualTo(WindowState.ACTIVE);
    }

    @Test
    void explicitReconciliationKeepsTheCostAndRequiresTheCurrentVersion() {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        service.authorizeSend(intent.id());
        service.markUncertain(intent.id());
        Intent uncertain = service.findIntent(intent.id()).orElseThrow();
        assertThat(administration.reconcile(new BudgetCommands.Reconcile(intent.id(),
                uncertain.version() - 1, PROOF, "Synthetic reconciliation proof")).code())
                .isEqualTo(ResultCode.VERSION_CONFLICT);
        assertThat(administration.reconcile(new BudgetCommands.Reconcile(intent.id(),
                uncertain.version(), PROOF, "Synthetic reconciliation proof")).code())
                .isEqualTo(ResultCode.OK);
        assertThat(service.findIntent(intent.id()).orElseThrow().state()).isEqualTo(IntentState.RECONCILED);
        assertThat(service.availability(window).available()).isEqualTo(79);
        assertThat(service.authorizeSend(intent.id()).code()).isEqualTo(ResultCode.ALREADY_COMMITTED);
    }

    @Test
    void concurrentInitializationCannotCreateOverlappingWindowsForTheSameAccount() throws Exception {
        String account = "synthetic-account-" + UUID.randomUUID();
        BudgetCommands.Initialize first = initialization(UUID.randomUUID(), account, 0, 80);
        BudgetCommands.Initialize second = initialization(UUID.randomUUID(), account, 0, 80);
        List<BudgetActionResult> results = race(
                () -> administration.initialize(first), () -> administration.initialize(second));
        assertThat(results.stream().filter(result -> result.code() == ResultCode.OK).count()).isEqualTo(1);
        UUID winner = results.stream().filter(result -> result.code() == ResultCode.OK)
                .findFirst().orElseThrow().windowId();
        Window window = repository.findWindow(winner).orElseThrow();
        assertThat(repository.findWindows(window.scopeId())).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"startsAt", "endsAt", "observedAt", "validUntil"})
    void initializationRejectsSubMicrosecondEvidenceWithoutCreatingAScope(String field) {
        UUID window = UUID.randomUUID();
        BudgetCommands.Initialize initial = initialization(window, "synthetic-account-" + UUID.randomUUID(), 0, 80);
        BudgetCommands.Initialize invalid = initializationTimes(initial,
                field.equals("startsAt") ? initial.startsAt().plusNanos(1) : initial.startsAt(),
                field.equals("endsAt") ? initial.endsAt().plusNanos(1) : initial.endsAt(),
                field.equals("observedAt") ? NOW.minusSeconds(1).plusNanos(1) : initial.observedAt(),
                field.equals("validUntil") ? initial.validUntil().minusNanos(1) : initial.validUntil());
        assertThat(administration.initialize(invalid).code()).isEqualTo(ResultCode.INVALID_COMMAND);
        assertThat(repository.findWindow(window)).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM provider_budget_scope WHERE provider = :provider AND account_ref = :account")
                .param("provider", initial.provider()).param("account", initial.accountRef())
                .query(Long.class).single()).isZero();
    }

    @Test
    void microsecondEvidenceRoundTripsExactlyAndInitializationRemainsIdempotent() {
        UUID window = UUID.randomUUID();
        BudgetCommands.Initialize initial = initialization(window, "synthetic-account-" + UUID.randomUUID(), 0, 80);
        BudgetCommands.Initialize precise = initializationTimes(initial,
                initial.startsAt().plusNanos(123_000), initial.endsAt().plusNanos(456_000),
                NOW.minusSeconds(1).plusNanos(789_000), initial.validUntil().plusNanos(123_000));
        BudgetActionResult created = administration.initialize(precise);
        assertThat(created.code()).isEqualTo(ResultCode.OK);
        assertThat(created.created()).isTrue();
        Window persisted = repository.findWindow(window).orElseThrow();
        var observation = repository.findObservation(persisted.currentObservationId()).orElseThrow();
        long events = countForWindow("provider_budget_event", window);
        assertThat(persisted.startsAt()).isEqualTo(precise.startsAt());
        assertThat(persisted.endsAt()).isEqualTo(precise.endsAt());
        assertThat(observation.observedAt()).isEqualTo(precise.observedAt());
        assertThat(observation.validUntil()).isEqualTo(precise.validUntil());
        BudgetActionResult duplicate = administration.initialize(precise);
        assertThat(duplicate.code()).isEqualTo(ResultCode.OK);
        assertThat(duplicate.created()).isFalse();
        assertThat(repository.findWindow(window)).contains(persisted);
        assertThat(repository.findObservation(observation.id())).contains(observation);
        assertThat(countForWindow("provider_budget_event", window)).isEqualTo(events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"observedAt", "validUntil"})
    void quotaEvidenceWithSubMicrosecondPrecisionIsRejectedWithoutAnyWrite(String field) {
        UUID window = initialize(0, 80);
        Window original = repository.findWindow(window).orElseThrow();
        long events = countForWindow("provider_budget_event", window);
        clock.set(NOW.plusSeconds(1));
        BudgetCommands.QuotaReading invalid = new BudgetCommands.QuotaReading(UUID.randomUUID(), 100,
                field.equals("observedAt") ? NOW.plusNanos(1) : NOW,
                field.equals("validUntil") ? NOW.plus(Duration.ofHours(1)).plusNanos(1)
                        : NOW.plus(Duration.ofHours(1)), PROOF, Set.of());
        assertThat(service.observeQuota(window, invalid).code()).isEqualTo(ResultCode.INVALID_COMMAND);
        assertThat(repository.findObservation(invalid.id())).isEmpty();
        assertThat(repository.findWindow(window)).contains(original);
        assertThat(service.incidents(window)).isEmpty();
        assertThat(countForWindow("provider_budget_event", window)).isEqualTo(events);
    }

    @Test
    void delayedObservationCannotReplaceTheAcceptedObservationOrInventAReset() {
        UUID window = initialize(0, 80);
        Intent first = reserve(window);
        service.authorizeSend(first.id());
        clock.set(NOW.plusSeconds(2));
        BudgetCommands.QuotaReading accepted = reading(99, Set.of(first.id()));
        assertThat(service.recordOutcome(new BudgetCommands.Outcome(first.id(), 200, SHA256, accepted)).code())
                .isEqualTo(ResultCode.OK);
        clock.set(NOW.plusSeconds(3));
        BudgetCommands.QuotaReading delayed = new BudgetCommands.QuotaReading(UUID.randomUUID(), 100,
                NOW.plusSeconds(1), NOW.plus(Duration.ofHours(12)), PROOF, Set.of());
        assertThat(service.observeQuota(window, delayed).code()).isEqualTo(ResultCode.OK);
        assertThat(repository.findWindow(window).orElseThrow().currentObservationId()).isEqualTo(accepted.id());
        assertThat(repository.findObservation(delayed.id()).orElseThrow().disposition())
                .isEqualTo(ObservationDisposition.STALE);
        assertThat(service.availability(window).available()).isEqualTo(79);
    }

    @Test
    void outOfOrderResultsUseOnlyExplicitCoverageAndNeverDebitTheCallsAgain() {
        UUID window = initialize(0, 80);
        Intent first = reserve(window);
        Intent second = reserve(window);
        service.authorizeSend(first.id());
        service.authorizeSend(second.id());
        clock.set(NOW.plusSeconds(2));
        BudgetCommands.QuotaReading newest = reading(98, Set.of(first.id(), second.id()));
        assertThat(service.recordOutcome(new BudgetCommands.Outcome(second.id(), 200, SHA256, newest)).code())
                .isEqualTo(ResultCode.OK);
        assertThat(service.availability(window).available()).isEqualTo(78);
        clock.set(NOW.plusSeconds(3));
        BudgetCommands.QuotaReading older = new BudgetCommands.QuotaReading(UUID.randomUUID(), 99,
                NOW.plusSeconds(1), NOW.plus(Duration.ofDays(1)), PROOF, Set.of(first.id()));
        assertThat(service.recordOutcome(new BudgetCommands.Outcome(first.id(), 200, SHA256, older)).code())
                .isEqualTo(ResultCode.OK);
        assertThat(repository.findWindow(window).orElseThrow().currentObservationId()).isEqualTo(newest.id());
        assertThat(repository.findObservation(older.id()).orElseThrow().disposition())
                .isEqualTo(ObservationDisposition.STALE);
        assertThat(service.availability(window).available()).isEqualTo(78);
        assertThat(repository.findWindowIntents(window)).allSatisfy(
                intent -> assertThat(intent.state()).isEqualTo(IntentState.RESULT_RECORDED));
    }

    @Test
    void invalidQuotaEvidenceRollsBackTheResultAsWellAsItsObservation() {
        UUID window = initialize(0, 80);
        Intent sent = reserve(window);
        Intent unsent = reserve(window);
        service.authorizeSend(sent.id());
        Intent committed = service.findIntent(sent.id()).orElseThrow();
        Window originalWindow = repository.findWindow(window).orElseThrow();
        clock.set(NOW.plusSeconds(1));
        BudgetCommands.QuotaReading invalid = reading(99, Set.of(sent.id(), unsent.id()));
        assertThat(service.recordOutcome(new BudgetCommands.Outcome(sent.id(), 200, SHA256, invalid)).code())
                .isEqualTo(ResultCode.INVALID_COMMAND);
        assertThat(service.findIntent(sent.id())).contains(committed);
        assertThat(repository.findWindow(window)).contains(originalWindow);
        assertThat(repository.findObservation(invalid.id())).isEmpty();
        assertThat(service.availability(window).available()).isEqualTo(78);
    }

    @Test
    void increasedCounterClosesAvailabilityUntilExplicitQuotaReconciliation() {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        service.authorizeSend(intent.id());
        clock.set(NOW.plusSeconds(1));
        service.recordOutcome(new BudgetCommands.Outcome(intent.id(), 200, SHA256, reading(99, Set.of(intent.id()))));
        clock.set(NOW.plusSeconds(2));
        assertThat(service.observeQuota(window, reading(100, Set.of(intent.id()))).code())
                .isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
        assertThat(service.availability(window).code()).isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
        Window inconsistent = repository.findWindow(window).orElseThrow();
        assertThat(inconsistent.startsAt()).isEqualTo(NOW.minus(Duration.ofHours(1)));
        assertThat(administration.reconcileQuota(new BudgetCommands.ReconcileQuota(window,
                inconsistent.version(), reading(99, Set.of(intent.id())), "Synthetic counter reconciliation")).code())
                .isEqualTo(ResultCode.OK);
        assertThat(service.availability(window).available()).isEqualTo(79);
    }

    @Test
    void repeatingContradictoryObservationPreservesItsRefusalAndRequiresNewIdentityForReconciliation() {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        service.authorizeSend(intent.id());
        clock.set(NOW.plusSeconds(1));
        BudgetCommands.QuotaReading accepted = reading(99, Set.of(intent.id()));
        assertThat(service.recordOutcome(new BudgetCommands.Outcome(intent.id(), 200, SHA256, accepted)).code())
                .isEqualTo(ResultCode.OK);
        clock.set(NOW.plusSeconds(2));
        BudgetCommands.QuotaReading contradictory = reading(100, Set.of(intent.id()));
        assertThat(service.observeQuota(window, contradictory).code()).isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
        Window inconsistent = repository.findWindow(window).orElseThrow();
        long events = countForWindow("provider_budget_event", window);
        long incidents = countForWindow("provider_budget_incident", window);
        assertThat(service.observeQuota(window, contradictory).code()).isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
        assertThat(repository.findWindow(window)).contains(inconsistent);
        assertThat(countForWindow("provider_budget_event", window)).isEqualTo(events);
        assertThat(countForWindow("provider_budget_incident", window)).isEqualTo(incidents);
        assertThat(administration.reconcileQuota(new BudgetCommands.ReconcileQuota(window,
                inconsistent.version(), contradictory, "Synthetic reused proof rejection")).code())
                .isEqualTo(ResultCode.IDEMPOTENCY_CONFLICT);
        BudgetCommands.QuotaReading conflictingReading = new BudgetCommands.QuotaReading(
                contradictory.id(), 99, contradictory.observedAt(), contradictory.validUntil(),
                contradictory.proof(), contradictory.coveredIntentIds());
        assertThat(administration.reconcileQuota(new BudgetCommands.ReconcileQuota(window,
                inconsistent.version(), conflictingReading, "Synthetic conflicting proof rejection")).code())
                .isEqualTo(ResultCode.IDEMPOTENCY_CONFLICT);
        assertThat(repository.findWindow(window)).contains(inconsistent);
        assertThat(countForWindow("provider_budget_event", window)).isEqualTo(events);
        assertThat(countForWindow("provider_budget_incident", window)).isEqualTo(incidents);
        assertThat(repository.findObservation(contradictory.id()).orElseThrow().disposition())
                .isEqualTo(ObservationDisposition.CONTRADICTORY);
        assertThat(service.availability(window).code()).isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
        assertThat(administration.reconcileQuota(new BudgetCommands.ReconcileQuota(window,
                inconsistent.version(), reading(99, Set.of(intent.id())), "Synthetic new proof reconciliation")).code())
                .isEqualTo(ResultCode.OK);
        assertThat(service.availability(window).available()).isEqualTo(79);
    }

    @Test
    void quotaCoverageCannotClaimAnUnsentOrForeignIntent() {
        UUID window = initialize(0, 80);
        Intent unsent = reserve(window);
        UUID foreignWindow = initialize(0, 80);
        Intent foreign = reserve(foreignWindow);
        service.authorizeSend(foreign.id());
        assertThat(service.observeQuota(window, reading(99, Set.of(unsent.id()))).code())
                .isIn(ResultCode.INVALID_COMMAND, ResultCode.CONTRADICTORY_OBSERVATION);
        assertThat(service.observeQuota(window, reading(99, Set.of(foreign.id()))).code())
                .isIn(ResultCode.INVALID_COMMAND, ResultCode.CONTRADICTORY_OBSERVATION);
        assertThat(service.findIntent(unsent.id()).orElseThrow().state()).isEqualTo(IntentState.RESERVED);
        assertThat(service.availability(foreignWindow).available()).isEqualTo(79);
    }

    @Test
    void expiryAndCivilMidnightNeverCreateAnotherBudgetWindow() {
        UUID window = initialize(0, 80);
        reserve(window);
        clock.set(Instant.parse("2026-09-07T00:00:00Z"));
        assertThat(service.availability(window).available()).isEqualTo(79);
        clock.set(NOW.plus(Duration.ofDays(1)));
        assertThat(service.availability(window).code()).isEqualTo(ResultCode.OUTSIDE_WINDOW);
        Window persisted = repository.findWindow(window).orElseThrow();
        assertThat(repository.findWindows(persisted.scopeId())).containsExactly(persisted);
        assertThat(repository.findWindowIntents(window)).hasSize(1);
    }

    @Test
    void expiredObservationAndUninitializedWindowRefuseWithoutCreatingAnIntent() {
        UUID window = UUID.randomUUID();
        BudgetCommands.Initialize command = initialization(window, "synthetic-account-" + UUID.randomUUID(), 0, 80);
        assertThat(administration.initialize(new BudgetCommands.Initialize(window, command.provider(),
                command.accountRef(), command.startsAt(), command.endsAt(), command.capacity(),
                command.projectLimit(), command.reserve(), command.sharedInitial(), command.projectInitial(),
                null, null, command.initialRemaining(), NOW, NOW.plusSeconds(1), PROOF,
                command.justification())).code()).isEqualTo(ResultCode.OK);
        clock.set(NOW.plusSeconds(1));
        assertThat(service.reserve(command(window)).code()).isEqualTo(ResultCode.STALE_OBSERVATION);
        assertThat(repository.findWindowIntents(window)).isEmpty();
        assertThat(service.availability(UUID.randomUUID()).code()).isEqualTo(ResultCode.WINDOW_UNINITIALIZED);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429})
    void authenticationAuthorizationAndQuotaSuspendOnlyTheAffectedProvider(int status) {
        UUID affected = initialize(0, 80);
        UUID other = UUID.randomUUID();
        BudgetCommands.Initialize otherCommand = initialization(other, "synthetic-account-" + UUID.randomUUID(), 0, 80);
        assertThat(administration.initialize(new BudgetCommands.Initialize(other, "synthetic-other-provider",
                otherCommand.accountRef(), otherCommand.startsAt(), otherCommand.endsAt(), 100L, 80,
                20, 0, 0, null, null, 100L, NOW, otherCommand.endsAt(), PROOF,
                otherCommand.justification())).code()).isEqualTo(ResultCode.OK);
        Intent failed = reserve(affected);
        Intent pending = reserve(affected);
        service.authorizeSend(failed.id());
        BudgetCommands.Outcome outcome = new BudgetCommands.Outcome(failed.id(), status, SHA256, null);
        assertThat(service.recordOutcome(outcome).code()).isEqualTo(ResultCode.OK);
        assertThat(service.recordOutcome(outcome).code()).isEqualTo(ResultCode.OK);
        assertThat(service.incidents(affected)).hasSize(1);
        assertThat(service.incidents(affected).get(0).intentId()).isEqualTo(failed.id());
        assertThat(service.authorizeSend(pending.id()).code()).isEqualTo(ResultCode.SUSPENDED);
        assertThat(service.findIntent(pending.id()).orElseThrow().state()).isEqualTo(IntentState.RESERVED);
        assertThat(service.availability(affected).code()).isEqualTo(ResultCode.SUSPENDED);
        assertThat(service.availability(other).available()).isEqualTo(80);
        assertThat(service.incidents(other)).isEmpty();
        assertThat(service.authorizeSend(reserve(other).id()).code()).isEqualTo(ResultCode.OK);
    }

    @Test
    void failedEventInsertRollsBackReservationAndAllBudgetEffects() {
        UUID window = initialize(0, 80);
        BudgetCommands.Reserve command = command(window);
        long before = countForWindow("provider_budget_event", window);
        failInserts("provider_budget_event", window);
        try {
            assertThatThrownBy(() -> service.reserve(command)).isInstanceOf(DataAccessException.class);
        }
        finally {
            removeFailureTrigger("provider_budget_event");
        }
        assertThat(repository.findIntentByKey(command.idempotencyKey())).isEmpty();
        assertThat(countForWindow("provider_budget_event", window)).isEqualTo(before);
        assertThat(service.availability(window).available()).isEqualTo(80);
        assertThat(service.reserve(command).created()).isTrue();
    }

    @Test
    void failedIncidentInsertRollsBackResultAndSuspensionTogether() {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        service.authorizeSend(intent.id());
        Intent committed = service.findIntent(intent.id()).orElseThrow();
        Window active = repository.findWindow(window).orElseThrow();
        long before = countForWindow("provider_budget_event", window);
        BudgetCommands.Outcome outcome = new BudgetCommands.Outcome(intent.id(), 429, SHA256, null);
        failInserts("provider_budget_incident", window);
        try {
            assertThatThrownBy(() -> service.recordOutcome(outcome)).isInstanceOf(DataAccessException.class);
        }
        finally {
            removeFailureTrigger("provider_budget_incident");
        }
        assertThat(service.findIntent(intent.id())).contains(committed);
        assertThat(repository.findWindow(window)).contains(active);
        assertThat(service.incidents(window)).isEmpty();
        assertThat(countForWindow("provider_budget_event", window)).isEqualTo(before);
        assertThat(service.recordOutcome(outcome).code()).isEqualTo(ResultCode.OK);
        assertThat(service.incidents(window)).hasSize(1);
    }

    @Test
    void failedResultEventRollsBackAnIncidentAlreadyInsertedInTheSameTransaction() {
        UUID window = initialize(0, 80);
        Intent intent = reserve(window);
        service.authorizeSend(intent.id());
        Intent committed = service.findIntent(intent.id()).orElseThrow();
        Window active = repository.findWindow(window).orElseThrow();
        long before = countForWindow("provider_budget_event", window);
        failInserts("provider_budget_event", window);
        try {
            assertThatThrownBy(() -> service.recordOutcome(new BudgetCommands.Outcome(
                    intent.id(), 403, SHA256, null))).isInstanceOf(DataAccessException.class);
        }
        finally {
            removeFailureTrigger("provider_budget_event");
        }
        assertThat(service.findIntent(intent.id())).contains(committed);
        assertThat(repository.findWindow(window)).contains(active);
        assertThat(service.incidents(window)).isEmpty();
        assertThat(countForWindow("provider_budget_event", window)).isEqualTo(before);
        assertThat(service.availability(window).available()).isEqualTo(79);
    }

    @Test
    void cadenceControlsSendSeparatelyFromProjectReservationsAndRetainsUncertainty() {
        UUID window = UUID.randomUUID();
        assertThat(administration.initialize(new BudgetCommands.Initialize(window, "synthetic-cadence-provider",
                "synthetic-account-" + UUID.randomUUID(), NOW.minusSeconds(1), NOW.plus(Duration.ofDays(1)),
                null, 10, 0, 0, 0, 1, Duration.ofMinutes(1), null, null, null,
                PROOF, "Synthetic cadence-only provider")).code()).isEqualTo(ResultCode.OK);
        Intent first = reserve(window);
        Intent second = reserve(window);
        assertThat(service.availability(window).available()).isEqualTo(8);
        assertThat(service.authorizeSend(first.id()).code()).isEqualTo(ResultCode.OK);
        BudgetActionResult unresolvedRefusal = service.authorizeSend(second.id());
        assertThat(unresolvedRefusal.code()).isEqualTo(ResultCode.RATE_LIMITED);
        assertThat(unresolvedRefusal.retryAt()).isNull();
        service.markUncertain(first.id());
        clock.set(NOW.plus(Duration.ofMinutes(2)));
        assertThat(service.authorizeSend(second.id()).code()).isEqualTo(ResultCode.RATE_LIMITED);
        Intent uncertain = service.findIntent(first.id()).orElseThrow();
        assertThat(administration.reconcile(new BudgetCommands.Reconcile(first.id(), uncertain.version(),
                PROOF, "Synthetic explicit cadence reconciliation")).code()).isEqualTo(ResultCode.OK);
        BudgetActionResult reconciledRefusal = service.authorizeSend(second.id());
        assertThat(reconciledRefusal.code()).isEqualTo(ResultCode.RATE_LIMITED);
        assertThat(reconciledRefusal.retryAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
        clock.set(NOW.plus(Duration.ofMinutes(3)));
        assertThat(service.authorizeSend(second.id()).code()).isEqualTo(ResultCode.OK);
        assertThat(service.availability(window).available()).isEqualTo(8);
    }

    @Test
    void committedAttemptAndBudgetSurviveActualContextAndPoolClosure() {
        UUID window = initialize(0, 80);
        BudgetCommands.Reserve command = command(window);
        Intent intent = service.reserve(command).intent();
        assertThat(service.authorizeSend(intent.id()).code()).isEqualTo(ResultCode.OK);
        Intent committed = service.findIntent(intent.id()).orElseThrow();
        long events = countForWindow("provider_budget_event", window);
        HikariDataSource firstPool = context.getBean(HikariDataSource.class);
        context.close();
        assertThat(firstPool.isClosed()).isTrue();
        openContext();

        assertThat(context.getBean(HikariDataSource.class)).isNotSameAs(firstPool);
        assertThat(service.findIntent(intent.id())).contains(committed);
        assertThat(service.availability(window).available()).isEqualTo(79);
        BudgetActionResult duplicate = service.reserve(command);
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.intent().id()).isEqualTo(intent.id());
        assertThat(service.authorizeSend(intent.id()).code()).isEqualTo(ResultCode.ALREADY_COMMITTED);
        assertThat(countForWindow("provider_budget_event", window)).isEqualTo(events);
        assertThat(service.authorizeSend(reserve(window).id()).code()).isEqualTo(ResultCode.OK);
    }

    private static void openContext() {
        context = new SpringApplicationBuilder(BettingProjectApplication.class, BudgetClockConfiguration.class)
                .profiles("control-api")
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run("--spring.datasource.url=" + POSTGRESQL.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRESQL.getUsername(),
                        "--spring.datasource.password=" + POSTGRESQL.getPassword(),
                        "--BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                        "--betting.operator.id=test-operator");
        service = context.getBean(ProviderBudgetService.class);
        administration = context.getBean(ProviderBudgetAdministration.class);
        repository = context.getBean(ProviderBudgetRepository.class);
        jdbc = context.getBean(JdbcClient.class);
        clock = context.getBean(BudgetTestClock.class);
    }

    private UUID initialize(long sharedInitial, long projectLimit) {
        UUID id = UUID.randomUUID();
        assertThat(administration.initialize(initialization(id,
                "synthetic-account-" + UUID.randomUUID(), sharedInitial, projectLimit)).code())
                .isEqualTo(ResultCode.OK);
        return id;
    }

    private BudgetCommands.Initialize initialization(UUID id, String account, long sharedInitial, long projectLimit) {
        return new BudgetCommands.Initialize(id, "synthetic-provider", account,
                NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(1)), 100L,
                projectLimit, 20, sharedInitial, 0, null, null, 100 - sharedInitial,
                NOW, NOW.plus(Duration.ofDays(1)), PROOF, "Synthetic exclusive-account initialization");
    }

    private BudgetCommands.Initialize initializationTimes(BudgetCommands.Initialize initial, Instant startsAt,
            Instant endsAt, Instant observedAt, Instant validUntil) {
        return new BudgetCommands.Initialize(initial.windowId(), initial.provider(), initial.accountRef(),
                startsAt, endsAt, initial.capacity(), initial.projectLimit(), initial.reserve(),
                initial.sharedInitial(), initial.projectInitial(), initial.cadenceLimit(), initial.cadencePeriod(),
                initial.initialRemaining(), observedAt, validUntil, initial.proof(), initial.justification());
    }

    private Intent reserve(UUID window) {
        BudgetActionResult result = service.reserve(command(window));
        assertThat(result.code()).isEqualTo(ResultCode.OK);
        assertThat(result.created()).isTrue();
        return result.intent();
    }

    private BudgetCommands.Reserve command(UUID window) {
        return new BudgetCommands.Reserve(window, "synthetic-intent-" + UUID.randomUUID(),
                "synthetic-detail", SHA256);
    }

    private BudgetCommands.QuotaReading reading(long remaining, Set<UUID> covered) {
        return new BudgetCommands.QuotaReading(UUID.randomUUID(), remaining, clock.instant(),
                NOW.plus(Duration.ofDays(1)), PROOF, covered);
    }

    private List<BudgetActionResult> race(Callable<BudgetActionResult> first, Callable<BudgetActionResult> second)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Budget race start timed out");
                }
                return first.call();
            });
            var secondResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Budget race start timed out");
                }
                return second.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
        }
    }

    private long countForWindow(String table, UUID window) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE window_id = :id")
                .param("id", window).query(Long.class).single();
    }

    private void awaitBlockedBy(int blockingPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            long blocked = jdbc.sql("""
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock'
                      AND :blockingPid = ANY(pg_blocking_pids(pid))
                    """).param("blockingPid", blockingPid).query(Long.class).single();
            if (blocked > 0) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("Reservation never reached the PostgreSQL scope lock");
    }

    private void failInserts(String table, UUID window) {
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION test_budget_reject_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN
                    RAISE EXCEPTION 'synthetic budget persistence failure';
                END $$
                """).update();
        jdbc.sql("CREATE TRIGGER test_budget_insert_failure BEFORE INSERT ON " + table
                + " FOR EACH ROW WHEN (NEW.window_id = '" + window + "'::uuid)"
                + " EXECUTE FUNCTION test_budget_reject_insert()").update();
    }

    private void removeFailureTrigger(String table) {
        jdbc.sql("DROP TRIGGER IF EXISTS test_budget_insert_failure ON " + table).update();
        jdbc.sql("DROP FUNCTION IF EXISTS test_budget_reject_insert()").update();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BudgetClockConfiguration {
        @Bean
        @Primary
        BudgetTestClock budgetTestClock() {
            return new BudgetTestClock();
        }
    }

    static final class BudgetTestClock extends Clock {
        private volatile Instant now = NOW;

        void set(Instant value) {
            now = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
