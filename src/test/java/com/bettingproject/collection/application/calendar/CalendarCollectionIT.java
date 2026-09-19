package com.bettingproject.collection.application.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import com.bettingproject.BettingProjectApplication;
import com.bettingproject.catalog.application.CatalogCommandService;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.collection.application.budget.BudgetCommands;
import com.bettingproject.collection.application.budget.ProviderBudgetAdministration;
import com.bettingproject.collection.application.budget.ProviderBudgetService;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.budget.BudgetModel.*;
import com.bettingproject.collection.domain.capability.*;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** No test-enclosing transaction: send permissions and every evidence boundary really commit. */
@Testcontainers
class CalendarCollectionIT {
    private static final Instant NOW = Instant.parse("2030-08-10T12:00:00Z");
    private static final String SHA = "b".repeat(64);
    private static final ProviderCapabilityKey HL = new ProviderCapabilityKey("highlightly", "920001", "2030",
            "Regular Season - 1", CapabilityDataType.CALENDAR);
    private static final ProviderCapabilityKey FD = new ProviderCapabilityKey("football-data.org", "SYN", "950001",
            "REGULAR_SEASON", CapabilityDataType.CALENDAR);
    @Container private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");
    private static ConfigurableApplicationContext context;
    private static JdbcClient jdbc;
    private static CalendarCollectionStore store;
    private static ProviderBudgetService budget;

    @BeforeAll static void start() { open(); }
    @AfterAll static void stop() { if (context != null) context.close(); }

    @BeforeEach void clean() {
        jdbc.sql("TRUNCATE provider_budget_scope, raw_snapshot, canonical_competition, canonical_team, "
                + "provider_mapping, control_command_receipt, provider_call_audit, outbox_message CASCADE").update();
    }

    @Test void nativeBytesAreAuditedBeforeDerivationAndReplayNeverDebitsOrSends() throws Exception {
        bind();
        UUID window = window(HL, 80);
        FakeClient client = client(HL, request -> response(200, hl(0, 1, 1), null));
        var command = command(window, HL);
        var service = service(client);
        assertThat(service.collect(command).status()).isEqualTo("COMPLETED");
        assertThat(client.calls.get()).isEqualTo(1);
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(2);
        assertThat(count("provider_call_audit")).isEqualTo(1);
        assertThat(count("outbox_message")).isEqualTo(1);
        var page = store.pages(command.id()).getFirst();
        var derived = store.derivations(page.id()).getFirst();
        assertThat(page.rawSnapshotId()).isNotEqualTo(derived.derivedSnapshotId());
        assertThat(store.readRaw(page.rawSnapshotId()).orElseThrow().payload()).isEqualTo(hl(0, 1, 1));
        assertThat(jdbc.sql("SELECT observed_at FROM fixture_observation").query(Instant.class).single()).isEqualTo(NOW);
        assertThat(jdbc.sql("SELECT last_authority_policy_version FROM canonical_fixture").query(String.class).single())
                .isEqualTo(SHA);
        assertThat(budget.availability(window).available()).isEqualTo(79);
        assertThat(service.collect(command).status()).isEqualTo("COMPLETED");
        assertThat(context.getBean(CalendarNativeReplayService.class).replayPage(page.id())).isEqualTo("APPLIED");
        assertThat(client.calls.get()).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(2);
        assertThat(store.derivations(page.id())).hasSize(2);
        assertThat(budget.availability(window).available()).isEqualTo(79);
    }

