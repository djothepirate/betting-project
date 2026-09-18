package com.bettingproject.collection.application.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import com.bettingproject.collection.application.CalendarSnapshot;
import com.bettingproject.collection.application.CalendarSnapshotSchemas;
import com.bettingproject.collection.application.DiscoveredFixture;
import com.bettingproject.collection.application.budget.BudgetActionResult;
import com.bettingproject.collection.application.budget.BudgetCommands;
import com.bettingproject.collection.application.budget.ProviderBudgetService;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.collection.domain.budget.BudgetModel.Intent;
import com.bettingproject.collection.domain.budget.BudgetModel.IntentState;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;
import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityEvidenceReference;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.CapabilityStatus;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarCollectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ProviderCapabilityKey KEY = new ProviderCapabilityKey(
            "highlightly", "synthetic-competition-001", "2026/2027", "LEAGUE", CapabilityDataType.CALENDAR);

    @Test
    void stopsAtOneHundredPagesWithoutSendingTheHundredAndFirst() {
        Harness harness = new Harness();
        harness.derivation.interpret = (collection, page) -> parsed(page, page.pageOffset() + 100, 10001, "fixture-" + page.pageNumber());

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertThat(result.status()).isEqualTo("INCOMPLETE");
        assertThat(result.reasonCode()).isEqualTo("PAGE_LIMIT");
        assertThat(harness.client.calls).isEqualTo(100);
        assertThat(harness.budget.reservations).hasSize(100);
        assertThat(harness.budget.authorizations).hasSize(100);
        assertThat(harness.derivation.applied).hasSize(100);
        assertThat(harness.store.pages(harness.command.id())).hasSize(100);
        assertThat(harness.budget.reservations.getLast().idempotencyKey()).endsWith(":9900");
    }

    @Test
    void refusesCyclicPaginationAfterPreservingBothPagesButBeforeApplyingTheRepeatedPage() {
        Harness harness = new Harness();
        harness.derivation.interpret = (collection, page) -> parsed(page, 100, 3, "fixture-" + page.pageNumber());

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertPaginationFailure(harness, result);
        assertThat(harness.evidence.refusals).containsExactly("INCOMPLETE_PAGINATION");
    }

    @Test
    void refusesRepeatedRawHashWithoutLosingTheSecondCallEvidence() {
        Harness harness = new Harness();
        harness.client.repeatBody = true;
        harness.derivation.interpret = (collection, page) -> parsed(page, page.pageOffset() + 100, 3, "fixture-" + page.pageNumber());

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertPaginationFailure(harness, result);
        assertThat(harness.store.pages(harness.command.id())).extracting(CalendarPageRecord::rawSha256)
                .containsOnly(harness.store.pages(harness.command.id()).getFirst().rawSha256());
    }

    @Test
    void refusesDuplicateFixtureAcrossDistinctRawPages() {
        Harness harness = new Harness();
        harness.derivation.interpret = (collection, page) -> parsed(page, page.pageOffset() + 100, 3, "same-fixture");

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertPaginationFailure(harness, result);
    }

    @Test
    void refusesChangingTotalInsteadOfClaimingAnExhaustiveCollection() {
        Harness harness = new Harness();
        harness.derivation.interpret = (collection, page) -> parsed(page, page.pageOffset() + 100,
                10 + page.pageNumber(), "fixture-" + page.pageNumber());

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertPaginationFailure(harness, result);
    }

    @Test
    void sameCollectionAndContentReturnsDurableStateWithoutAnotherBudgetReservationOrCall() {
        Harness harness = new Harness();
        CalendarCollectionService service = harness.service();
        CalendarCollectionResult first = service.collect(harness.command);

        CalendarCollectionResult second = service.collect(harness.command);

        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(second).isEqualTo(first);
        assertThat(harness.client.calls).isEqualTo(1);
        assertThat(harness.budget.reservations).hasSize(1);
        assertThat(harness.derivation.applied).hasSize(1);
    }

    @Test
    void sameCollectionUuidWithAnotherWindowOrDateConflictsWithoutAnyNewEffect() {
        Harness harness = new Harness();
        CalendarCollectionService service = harness.service();
        service.collect(harness.command);
        CalendarCollectionCommand otherWindow = new CalendarCollectionCommand(harness.command.id(), UUID.randomUUID(),
                KEY, harness.command.date(), 2026);
        CalendarCollectionCommand otherDate = new CalendarCollectionCommand(harness.command.id(), harness.command.windowId(),
                KEY, harness.command.date().plusDays(1), 2026);

        assertThat(service.collect(otherWindow).reasonCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(service.collect(otherDate).reasonCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(harness.client.calls).isEqualTo(1);
        assertThat(harness.budget.reservations).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = ResultCode.class, names = {"EXHAUSTED", "SUSPENDED", "OUTSIDE_WINDOW", "STALE_OBSERVATION"})
    void budgetRefusalStopsWithoutCallingTheClientOrPreparingAnAudit(ResultCode refusal) {
        Harness harness = new Harness();
        harness.budget.reservationCode = refusal;

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertThat(result.status()).isEqualTo("INCOMPLETE");
        assertThat(result.reasonCode()).isEqualTo(refusal.name());
        assertThat(harness.client.calls).isZero();
        assertThat(harness.store.pages(harness.command.id())).isEmpty();
        assertThat(harness.budget.authorizations).isEmpty();
        assertThat(harness.store.findCollection(harness.command.id()).orElseThrow().status()).isEqualTo("INCOMPLETE");
    }

    @Test
    void budgetExhaustionOnNextPagePreservesTheEvidenceAlreadyCollected() {
        Harness harness = new Harness();
        harness.budget.successfulReservations = 1;
        harness.derivation.interpret = (collection, page) -> parsed(page, 100, 2, "fixture-1");

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertThat(result.reasonCode()).isEqualTo("EXHAUSTED");
        assertThat(result.status()).isEqualTo("INCOMPLETE");
        assertThat(harness.client.calls).isEqualTo(1);
        assertThat(harness.budget.reservations).hasSize(2);
        assertThat(harness.store.pages(harness.command.id())).hasSize(1)
                .extracting(CalendarPageRecord::responseCode).containsExactly("RECEIVED");
        assertThat(harness.derivation.applied).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = ResultCode.class, names = {"SUSPENDED", "RATE_LIMITED", "ALREADY_COMMITTED"})
    void authorizationRefusalNeverBecomesANewEmission(ResultCode refusal) {
        Harness harness = new Harness();
        harness.budget.authorizationCode = refusal;

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertThat(result.reasonCode()).isEqualTo(refusal.name());
        assertThat(harness.client.calls).isZero();
        assertThat(harness.store.pages(harness.command.id())).hasSize(1)
                .extracting(CalendarPageRecord::responseCode).containsExactly("PENDING");
        assertThat(harness.derivation.applied).isEmpty();
    }

    @Test
    void inactiveCapabilityDoesNotEvenInspectClientAvailabilityOrCreateCollection() {
        Harness harness = new Harness();
        harness.registry.enabled = false;

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertThat(result.reasonCode()).isEqualTo("CAPABILITY_INACTIVE");
        assertThat(harness.client.calls).isZero();
        assertThat(harness.client.availabilityChecks).isZero();
        assertThat(harness.budget.reservations).isEmpty();
        assertThat(harness.store.collections).isEmpty();
    }

    @Test
    void missingExactCapabilityDoesNotFallbackToAnotherSeason() {
        Harness harness = new Harness();
        var other = new ProviderCapabilityKey(KEY.provider(), KEY.providerCompetitionId(), "2027/2028",
                KEY.sourcePhase(), KEY.dataType());
        var command = new CalendarCollectionCommand(UUID.randomUUID(), harness.command.windowId(), other,
                harness.command.date(), 2027);

        assertThat(harness.service().collect(command).reasonCode()).isEqualTo("CAPABILITY_INACTIVE");
        assertThat(harness.client.calls).isZero();
        assertThat(harness.budget.reservations).isEmpty();
    }

    @Test
    void disabledTransportAndWrongBudgetProviderRemainClosedBeforeReservation() {
        Harness disabled = new Harness();
        disabled.client.enabled = false;
        assertThat(disabled.service().collect(disabled.command).reasonCode()).isEqualTo("PROVIDER_DISABLED");
        assertThat(disabled.budget.reservations).isEmpty();
        assertThat(disabled.client.calls).isZero();

        Harness wrongWindow = new Harness();
        wrongWindow.store.matchingWindow = false;
        assertThat(wrongWindow.service().collect(wrongWindow.command).reasonCode()).isEqualTo("WINDOW_PROVIDER_MISMATCH");
        assertThat(wrongWindow.budget.reservations).isEmpty();
        assertThat(wrongWindow.client.calls).isZero();
    }

    @Test
    void unexpectedTransportExceptionIsRecordedOnceAsUncertainWithoutLeakingItsMessage() {
        Harness harness = new Harness();
        harness.client.throwFailure = true;

        CalendarCollectionResult result = harness.service().collect(harness.command);

        assertThat(result.reasonCode()).isEqualTo("UNCERTAIN_RESPONSE");
        assertThat(harness.client.calls).isEqualTo(1);
        assertThat(harness.evidence.responses).hasSize(1);
        assertThat(harness.evidence.responses.getFirst().failureCode()).isEqualTo("UNCERTAIN_RESPONSE");
        assertThat(harness.evidence.responses.getFirst().body()).isEmpty();
        assertThat(harness.derivation.applied).isEmpty();
    }

    private static void assertPaginationFailure(Harness harness, CalendarCollectionResult result) {
        assertThat(result.status()).isEqualTo("INCOMPLETE");
        assertThat(result.reasonCode()).isEqualTo("INCOMPLETE_PAGINATION");
        assertThat(harness.client.calls).isEqualTo(2);
        assertThat(harness.store.pages(harness.command.id())).hasSize(2);
        assertThat(harness.derivation.applied).hasSize(1);
    }

    private static ParsedCalendarPage parsed(CalendarPageRecord page, Integer next, long total, String fixtureId) {
        return new ParsedCalendarPage(new CalendarSnapshot(CalendarSnapshotSchemas.CANONICAL_V3, KEY.provider(), NOW,
                List.of(new DiscoveredFixture(fixtureId, NOW.plusSeconds(3600), "Synthetic home", "Synthetic away"))),
                next, total);
    }

    private static final class Harness {
        final CalendarCollectionCommand command = new CalendarCollectionCommand(UUID.randomUUID(), UUID.randomUUID(),
                KEY, LocalDate.of(2026, 9, 18), 2026);
        final MemoryStore store = new MemoryStore();
        final FakeRegistry registry = new FakeRegistry();
        final FakeBudget budget = new FakeBudget();
        final FakeClient client = new FakeClient();
        final FakeEvidence evidence = new FakeEvidence(store);
        final FakeDerivation derivation = new FakeDerivation();

        CalendarCollectionService service() {
            return new CalendarCollectionService(registry, budget, store, List.of(client), evidence, derivation, CLOCK);
        }
    }

    private static final class FakeRegistry implements ProviderCapabilityRegistry {
        boolean enabled = true;

        @Override public String registryVersion() { return "synthetic-v1"; }
        @Override public String documentSha256() { return "b".repeat(64); }
        @Override public Optional<ProviderCapability> find(ProviderCapabilityKey key) {
            return KEY.equals(key) ? Optional.of(new ProviderCapability(KEY,
                    new CapabilityRouteKey("PPL", KEY.sourceSeason(), KEY.sourcePhase(), CapabilityDataType.CALENDAR),
                    CapabilityStatus.PRIMARY, CapabilityAuthorityRole.PRIMARY, enabled,
                    List.of(new CapabilityEvidenceReference("synthetic-test", NOW, "a".repeat(64))))) : Optional.empty();
        }
        @Override public List<ProviderCapability> candidates(CapabilityRouteKey route) { return List.of(); }
    }

    private static final class FakeBudget extends ProviderBudgetService {
        final List<BudgetCommands.Reserve> reservations = new ArrayList<>();
        final List<UUID> authorizations = new ArrayList<>();
        final Map<UUID, Intent> intents = new HashMap<>();
        ResultCode reservationCode = ResultCode.OK;
        ResultCode authorizationCode = ResultCode.OK;
        int successfulReservations = Integer.MAX_VALUE;

        FakeBudget() { super(null); }

        @Override public BudgetActionResult reserve(BudgetCommands.Reserve command) {
            reservations.add(command);
            if (reservationCode != ResultCode.OK) {
                return BudgetActionResult.refused(reservationCode, command.windowId());
            }
            if (reservations.size() > successfulReservations) {
                return BudgetActionResult.refused(ResultCode.EXHAUSTED, command.windowId());
            }
            Intent intent = new Intent(UUID.randomUUID(), command.windowId(), command.idempotencyKey(),
                    command.logicalEndpoint(), command.requestSha256(), IntentState.RESERVED, 1, NOW, NOW,
                    null, null, null, null);
            intents.put(intent.id(), intent);
            return new BudgetActionResult(ResultCode.OK, command.windowId(), intent, true);
        }

        @Override public BudgetActionResult authorizeSend(UUID intentId) {
            authorizations.add(intentId);
            Intent intent = intents.get(intentId);
            return new BudgetActionResult(authorizationCode, intent.windowId(), intent, authorizationCode == ResultCode.OK);
        }
    }

    private static final class FakeClient implements CalendarPageClient {
        int calls;
        int availabilityChecks;
        boolean enabled = true;
        boolean repeatBody;
        boolean throwFailure;

        @Override public String provider() { return KEY.provider(); }
        @Override public boolean available() { availabilityChecks++; return enabled; }
        @Override public CalendarPageResponse fetch(CalendarPageRequest request) {
            calls++;
            if (throwFailure) {
                throw new IllegalStateException("synthetic transport detail must not escape");
            }
            byte[] body = ("synthetic page " + (repeatBody ? 1 : calls)).getBytes(StandardCharsets.UTF_8);
            return new CalendarPageResponse(NOW, NOW, 200, body, null, null);
        }
    }

    private static final class FakeEvidence extends CalendarEvidenceTransactions {
        private final MemoryStore memory;
        final List<String> refusals = new ArrayList<>();
        final List<CalendarPageResponse> responses = new ArrayList<>();

        FakeEvidence(MemoryStore memory) { super(memory, null, null, CLOCK, null); this.memory = memory; }

        @Override public void recordResponse(CalendarPageRecord page, String provider, CalendarPageResponse response) {
            responses.add(response);
            String code = response.failureCode() == null ? "RECEIVED" : response.failureCode();
            memory.pageRecords.put(page.id(), new CalendarPageRecord(page.id(), page.collectionId(), page.intentId(),
                    page.auditId(), page.pageNumber(), page.pageOffset(), page.pageLimit(), page.connectorVersion(),
                    UUID.randomUUID(), SnapshotHasher.sha256(response.body()), response.requestedAt(), response.receivedAt(),
                    response.httpStatus() == 0 ? null : response.httpStatus(), response.quotaRemaining(), code));
        }

        @Override public void refused(CalendarPageRecord page, String parserVersion, String code) { refusals.add(code); }
    }

    private static final class FakeDerivation extends CalendarDerivationService {
        BiFunction<CalendarCollectionRecord, CalendarPageRecord, ParsedCalendarPage> interpret =
                (collection, page) -> parsed(page, null, 1, "fixture");
        final List<UUID> applied = new ArrayList<>();

        FakeDerivation() { super(null, List.of(), null, null); }
        @Override public ParsedCalendarPage parse(CalendarCollectionRecord collection, CalendarPageRecord page) {
            return interpret.apply(collection, page);
        }
        @Override public void apply(CalendarCollectionRecord collection, CalendarPageRecord page, ParsedCalendarPage parsed) {
            applied.add(page.id());
        }
    }

    private static final class MemoryStore implements CalendarCollectionStore {
        final Map<UUID, CalendarCollectionRecord> collections = new HashMap<>();
        final Map<UUID, CalendarPageRecord> pageRecords = new HashMap<>();
        boolean matchingWindow = true;

        @Override public StoredCollection createAndResolve(CalendarCollectionRecord candidate) {
            CalendarCollectionRecord existing = collections.putIfAbsent(candidate.id(), candidate);
            return new StoredCollection(existing == null ? candidate : existing, existing == null);
        }
        @Override public Optional<CalendarCollectionRecord> findCollection(UUID id) { return Optional.ofNullable(collections.get(id)); }
        @Override public void finish(UUID id, String status, String reason, Instant now) {
            CalendarCollectionRecord prior = collections.get(id);
            collections.put(id, new CalendarCollectionRecord(prior.id(), prior.windowId(), prior.capability(), prior.date(),
                    prior.seasonStartYear(), prior.commandSha256(), prior.registrySha256(), status, reason, prior.createdAt(), now));
        }
        @Override public CalendarPageRecord preparePage(UUID collectionId, UUID intentId, int number, int offset,
                int limit, String connector, Instant now) {
            CalendarPageRecord page = new CalendarPageRecord(UUID.randomUUID(), collectionId, intentId, UUID.randomUUID(),
                    number, offset, limit, connector, null, null, now, null, null, null, "PENDING");
            pageRecords.put(page.id(), page);
            return page;
        }
        @Override public void recordResponse(UUID id, RawSnapshot snapshot, Instant requested, Instant received,
                Integer status, Long quota, String code) { throw new UnsupportedOperationException(); }
        @Override public List<CalendarPageRecord> pages(UUID collectionId) {
            return pageRecords.values().stream().filter(page -> page.collectionId().equals(collectionId))
                    .sorted(java.util.Comparator.comparingInt(CalendarPageRecord::pageNumber)).toList();
        }
        @Override public Optional<CalendarPageRecord> findPage(UUID id) { return Optional.ofNullable(pageRecords.get(id)); }
        @Override public Optional<RawSnapshot> readRaw(UUID id) { return Optional.empty(); }
        @Override public void appendDerivation(CalendarDerivationRecord record) { throw new UnsupportedOperationException(); }
        @Override public List<CalendarDerivationRecord> derivations(UUID id) { return List.of(); }
        @Override public boolean windowMatchesProvider(UUID id, String provider) { return matchingWindow; }
    }
}
