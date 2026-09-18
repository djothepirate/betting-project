package com.bettingproject.collection.application.calendar;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import com.bettingproject.BettingProjectApplication;
import com.bettingproject.catalog.application.CatalogCommandService;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.collection.application.budget.*;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.budget.BudgetModel.*;
import com.bettingproject.collection.domain.capability.*;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.domain.*;
import com.bettingproject.operations.application.jobs.*;
import com.bettingproject.operations.domain.JobModel.*;
import com.bettingproject.operations.adapter.worker.CollectionWorkerLoop;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Real separate transactions, pools and workers; no test-enclosing transaction or supplier network. */
@Testcontainers
class CalendarJobsIT {
    private static final Instant NOW = Instant.parse("2030-08-10T12:00:00Z");
    private static final Instant DUE = Instant.parse("2020-01-01T00:00:00Z");
    private static final String SHA = "b".repeat(64);
    private static final ProviderCapabilityKey HL = key("highlightly", "920001", "2030", "Regular Season - 1");
    private static final ProviderCapabilityKey FD = key("football-data.org", "SYN", "950001", "REGULAR_SEASON");
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    static ConfigurableApplicationContext control;
    static ConfigurableApplicationContext batch;
    static JdbcClient jdbc;
    static final AtomicInteger calls = new AtomicInteger();
    static volatile Instant currentClock = NOW;
    static Function<CalendarPageRequest, CalendarPageResponse> responder;

    @BeforeAll static void start() { control = open("control-api"); batch = open("batch-worker"); jdbc = control.getBean(JdbcClient.class); }
    @AfterAll static void stop() { control.close(); batch.close(); }
    @BeforeEach void reset() {
        jdbc.sql("TRUNCATE persistent_job, provider_budget_scope, raw_snapshot, canonical_competition, canonical_team, "
                + "provider_mapping, control_command_receipt, provider_call_audit, outbox_message CASCADE").update();
        currentClock = NOW; calls.set(0); responder = request -> response(200, body());
    }