    @Test void footballDataControlCannotOverwritePrimaryDespiteDifferentNativeShape() throws Exception {
        bind();
        service(client(HL, request -> response(200, hl(0, 1, 1), null))).collect(command(window(HL, 80), HL));
        byte[] nativeFd = resource("football-data-calendar.synthetic.json").replace("TIMED", "CANCELLED")
                .getBytes(StandardCharsets.UTF_8);
        var result = service(client(FD, request -> response(200, nativeFd, null))).collect(command(window(FD, 20), FD));
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM canonical_fixture").query(String.class).single()).isEqualTo("SCHEDULED");
        assertThat(jdbc.sql("SELECT anomaly_code FROM normalization_anomaly").query(String.class).single())
                .isEqualTo("CONTROL_DIVERGENCE");
        assertThat(count("provider_call_audit")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT season_label FROM canonical_season").query(String.class).single()).isEqualTo("2030");
        assertThat(jdbc.sql("SELECT phase FROM canonical_fixture").query(String.class).single()).isEqualTo("REGULAR_SEASON");
        assertThat(jdbc.sql("SELECT source_season FROM fixture_observation WHERE provider='football-data.org'")
                .query(String.class).single()).isEqualTo("950001");
        assertThat(jdbc.sql("SELECT source_phase FROM fixture_observation WHERE provider='highlightly'")
                .query(String.class).single()).isEqualTo("Regular Season - 1");
    }

    @Test void malformedNativePagePreservesRawResponseWithoutPartialNormalization() {
        var command = command(window(HL, 80), HL);
        var result = service(client(HL, request -> response(200, "{bad".getBytes(StandardCharsets.UTF_8), null)))
                .collect(command);
        assertThat(result.status()).isEqualTo("INCOMPLETE");
        assertThat(result.reasonCode()).isEqualTo("INCOMPATIBLE");
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("fixture_observation")).isZero();
        assertThat(count("canonical_fixture")).isZero();
        assertThat(store.derivations(store.pages(command.id()).getFirst().id())).singleElement()
                .extracting(CalendarDerivationRecord::outcome).isEqualTo("INCOMPATIBLE");
        assertThat(budget.availability(command.windowId()).available()).isEqualTo(79);
    }

    @Test void deduplicatedNativeBytesStillUseEachCallsReceiveTimeRatherThanTheRawRowsFirstTime() {
        bind();
        UUID window = window(HL, 80);
        service(client(HL, request -> response(200, hl(0, 1, 1), null))).collect(command(window, HL));
        Instant later = NOW.plusSeconds(10);
        var second = command(window, HL);
        service(client(HL, request -> new CalendarPageResponse(NOW, later, 200, hl(0, 1, 1), null, null)))
                .collect(second);
        assertThat(count("raw_snapshot")).isEqualTo(3);
        assertThat(count("fixture_observation")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT last_authority_observed_at FROM canonical_fixture").query(Instant.class).single())
                .isEqualTo(later);
        assertThat(store.readRaw(store.pages(second.id()).getFirst().rawSnapshotId()).orElseThrow().receivedAt())
                .isEqualTo(NOW);
    }

    @Test void laterFailureOfTheOtherProviderDoesNotRollbackPreviousEvidence() {
        bind();
        var first = command(window(HL, 80), HL);
        service(client(HL, request -> response(200, hl(0, 1, 1), null))).collect(first);
        UUID raw = store.pages(first.id()).getFirst().rawSnapshotId();
        service(client(FD, request -> response(503, new byte[0], null))).collect(command(window(FD, 20), FD));
        assertThat(store.readRaw(raw)).isPresent();
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("provider_call_audit")).isEqualTo(2);
        assertThat(store.findCollection(first.id()).orElseThrow().status()).isEqualTo("COMPLETED");
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 429})
    void authenticationAndQuotaErrorsAtomicallyRetainAuditAndSuspendOnlyTheirWindow(int status) {
        UUID affected = window(HL, 80);
        UUID other = window(FD, 20);
        var result = service(client(HL, request -> response(status, "{}".getBytes(StandardCharsets.UTF_8), null)))
                .collect(command(affected, HL));
        assertThat(result.reasonCode()).isEqualTo("HTTP_ERROR");
        assertThat(budget.availability(affected).code()).isEqualTo(ResultCode.SUSPENDED);
        assertThat(budget.availability(other).available()).isEqualTo(20);
        assertThat(budget.incidents(affected)).singleElement().extracting(Incident::code).isEqualTo("HTTP_" + status);
        assertThat(count("provider_call_audit")).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM outbox_message").query(String.class).single()).isEqualTo("DELIVERED");
    }

    @Test void uncertainSendIsNotRetriedAndOtherIntentionsKeepTheirRemainingBudget() {
        UUID window = window(HL, 80);
        var client = client(HL, request -> response(0, new byte[0], "UNCERTAIN_RESPONSE"));
        var command = command(window, HL);
        var service = service(client);
        assertThat(service.collect(command).reasonCode()).isEqualTo("UNCERTAIN_RESPONSE");
        assertThat(service.collect(command).reasonCode()).isEqualTo("UNCERTAIN_RESPONSE");
        assertThat(client.calls.get()).isEqualTo(1);
        assertThat(budget.findIntent(store.pages(command.id()).getFirst().intentId()).orElseThrow().state())
                .isEqualTo(IntentState.UNCERTAIN);
        assertThat(budget.availability(window).available()).isEqualTo(79);
        assertThat(service(client(HL, request -> response(200, hl(0, 0, 0), null)))
                .collect(command(window, HL)).status()).isEqualTo("COMPLETED");
        assertThat(budget.availability(window).available()).isEqualTo(78);
    }

    @Test void twoConcurrentInvocationsOfOneCollectionSendOnlyOnce() throws Exception {
        var client = client(HL, request -> response(200, hl(0, 0, 0), null));
        var service = service(client);
        var command = command(window(HL, 80), HL);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return service.collect(command); });
            var b = executor.submit(() -> { start.await(); return service.collect(command); });
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS).status()).isIn("RUNNING", "COMPLETED");
            assertThat(b.get(20, TimeUnit.SECONDS).status()).isIn("RUNNING", "COMPLETED");
        }
        assertThat(client.calls.get()).isEqualTo(1);
        assertThat(count("provider_call_intent")).isEqualTo(1);
        assertThat(count("provider_call_audit")).isEqualTo(1);
    }

    @Test void paginationRetainsEachPageAndBudgetExhaustionNeverClaimsExhaustiveness() {
        bind();
        UUID window = window(HL, 1);
        var client = client(HL, request -> response(200, hl(request.offset(), 100, 101), null));
        var command = command(window, HL);
        var result = service(client).collect(command);
        assertThat(result.status()).isEqualTo("INCOMPLETE");
        assertThat(result.reasonCode()).isEqualTo("EXHAUSTED");
        assertThat(client.calls.get()).isEqualTo(1);
        assertThat(store.pages(command.id())).hasSize(1);
        assertThat(count("fixture_observation")).isEqualTo(100);
        assertThat(count("raw_snapshot")).isEqualTo(2);
    }

    @Test void twoPagesAreEachBilledOnceAndChangingTotalsAreNeverCalledComplete() {
        var client = client(HL, request -> response(200,
                request.offset() == 0 ? hl(0, 100, 101) : hl(100, 1, 101), null));
        var command = command(window(HL, 80), HL);
        assertThat(service(client).collect(command).status()).isEqualTo("COMPLETED");
        assertThat(client.calls.get()).isEqualTo(2);
        assertThat(budget.availability(command.windowId()).available()).isEqualTo(78);
        var changed = client(HL, request -> response(200,
                request.offset() == 0 ? hl(0, 100, 101) : hl(100, 2, 102), null));
        var changedCommand = command(window(HL, 80), HL);
        assertThat(service(changed).collect(changedCommand).reasonCode()).isEqualTo("INCOMPLETE_PAGINATION");
        assertThat(store.pages(changedCommand.id())).hasSize(2);
    }

    @Test void productionDisabledAndUnknownCapabilitiesCannotConsumeBudget() {
        var command = command(window(HL, 80), HL);
        assertThat(context.getBean(CalendarCollectionService.class).collect(command).reasonCode())
                .isEqualTo("PROVIDER_DISABLED");
        var unknown = new CalendarCollectionCommand(command.id(), command.windowId(),
                new ProviderCapabilityKey("highlightly", "920001", "2031", "Regular Season - 1", CapabilityDataType.CALENDAR),
                command.date(), 2031);
        assertThat(service(client(HL, request -> { throw new AssertionError(); })).collect(unknown).reasonCode())
                .isEqualTo("CAPABILITY_INACTIVE");
        assertThat(count("calendar_collection")).isZero();
        assertThat(count("provider_call_intent")).isZero();
    }

    @Test void restartRetainsEvidenceAndNeverReissuesTheCommittedCollection() {
        var command = command(window(HL, 80), HL);
        assertThat(service(client(HL, request -> response(0, new byte[0], "UNCERTAIN_RESPONSE")))
                .collect(command).status()).isEqualTo("INCOMPLETE");
        var pool = context.getBean(com.zaxxer.hikari.HikariDataSource.class);
        context.close();
        assertThat(pool.isClosed()).isTrue();
        open();
        var client = client(HL, request -> { throw new AssertionError("must not resend"); });
        assertThat(service(client).collect(command).status()).isEqualTo("INCOMPLETE");
        assertThat(client.calls.get()).isZero();
        assertThat(budget.availability(command.windowId()).available()).isEqualTo(79);
        assertThat(count("provider_call_audit")).isEqualTo(1);
        assertThat(budget.incidents(command.windowId())).hasSize(1);
    }

    @Test void crashAfterCommittedPermitCannotGrantANewSendAfterRestart() {
        var command = command(window(HL, 80), HL);
        assertThatThrownBy(() -> service(client(HL, request -> { throw new AssertionError("synthetic process stop"); }))
                .collect(command)).isInstanceOf(AssertionError.class);
        var page = store.pages(command.id()).getFirst();
        assertThat(page.responseCode()).isEqualTo("PENDING");
        assertThat(budget.findIntent(page.intentId()).orElseThrow().state()).isEqualTo(IntentState.COMMITTED_FOR_SEND);
        var pool = context.getBean(com.zaxxer.hikari.HikariDataSource.class);
        context.close();
        assertThat(pool.isClosed()).isTrue();
        open();
        var client = client(HL, request -> { throw new AssertionError("must not resend"); });
        assertThat(service(client).collect(command).status()).isEqualTo("RUNNING");
        assertThat(client.calls.get()).isZero();
        assertThat(budget.authorizeSend(page.intentId()).code()).isEqualTo(ResultCode.ALREADY_COMMITTED);
        assertThat(budget.availability(command.windowId()).available()).isEqualTo(79);
        assertThat(count("provider_call_audit")).isEqualTo(1);
        assertThat(count("calendar_collection_derivation")).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"SECRET_ECHO", "RESPONSE_TOO_LARGE", "UNCERTAIN_RESPONSE"})
    void knownQuotaErrorSuspendsEvenWhenTheBodyIsNotUsable(String code) {
        var command = command(window(HL, 80), HL);
        var result = service(client(HL, request -> response(429, new byte[0], code))).collect(command);
        assertThat(result.reasonCode()).isEqualTo(code);
        assertThat(budget.availability(command.windowId()).code()).isEqualTo(ResultCode.SUSPENDED);
        assertThat(budget.incidents(command.windowId())).singleElement().extracting(Incident::code).isEqualTo("HTTP_429");
        assertThat(count("raw_snapshot")).isEqualTo(code.equals("SECRET_ECHO") ? 0 : 1);
        assertThat(count("provider_call_audit")).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(longs = {0, 101})
    void restrictiveOrImpossibleCountersCannotBeIgnored(long remaining) {
        var command = command(window(HL, 80), HL);
        var result = service(client(HL, request -> new CalendarPageResponse(NOW, NOW, 200,
                hl(0, 0, 0), remaining, null))).collect(command);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(budget.availability(command.windowId()).code())
                .isEqualTo(remaining == 0 ? ResultCode.EXHAUSTED : ResultCode.CONTRADICTORY_OBSERVATION);
        assertThat(jdbc.sql("SELECT quota_remaining FROM provider_call_audit").query(Long.class).single())
                .isEqualTo(remaining);
    }

    @ParameterizedTest @ValueSource(longs = {99, 101})
    void recordingOneResponseTwiceDoesNotDuplicateQuotaObservationsOrIncidents(long remaining) {
        var command = command(window(HL, 80), HL);
        var response = new CalendarPageResponse(NOW, NOW, 200, hl(0, 0, 0), remaining, null);
        service(client(HL, request -> response)).collect(command);
        var page = store.pages(command.id()).getFirst();
        long events = count("provider_budget_event");
        long incidents = budget.incidents(command.windowId()).size();
        long version = jdbc.sql("SELECT version FROM provider_budget_window WHERE id=:id")
                .param("id", command.windowId()).query(Long.class).single();
        context.getBean(CalendarEvidenceTransactions.class).recordResponse(page, HL.provider(), response);
        assertThat(count("provider_budget_event")).isEqualTo(events);
        assertThat(budget.incidents(command.windowId())).hasSize((int) incidents);
        assertThat(jdbc.sql("SELECT version FROM provider_budget_window WHERE id=:id")
                .param("id", command.windowId()).query(Long.class).single()).isEqualTo(version);
        assertThat(count("provider_call_audit")).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"payload", "compression"})
    void replayRefusesAlteredEvidenceWithAnAppendOnlyIntegrityResult(String field) {
        var command = command(window(HL, 80), HL);
        service(client(HL, request -> response(200, hl(0, 0, 0), null))).collect(command);
        var page = store.pages(command.id()).getFirst();
        if (field.equals("payload")) {
            jdbc.sql("UPDATE raw_snapshot SET payload=:bytes WHERE id=:id")
                    .param("bytes", new byte[] {1, 2, 3}).param("id", page.rawSnapshotId()).update();
        }
        else {
            jdbc.sql("UPDATE raw_snapshot SET payload_compression='gzip' WHERE id=:id")
                    .param("id", page.rawSnapshotId()).update();
        }
        assertThat(context.getBean(CalendarNativeReplayService.class).replayPage(page.id())).isEqualTo("INTEGRITY_ERROR");
        assertThat(store.derivations(page.id())).extracting(CalendarDerivationRecord::outcome)
                .containsExactlyInAnyOrder("APPLIED", "INTEGRITY_ERROR");
        assertThat(budget.availability(command.windowId()).available()).isEqualTo(79);
    }

    @Test void failedBudgetJournalRollsBackResponseButRetainsThePreviouslyCommittedPermit() {
        var command = command(window(HL, 80), HL);
        var client = client(HL, request -> {
            failInsert("provider_budget_event");
            return response(200, hl(0, 0, 0), null);
        });
        try {
            assertThatThrownBy(() -> service(client).collect(command)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        finally { removeFailure("provider_budget_event"); }
        var page = store.pages(command.id()).getFirst();
        assertThat(page.responseCode()).isEqualTo("PENDING");
        assertThat(page.rawSnapshotId()).isNull();
        assertThat(budget.findIntent(page.intentId()).orElseThrow().state()).isEqualTo(IntentState.COMMITTED_FOR_SEND);
        assertThat(count("raw_snapshot")).isZero();
        assertThat(count("provider_call_audit")).isEqualTo(1);
        assertThat(service(client).collect(command).status()).isEqualTo("RUNNING");
        assertThat(client.calls.get()).isEqualTo(1);
    }

    @Test void failedDerivationJournalRollsBackCanonButKeepsRawResponseAndPermitsNetworkFreeReplay() {
        bind();
        var command = command(window(HL, 80), HL);
        failInsert("calendar_collection_derivation");
        try {
            assertThatThrownBy(() -> service(client(HL, request -> response(200, hl(0, 1, 1), null))).collect(command))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        finally { removeFailure("calendar_collection_derivation"); }
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("canonical_fixture")).isZero();
        assertThat(count("fixture_observation")).isZero();
        var page = store.pages(command.id()).getFirst();
        assertThat(page.responseCode()).isEqualTo("RECEIVED");
        assertThat(budget.findIntent(page.intentId()).orElseThrow().state()).isEqualTo(IntentState.RESULT_RECORDED);
        assertThat(context.getBean(CalendarNativeReplayService.class).replayPage(page.id())).isEqualTo("APPLIED");
        assertThat(count("canonical_fixture")).isEqualTo(1);
        assertThat(budget.availability(command.windowId()).available()).isEqualTo(79);
        assertThat(store.findCollection(command.id()).orElseThrow().status()).isEqualTo("RUNNING");
    }

    private void failInsert(String table) {
        jdbc.sql("CREATE OR REPLACE FUNCTION test_calendar_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "RAISE EXCEPTION 'synthetic calendar failure'; END $$").update();
        jdbc.sql("CREATE TRIGGER test_calendar_insert_failure BEFORE INSERT ON " + table
                + " FOR EACH ROW EXECUTE FUNCTION test_calendar_failure()").update();
    }

    private void removeFailure(String table) {
        jdbc.sql("DROP TRIGGER IF EXISTS test_calendar_insert_failure ON " + table).update();
        jdbc.sql("DROP FUNCTION IF EXISTS test_calendar_failure()").update();
    }

    private static void open() {
        context = new SpringApplicationBuilder(BettingProjectApplication.class, SyntheticConfiguration.class)
                .profiles("control-api").web(WebApplicationType.NONE).properties("spring.main.banner-mode=off")
                .run("--spring.datasource.url=" + POSTGRESQL.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRESQL.getUsername(),
                        "--spring.datasource.password=" + POSTGRESQL.getPassword(),
                        "--BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER", "--betting.operator.id=test-operator");
        jdbc = context.getBean(JdbcClient.class);
        store = context.getBean(CalendarCollectionStore.class);
        budget = context.getBean(ProviderBudgetService.class);
    }

    private UUID window(ProviderCapabilityKey key, long limit) {
        UUID id = UUID.randomUUID();
        boolean hl = key.equals(HL);
        var initial = new BudgetCommands.Initialize(id, key.provider(), "synthetic-" + UUID.randomUUID(),
                NOW.minusSeconds(3600), NOW.plusSeconds(86400), hl ? 100L : null, limit, hl ? 20 : 0,
                0, 0, hl ? null : 10, hl ? null : Duration.ofMinutes(1), hl ? 100L : null,
                hl ? NOW.minusSeconds(1) : null, hl ? NOW.plusSeconds(86400) : null, new Proof("synthetic", SHA), "Synthetic test");
        assertThat(context.getBean(ProviderBudgetAdministration.class).initialize(initial).code()).isEqualTo(ResultCode.OK);
        return id;
    }

    private CalendarCollectionService service(FakeClient client) {
        return new CalendarCollectionService(context.getBean(ProviderCapabilityRegistry.class), budget, store,
                List.of(client), context.getBean(CalendarEvidenceTransactions.class),
                context.getBean(CalendarDerivationService.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CalendarCollectionCommand command(UUID window, ProviderCapabilityKey key) {
        return new CalendarCollectionCommand(UUID.randomUUID(), window, key, LocalDate.parse("2030-08-10"), 2030);
    }

    private void bind() {
        var commands = context.getBean(CatalogCommandService.class);
        UUID competition = commands.registerCompetition("Synthetic League", "ZZZ", CompetitionType.DOMESTIC_LEAGUE);
        UUID home = commands.registerTeam("Synthetic Alpha", "ZZZ");
        UUID away = commands.registerTeam("Synthetic Beta", "ZZZ");
        mapping(HL, ProviderEntityType.COMPETITION, "920001", competition);
        mapping(HL, ProviderEntityType.TEAM, "910001", home);
        mapping(HL, ProviderEntityType.TEAM, "910002", away);
        mapping(FD, ProviderEntityType.COMPETITION, "SYN", competition);
        mapping(FD, ProviderEntityType.TEAM, "960001", home);
        mapping(FD, ProviderEntityType.TEAM, "960002", away);
    }

    private void mapping(ProviderCapabilityKey key, ProviderEntityType type, String ref, UUID canonical) {
        context.getBean(ProviderMappingRepository.class).insertIfAbsentAndResolve(ProviderMapping.confirmed(
                key.provider(), type, ref, canonical,
                type == ProviderEntityType.COMPETITION ? key.sourceSeason() : "",
                type == ProviderEntityType.COMPETITION ? key.sourcePhase() : "", NOW));
    }

    private static CalendarPageResponse response(int status, byte[] body, String failure) {
        return new CalendarPageResponse(NOW, NOW, status, body, null, failure);
    }

    private static byte[] hl(int offset, int size, int total) {
        var fixtures = new java.util.ArrayList<String>();
        for (int i = 0; i < size; i++) {
            fixtures.add("""
                    {"id":%d,"round":"Regular Season - 1","date":"2030-08-10T19:00:00Z",
                    "homeTeam":{"id":910001,"name":"Synthetic Alpha"},
                    "awayTeam":{"id":910002,"name":"Synthetic Beta"},
                    "league":{"id":920001,"season":2030,"name":"Synthetic League"},
                    "state":{"description":"Not started"}}
                    """.formatted(900001 + offset + i));
        }
        return ("{\"data\":[" + String.join(",", fixtures) + "],\"pagination\":{\"offset\":" + offset
                + ",\"limit\":100,\"totalCount\":" + total + "}}").getBytes(StandardCharsets.UTF_8);
    }

    private static String resource(String name) throws Exception {
        try (var stream = CalendarCollectionIT.class.getResourceAsStream("/fixtures/mvp001/calendar/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long count(String table) { return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single(); }

    private static FakeClient client(ProviderCapabilityKey key, Function<CalendarPageRequest, CalendarPageResponse> responder) {
        return new FakeClient(key.provider(), responder);
    }

    private static final class FakeClient implements CalendarPageClient {
        private final String provider;
        private final Function<CalendarPageRequest, CalendarPageResponse> responder;
        private final AtomicInteger calls = new AtomicInteger();
        private FakeClient(String provider, Function<CalendarPageRequest, CalendarPageResponse> responder) {
            this.provider = provider;
            this.responder = responder;
        }
        @Override public String provider() { return provider; }
        @Override public boolean available() { return true; }
        @Override public CalendarPageResponse fetch(CalendarPageRequest request) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            calls.incrementAndGet();
            return responder.apply(request);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SyntheticConfiguration {
        @Bean @Primary Clock syntheticClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean @Primary ProviderCapabilityRegistry syntheticCalendarRegistry() {
            var entries = List.of(capability(HL, CapabilityStatus.PRIMARY, CapabilityAuthorityRole.PRIMARY),
                    capability(FD, CapabilityStatus.CONTROL, CapabilityAuthorityRole.CONTROL));
            return new ProviderCapabilityRegistry() {
                @Override public String registryVersion() { return "synthetic-calendar-v1"; }
                @Override public String documentSha256() { return SHA; }
                @Override public Optional<ProviderCapability> find(ProviderCapabilityKey key) {
                    return entries.stream().filter(item -> item.key().equals(key)).findFirst();
                }
                @Override public List<ProviderCapability> candidates(CapabilityRouteKey key) {
                    return entries.stream().filter(item -> item.route().equals(key)).toList();
                }
            };
        }
        private static ProviderCapability capability(ProviderCapabilityKey key, CapabilityStatus status,
                CapabilityAuthorityRole role) {
            return new ProviderCapability(key, new CapabilityRouteKey("SYN", "2030", "REGULAR_SEASON", CapabilityDataType.CALENDAR),
                    status, role, true, List.of(new CapabilityEvidenceReference("synthetic", NOW, SHA)));
        }
    }
}