    @Test void jobOutboxAndInputAreAtomicIdempotentAndContentCollisionsDoNotMutate() {
        UUID window = window(HL);
        var command = command(window, HL);
        var first = plan().enqueueDiscovery("same-key", command, DUE, 3);
        var same = plan().enqueueDiscovery("same-key", command, DUE, 3);
        assertThat(same.created()).isFalse(); assertThat(same.job().id()).isEqualTo(first.job().id());
        assertThatThrownBy(() -> plan().enqueueDiscovery("same-key", command, DUE.plusSeconds(1), 3))
                .isInstanceOf(JobIdempotencyConflictException.class);
        assertThat(count("persistent_job")).isEqualTo(1);
        assertThat(count("outbox_message")).isEqualTo(1);
        assertThat(count("calendar_job_input")).isEqualTo(1);
        assertThat(count("collection_job_event")).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"collection_job_event", "outbox_message", "calendar_job_input"})
    void enqueueRollbackDoesNotLeaveAnOrphanJob(String table) {
        UUID window = window(HL); fail(table);
        try { assertThatThrownBy(() -> plan().enqueueDiscovery("rollback", command(window, HL), DUE, 3)).isInstanceOf(RuntimeException.class); }
        finally { unFail(table); }
        assertThat(count("persistent_job")).isZero(); assertThat(count("outbox_message")).isZero();
        assertThat(count("collection_job_event")).isZero(); assertThat(count("calendar_job_input")).isZero();
    }

    @Test void concurrentEnqueueResolvesOneJobAndOneOutbox() throws Exception {
        var command = command(window(HL), HL);
        var values = concurrently(() -> plan().enqueueDiscovery("concurrent-key", command, DUE, 3));
        assertThat(values.stream().map(value -> value.job().id()).distinct()).hasSize(1);
        assertThat(values.stream().filter(Enqueued::created)).hasSize(1);
        assertThat(count("outbox_message")).isEqualTo(1);
    }

    @Test void twoConcurrentClaimsReturnOnlyOneOwner() throws Exception {
        enqueue();
        var values = concurrently(() -> jobs().claimNext());
        assertThat(values.stream().filter(Optional::isPresent)).hasSize(1);
        assertThat(countWhere("collection_job_event", "event_type = 'CLAIMED'")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT attempt_count FROM outbox_message WHERE destination = 'COLLECTION_JOB'").query(Integer.class).single()).isEqualTo(1);
    }

    @Test void lateOwnerCannotWriteOrAcknowledgeAfterExpiryAndReclaim() {
        UUID id = enqueue(); Claim stale = jobs().claimNext().orElseThrow(); expire(id);
        assertThat(jobs().recoverExpired(10)).isEqualTo(1);
        assertThat(jobs().claimNext()).isEmpty(); // backoff not yet due
        due(id); Claim fresh = jobs().claimNext().orElseThrow();
        assertThat(fresh.job().token()).isNotEqualTo(stale.job().token());
        assertThatThrownBy(() -> jobs().fenced(stale, () -> { throw new AssertionError("stale work executed"); }))
                .isInstanceOf(JobLeaseLostException.class);
        assertThatThrownBy(() -> jobs().finish(stale, Outcome.success())).isInstanceOf(JobLeaseLostException.class);
        jobs().finish(fresh, Outcome.success());
        assertThat(job(id).status()).isEqualTo(Status.SUCCEEDED);
    }

    @Test void retryBoundTerminatesAndDoesNotClaimPublicationOrImportOutbox() {
        var queued = plan().enqueueDiscovery("bounded", command(window(HL), HL), DUE, 2);
        jobs().finish(jobs().claimNext().orElseThrow(), Outcome.retry("EXECUTION_ERROR"));
        assertThat(jobs().claimNext()).isEmpty(); due(queued.job().id());
        jobs().finish(jobs().claimNext().orElseThrow(), Outcome.retry("EXECUTION_ERROR"));
        assertThat(job(queued.job().id()).status()).isEqualTo(Status.FAILED);
        assertThat(job(queued.job().id()).attempts()).isEqualTo(2);
        control.getBean(com.bettingproject.operations.application.JobOutboxService.class)
                .schedulePublication("historic", "CALENDAR_DISCOVERY", "J7_IMPORT_ACCEPTED", "{}");
        assertThat(jobs().claimNext()).isEmpty();
        assertThat(countWhere("outbox_message", "destination = 'J7_IMPORT_ACCEPTED' AND status = 'PENDING'")).isEqualTo(1);
    }

    @Test void journalFailureRollsBackClaimAndAckIncludingOutbox() {
        UUID id = enqueue(); fail("collection_job_event");
        try { assertThatThrownBy(() -> jobs().claimNext()).isInstanceOf(RuntimeException.class); }
        finally { unFail("collection_job_event"); }
        assertThat(job(id).status()).isEqualTo(Status.PENDING); assertThat(job(id).attempts()).isZero();
        Claim claim = jobs().claimNext().orElseThrow(); fail("collection_job_event");
        try { assertThatThrownBy(() -> jobs().finish(claim, Outcome.success())).isInstanceOf(RuntimeException.class); }
        finally { unFail("collection_job_event"); }
        assertThat(job(id).status()).isEqualTo(Status.RUNNING);
        assertThat(countWhere("outbox_message", "destination = 'COLLECTION_JOB' AND status = 'SENDING'")).isEqualTo(1);
    }

    @Test void dueJobCollectsOnceAndConsumesBothDedicatedOutboxes() {
        bind(); UUID id = enqueue(); assertThat(worker().tick()).isTrue(); assertThat(worker().tick()).isFalse();
        assertThat(job(id).status()).isEqualTo(Status.SUCCEEDED); assertThat(calls.get()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1); assertThat(count("fixture_application_log")).isEqualTo(1);
        assertThat(countWhere("outbox_message", "status = 'DELIVERED'")).isEqualTo(2);
        assertThat(count("provider_call_intent")).isEqualTo(1);
    }

    @Test void futureJobsDoNotConsumeBudgetOrSend() {
        var queued = plan().enqueueDiscovery("future", command(window(HL), HL), Instant.parse("2099-01-01T00:00:00Z"), 3);
        assertThat(worker().tick()).isFalse(); assertThat(calls.get()).isZero();
        assertThat(job(queued.job().id()).status()).isEqualTo(Status.PENDING); assertThat(count("provider_call_intent")).isZero();
    }

    @Test void rawResponseSurvivesApplicationFailureAndRetryDoesNotCallTwice() {
        bind(); UUID id = enqueue(); fail("fixture_application_log");
        try { worker().tick(); } finally { unFail("fixture_application_log"); }
        assertThat(job(id).status()).isEqualTo(Status.RETRY); assertThat(calls.get()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1); assertThat(count("canonical_fixture")).isZero();
        due(id); worker().tick();
        assertThat(job(id).status()).isEqualTo(Status.SUCCEEDED); assertThat(calls.get()).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(1); assertThat(count("calendar_collection_derivation")).isEqualTo(1);
    }

    @Test void ackFailureAfterApplicationDoesNotDoubleNormalizeOnRecovery() {
        bind(); UUID id = enqueue(); Claim claim = jobs().claimNext().orElseThrow();
        assertThat(batch.getBean(com.bettingproject.collection.adapter.worker.CalendarDiscoveryJobHandler.class).execute(claim).successful()).isTrue();
        expire(id); jobs().recoverExpired(10); due(id); worker().tick();
        assertThat(job(id).status()).isEqualTo(Status.SUCCEEDED); assertThat(calls.get()).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(1);
    }

    @Test void parserFailureIsTerminalAndKeepsRefusalJournal() {
        responder = request -> response(200, "{}".getBytes(StandardCharsets.UTF_8));
        UUID id = enqueue(); worker().tick();
        assertThat(job(id).status()).isEqualTo(Status.FAILED); assertThat(calls.get()).isEqualTo(1);
        assertThat(countWhere("calendar_collection_derivation", "outcome = 'INCOMPATIBLE'")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
    }

    @Test void workerReplayHasOneLogicalEffectAcrossLostAcknowledgementAndNeverCallsProvider() {
        bind(); enqueue(); worker().tick();
        UUID page = jdbc.sql("SELECT id FROM calendar_collection_page").query(UUID.class).single();
        var replay = plan().enqueueReplay("replay", page, DUE, 3); Claim claim = jobs().claimNext().orElseThrow();
        assertThat(batch.getBean(com.bettingproject.collection.adapter.worker.CalendarReplayJobHandler.class).execute(claim).successful()).isTrue();
        expire(replay.job().id()); jobs().recoverExpired(10); due(replay.job().id()); worker().tick();
        assertThat(count("fixture_application_log")).isEqualTo(2); assertThat(count("collection_job_effect")).isEqualTo(1);
        assertThat(calls.get()).isEqualTo(1); assertThat(job(replay.job().id()).attempts()).isEqualTo(2);
    }

    @Test void expiredWorkerCannotPersistResponseOrCanonicalChanges() {
        UUID id = enqueue();
        responder = request -> { expire(id); jobs().recoverExpired(10); return response(200, body()); };
        worker().tick(); assertThat(count("raw_snapshot")).isZero(); assertThat(calls.get()).isEqualTo(1);
        responder = request -> { throw new AssertionError("uncertain send must never repeat"); };
        due(id); worker().tick();
        assertThat(job(id).status()).isEqualTo(Status.FAILED);
        assertThat(countWhere("provider_call_intent", "state = 'UNCERTAIN'")).isEqualTo(1);
        assertThat(countWhere("calendar_collection_page", "response_code = 'SEND_UNCERTAIN' AND received_at IS NULL")).isEqualTo(1);
        assertThat(count("provider_budget_incident")).isEqualTo(1);
    }

    @Test void realContextAndPoolRestartPreservesConsumedIntentAndNoSecondPermission() {
        UUID id = enqueue(); responder = request -> { throw new SimulatedCrash(); };
        assertThatThrownBy(() -> worker().tick()).isInstanceOf(SimulatedCrash.class);
        assertThat(job(id).status()).isEqualTo(Status.RUNNING);
        var batchPool = batch.getBean(com.zaxxer.hikari.HikariDataSource.class);
        var controlPool = control.getBean(com.zaxxer.hikari.HikariDataSource.class);
        batch.close(); control.close(); assertThat(batchPool.isClosed()).isTrue(); assertThat(controlPool.isClosed()).isTrue();
        control = open("control-api"); batch = open("batch-worker"); jdbc = control.getBean(JdbcClient.class);
        responder = request -> { throw new AssertionError("no resend after restart"); };
        expire(id); jobs().recoverExpired(10); due(id); worker().tick();
        assertThat(job(id).status()).isEqualTo(Status.FAILED); assertThat(calls.get()).isEqualTo(1);
        assertThat(countWhere("provider_call_intent", "state = 'UNCERTAIN'")).isEqualTo(1);
        assertThat(count("raw_snapshot")).isZero(); assertThat(count("provider_budget_incident")).isEqualTo(1);
        assertThat(countWhere("collection_job_event", "event_type = 'CLAIMED'")).isEqualTo(2);
        assertThat(worker().tick()).isFalse();
    }

    @Test void dailyPlannerUsesExactBindingsAndIsIdempotentAcrossFourRoutes() {
        UUID hl = window(HL); UUID fd = window(FD); UUID planId = UUID.randomUUID();
        List<CalendarJobPlanningService.RoutePlan> routes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var entries = entries().subList(i * 2, i * 2 + 2);
            routes.add(new CalendarJobPlanningService.RoutePlan(entries.getFirst().route(), List.of(
                    new CalendarJobPlanningService.Binding(entries.get(0).key(), hl, 2030),
                    new CalendarJobPlanningService.Binding(entries.get(1).key(), fd, 2030))));
        }
        assertThat(plan().planDay(planId, LocalDate.parse("2030-08-10"), DUE, routes)).hasSize(8);
        assertThat(plan().planDay(planId, LocalDate.parse("2030-08-10"), DUE, routes))
                .extracting(CalendarJobPlanningService.Planned::code).containsOnly("ALREADY_PLANNED");
        assertThat(count("persistent_job")).isEqualTo(8); assertThat(count("provider_call_intent")).isZero();
        var unknown = new CalendarJobPlanningService.RoutePlan(new CapabilityRouteKey("PPL", "2031", "LEAGUE", CapabilityDataType.CALENDAR), List.of());
        assertThat(plan().planDay(UUID.randomUUID(), LocalDate.parse("2030-08-10"), DUE, List.of(unknown)).getFirst().code()).isEqualTo("NO_PRIMARY");
        assertThat(batch.getBeansOfType(CollectionWorkerLoop.class)).isEmpty();
    }

    @Test void reservedButNeverAuthorizedPageCanResumeWithOnePermission() {
        UUID id = enqueue(); var input = control.getBean(CalendarJobInputStore.class).find(id).orElseThrow();
        var command = input.discovery(); var store = control.getBean(CalendarCollectionStore.class);
        String fingerprint = CalendarCollectionService.fingerprint(command);
        store.createAndResolve(new CalendarCollectionRecord(id, command.windowId(), command.capability(), command.date(),
                command.seasonStartYear(), fingerprint, SHA, "RUNNING", null, NOW, NOW));
        String hash = com.bettingproject.collection.domain.SnapshotHasher.sha256((fingerprint + ":0:100").getBytes(StandardCharsets.UTF_8));
        var reserved = control.getBean(ProviderBudgetService.class).reserve(new BudgetCommands.Reserve(command.windowId(),
                "calendar:" + id + ":0", "calendar/matches", hash));
        store.preparePage(id, reserved.intent().id(), 1, 0, 100, "calendar-http-v1", NOW);
        worker().tick();
        assertThat(job(id).status()).isEqualTo(Status.SUCCEEDED); assertThat(calls.get()).isEqualTo(1);
        assertThat(count("provider_call_intent")).isEqualTo(1); assertThat(count("calendar_collection_page")).isEqualTo(1);
    }

    @Test void cadenceDefersTheSameReservationThenPermitsOneSendWhenTheSlotExpires() throws Exception {
        UUID window = window(FD); var budget = control.getBean(ProviderBudgetService.class);
        List<UUID> occupied = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            var reserved = budget.reserve(new BudgetCommands.Reserve(window, "occupied-" + i, "calendar/matches", SHA));
            assertThat(budget.authorizeSend(reserved.intent().id()).code()).isEqualTo(ResultCode.OK);
            occupied.add(reserved.intent().id());
        }
        UUID id = plan().enqueueDiscovery("cadence", command(window, FD), DUE, 3).job().id();
        worker().tick(); assertThat(job(id).status()).isEqualTo(Status.RETRY); assertThat(calls.get()).isZero();
        assertThat(countWhere("provider_call_intent", "state = 'RESERVED'")).isEqualTo(1);
        for (UUID intent : occupied) { assertThat(budget.recordOutcome(new BudgetCommands.Outcome(intent, 200, SHA, null)).code()).isEqualTo(ResultCode.OK); }
        currentClock = NOW.plusSeconds(61);
        byte[] nativeResponse;
        try (var stream = CalendarJobsIT.class.getResourceAsStream("/fixtures/mvp001/calendar/football-data-calendar.synthetic.json")) { nativeResponse = stream.readAllBytes(); }
        responder = request -> response(200, nativeResponse);
        due(id); worker().tick();
        assertThat(job(id).status()).isEqualTo(Status.SUCCEEDED); assertThat(calls.get()).isEqualTo(1);
        assertThat(count("provider_call_intent")).isEqualTo(11); assertThat(count("calendar_collection_page")).isEqualTo(1);
    }

    @Test void pinnedRegistryAndParserChangesFailBeforeAnyBudgetReservation() {
        UUID id = enqueue();
        jdbc.sql("UPDATE calendar_job_input SET registry_sha256 = :hash WHERE job_id = :id").param("hash", "a".repeat(64)).param("id", id).update();
        worker().tick(); assertThat(job(id).status()).isEqualTo(Status.FAILED);
        UUID other = enqueue();
        jdbc.sql("UPDATE calendar_job_input SET parser_version = 'synthetic-other-v2' WHERE job_id = :id").param("id", other).update();
        worker().tick(); assertThat(job(other).status()).isEqualTo(Status.FAILED);
        assertThat(count("provider_call_intent")).isZero(); assertThat(calls.get()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"TIMEOUT", "TRANSPORT_ERROR", "RESPONSE_INTERRUPTED"})
    void uncertainTransportIsTerminalWithoutAutomaticReissue(String reason) {
        UUID id = enqueue(); responder = request -> new CalendarPageResponse(NOW, NOW, 0, new byte[0], null, reason);
        worker().tick(); assertThat(worker().tick()).isFalse();
        assertThat(job(id).status()).isEqualTo(Status.FAILED); assertThat(calls.get()).isEqualTo(1);
        assertThat(countWhere("provider_call_intent", "state = 'UNCERTAIN'")).isEqualTo(1);
    }

    @Test void receivedFirstPageIsReusedBeforeFetchingOnlyTheUnsentSecondPage() {
        UUID id = enqueue(); responder = request -> response(200, pageBody(request.offset(), 101));
        fail("fixture_application_log");
        try { worker().tick(); } finally { unFail("fixture_application_log"); }
        assertThat(calls.get()).isEqualTo(1); assertThat(job(id).status()).isEqualTo(Status.RETRY);
        due(id); worker().tick();
        assertThat(job(id).status()).isEqualTo(Status.SUCCEEDED); assertThat(calls.get()).isEqualTo(2);
        assertThat(count("calendar_collection_page")).isEqualTo(2);
        assertThat(count("calendar_collection_derivation")).isEqualTo(2);
        assertThat(count("fixture_application_log")).isEqualTo(101);
    }

    private static byte[] pageBody(int offset, int total) {
        List<String> fixtures = new ArrayList<>();
        for (int i = offset; i < Math.min(total, offset + 100); i++) {
            fixtures.add("""
                    {"id":%d,"round":"Regular Season - 1","date":"2030-08-10T19:00:00Z",
                     "homeTeam":{"id":910001,"name":"Synthetic Alpha"},"awayTeam":{"id":910002,"name":"Synthetic Beta"},
                     "league":{"id":920001,"season":2030,"name":"Synthetic League"},"state":{"description":"Not started"}}
                    """.formatted(900001 + i));
        }
        return ("{\"data\":[" + String.join(",", fixtures) + "],\"pagination\":{\"totalCount\":" + total
                + ",\"offset\":" + offset + ",\"limit\":100}}").getBytes(StandardCharsets.UTF_8);
    }

    private UUID enqueue() { return plan().enqueueDiscovery("job-" + UUID.randomUUID(), command(window(HL), HL), DUE, 3).job().id(); }
    private CalendarJobPlanningService plan() { return control.getBean(CalendarJobPlanningService.class); }
    private JobTransactions jobs() { return batch.getBean(JobTransactions.class); }
    private JobWorker worker() { return batch.getBean(JobWorker.class); }
    private Job job(UUID id) { return batch.getBean(JobRepository.class).find(id).orElseThrow(); }
    private void expire(UUID id) { jdbc.sql("UPDATE persistent_job SET lease_until = clock_timestamp() - interval '1 second' WHERE id = :id").param("id", id).update(); }
    private void due(UUID id) { jdbc.sql("UPDATE persistent_job SET next_run_at = clock_timestamp() - interval '1 second' WHERE id = :id").param("id", id).update(); }
    private static long count(String table) { return countWhere(table, "TRUE"); }
    private static long countWhere(String table, String predicate) { return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + predicate).query(Long.class).single(); }
    private static CalendarCollectionCommand command(UUID window, ProviderCapabilityKey key) {
        return new CalendarCollectionCommand(UUID.randomUUID(), window, key, LocalDate.parse("2030-08-10"), 2030);
    }
    private UUID window(ProviderCapabilityKey key) {
        UUID id = UUID.randomUUID(); boolean hl = key.provider().equals("highlightly");
        var command = new BudgetCommands.Initialize(id, key.provider(), "synthetic-" + UUID.randomUUID(),
                NOW.minusSeconds(3600), NOW.plusSeconds(86400), hl ? 100L : null, hl ? 80 : 20, hl ? 20 : 0,
                0, 0, hl ? null : 10, hl ? null : Duration.ofMinutes(1), hl ? 100L : null,
                hl ? NOW.minusSeconds(1) : null, hl ? NOW.plusSeconds(86400) : null, new Proof("synthetic", SHA), "Synthetic test");
        assertThat(control.getBean(ProviderBudgetAdministration.class).initialize(command).code()).isEqualTo(ResultCode.OK);
        return id;
    }
    private void bind() {
        var catalog = control.getBean(CatalogCommandService.class);
        UUID competition = catalog.registerCompetition("Synthetic League", "ZZZ", CompetitionType.DOMESTIC_LEAGUE);
        UUID home = catalog.registerTeam("Synthetic Alpha", "ZZZ"); UUID away = catalog.registerTeam("Synthetic Beta", "ZZZ");
        var mappings = control.getBean(ProviderMappingRepository.class);
        mappings.insertIfAbsentAndResolve(ProviderMapping.confirmed(HL.provider(), ProviderEntityType.COMPETITION, "920001", competition, "2030", HL.sourcePhase(), NOW));
        mappings.insertIfAbsentAndResolve(ProviderMapping.confirmed(HL.provider(), ProviderEntityType.TEAM, "910001", home, "", "", NOW));
        mappings.insertIfAbsentAndResolve(ProviderMapping.confirmed(HL.provider(), ProviderEntityType.TEAM, "910002", away, "", "", NOW));
    }
    private static ConfigurableApplicationContext open(String profile) {
        return new SpringApplicationBuilder(BettingProjectApplication.class, Synthetic.class).profiles(profile)
                .web(WebApplicationType.NONE).properties("spring.main.banner-mode=off")
                .run("--spring.datasource.url=" + DB.getJdbcUrl(), "--spring.datasource.username=" + DB.getUsername(),
                        "--spring.datasource.password=" + DB.getPassword(), "--BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                        "--betting.operator.id=synthetic-operator", "--betting.collection.worker.enabled=false");
    }
    private void fail(String table) {
        jdbc.sql("CREATE OR REPLACE FUNCTION job_test_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic'; END $$").update();
        jdbc.sql("CREATE TRIGGER job_test_failure BEFORE INSERT ON " + table + " FOR EACH ROW EXECUTE FUNCTION job_test_failure()").update();
    }
    private void unFail(String table) {
        jdbc.sql("DROP TRIGGER job_test_failure ON " + table).update(); jdbc.sql("DROP FUNCTION job_test_failure()").update();
    }
    private static <T> List<T> concurrently(Callable<T> action) throws Exception {
        var gate = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<T> task = () -> { gate.await(10, TimeUnit.SECONDS); return action.call(); };
            var first = pool.submit(task); var second = pool.submit(task);
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
    private static byte[] body() {
        try (var input = CalendarJobsIT.class.getResourceAsStream("/fixtures/mvp001/calendar/highlightly-page-0.synthetic.json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\"totalCount\": 2", "\"totalCount\": 1")
                    .replace("\"limit\": 1", "\"limit\": 100").getBytes(StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static CalendarPageResponse response(int status, byte[] body) { return new CalendarPageResponse(NOW, NOW, status, body, null, null); }
    private static ProviderCapabilityKey key(String provider, String competition, String season, String phase) {
        return new ProviderCapabilityKey(provider, competition, season, phase, CapabilityDataType.CALENDAR);
    }
    private static List<ProviderCapability> entries() {
        List<ProviderCapability> result = new ArrayList<>(); String[] codes = {"PPL", "PD", "DED", "ELC"};
        for (int i = 0; i < codes.length; i++) {
            var route = new CapabilityRouteKey(codes[i], "2030/2031", "LEAGUE", CapabilityDataType.CALENDAR);
            result.add(new ProviderCapability(i == 0 ? HL : key(HL.provider(), Integer.toString(920001 + i), "2030", HL.sourcePhase()),
                    route, CapabilityStatus.PRIMARY, CapabilityAuthorityRole.PRIMARY, true, List.of(new CapabilityEvidenceReference("synthetic", NOW, SHA))));
            result.add(new ProviderCapability(i == 0 ? FD : key(FD.provider(), "SYN" + i, "950001", FD.sourcePhase()),
                    route, CapabilityStatus.CONTROL, CapabilityAuthorityRole.CONTROL, true, List.of(new CapabilityEvidenceReference("synthetic", NOW, SHA))));
        }
        return result;
    }
    private static class SimulatedCrash extends Error { }
    @TestConfiguration(proxyBeanMethods = false)
    static class Synthetic {
        @Bean @Primary Clock testClock() {
            return new Clock() {
                public ZoneId getZone() { return ZoneOffset.UTC; }
                public Clock withZone(ZoneId zone) { return this; }
                public Instant instant() { return currentClock; }
            };
        }
        @Bean @Order(-100) CalendarPageClient testClient() {
            return new CalendarPageClient() {
                public String provider() { return HL.provider(); }
                public boolean available() { return true; }
                public CalendarPageResponse fetch(CalendarPageRequest request) {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    calls.incrementAndGet(); return responder.apply(request);
                }
            };
        }
        @Bean @Order(-100) CalendarPageClient testFootballDataClient() {
            return new CalendarPageClient() {
                public String provider() { return FD.provider(); }
                public boolean available() { return true; }
                public CalendarPageResponse fetch(CalendarPageRequest request) {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    calls.incrementAndGet(); return responder.apply(request);
                }
            };
        }
        @Bean @Primary ProviderCapabilityRegistry testRegistry() {
            return new ProviderCapabilityRegistry() {
                public String registryVersion() { return "synthetic-jobs-v1"; }
                public String documentSha256() { return SHA; }
                public Optional<ProviderCapability> find(ProviderCapabilityKey key) { return entries().stream().filter(e -> e.key().equals(key)).findFirst(); }
                public List<ProviderCapability> candidates(CapabilityRouteKey route) { return entries().stream().filter(e -> e.route().equals(route)).toList(); }
            };
        }
    }
}
