package com.bettingproject.collection.application.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import com.bettingproject.collection.application.budget.BudgetActionResult;
import com.bettingproject.collection.application.budget.BudgetCommands;
import com.bettingproject.collection.application.budget.ProviderBudgetService;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Synchronous internal use case, not a scheduler. Every external effect is durably permitted once. */
@Service
@Profile({"control-api", "batch-worker"})
@Transactional(propagation = Propagation.NEVER)
public class CalendarCollectionService {
    private static final String CONNECTOR_VERSION = "calendar-http-v1";
    private final ProviderCapabilityRegistry registry;
    private final ProviderBudgetService budget;
    private final CalendarCollectionStore store;
    private final List<CalendarPageClient> clients;
    private final CalendarEvidenceTransactions evidence;
    private final CalendarDerivationService derivation;
    private final Clock clock;

    public CalendarCollectionService(ProviderCapabilityRegistry registry, ProviderBudgetService budget,
            CalendarCollectionStore store, List<CalendarPageClient> clients,
            CalendarEvidenceTransactions evidence, CalendarDerivationService derivation, Clock clock) {
        this.registry = registry;
        this.budget = budget;
        this.store = store;
        this.clients = List.copyOf(clients);
        this.evidence = evidence;
        this.derivation = derivation;
        this.clock = clock;
    }

    public CalendarCollectionResult collect(CalendarCollectionCommand command) {
        return collect(command, new CalendarExecution() {
            public boolean resumable() { return false; }
            public <T> T atomic(Supplier<T> work) { return work.get(); }
            public BudgetActionResult reserve(BudgetCommands.Reserve input) { return budget.reserve(input); }
            public BudgetActionResult authorize(UUID id) { return budget.authorizeSend(id); }
            public BudgetActionResult uncertain(UUID id) { return budget.markUncertain(id); }
        });
    }

    public CalendarCollectionResult collect(CalendarCollectionCommand command, CalendarExecution execution) {
        String fingerprint = fingerprint(command);
        var prior = store.findCollection(command.id());
        if (prior.isPresent() && (!execution.resumable() || !"RUNNING".equals(prior.get().status())
                || !prior.get().commandSha256().equals(fingerprint))) {
            return existing(prior.get(), fingerprint);
        }
        var capability = registry.find(command.capability());
        if (capability.isEmpty() || !capability.get().operational()) {
            return new CalendarCollectionResult(command.id(), "REFUSED", "CAPABILITY_INACTIVE");
        }
        var client = clients.stream().filter(item -> item.provider().equals(command.capability().provider()))
                .findFirst().orElse(null);
        if (client == null || !client.available()) {
            return new CalendarCollectionResult(command.id(), "REFUSED", "PROVIDER_DISABLED");
        }
        if (!store.windowMatchesProvider(command.windowId(), command.capability().provider())) {
            return new CalendarCollectionResult(command.id(), "REFUSED", "WINDOW_PROVIDER_MISMATCH");
        }
        Instant now = now();
        var proposed = new CalendarCollectionRecord(command.id(), command.windowId(), command.capability(),
                command.date(), command.seasonStartYear(), fingerprint, registry.documentSha256(),
                "RUNNING", null, now, now);
        var stored = execution.atomic(() -> store.createAndResolve(proposed));
        if (!stored.inserted() && (!execution.resumable() || !"RUNNING".equals(stored.record().status())
                || !stored.record().commandSha256().equals(fingerprint))) {
            return existing(stored.record(), fingerprint);
        }
        var candidate = stored.record();
        if (!candidate.registrySha256().equals(registry.documentSha256())) {
            return finish(candidate, "INCOMPLETE", "REGISTRY_CHANGED", execution);
        }
        int offset = 0;
        Long total = null;
        Set<String> fixtureIds = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (int number = 1; number <= 100; number++) {
            var request = new CalendarPageRequest(command.capability(), command.date(), command.seasonStartYear(),
                    offset, 100);
            final int pageOffset = offset;
            final int pageNumber = number;
            var priorPage = store.pages(command.id()).stream().filter(p -> p.pageOffset() == pageOffset).findFirst();
            CalendarPageRecord page = priorPage.orElse(null);
            if (page == null || "PENDING".equals(page.responseCode())) {
                Dispatch dispatch = execution.atomic(() -> {
                    var reserved = execution.reserve(new BudgetCommands.Reserve(command.windowId(),
                            "calendar:" + command.id() + ":" + pageOffset, "calendar/matches",
                            hash(fingerprint + ":" + pageOffset + ":100")));
                    if (reserved.code() != ResultCode.OK) { return new Dispatch(null, reserved); }
                    var prepared = store.preparePage(command.id(), reserved.intent().id(), pageNumber, pageOffset, 100,
                            CONNECTOR_VERSION, now());
                    var permit = execution.authorize(reserved.intent().id());
                    if (execution.resumable() && permit.code() == ResultCode.ALREADY_COMMITTED) {
                        var uncertain = execution.uncertain(reserved.intent().id());
                        if (uncertain.code() != ResultCode.OK) {
                            throw new IllegalStateException("Unrecorded calendar send invariant");
                        }
                        store.markMissingResponse(prepared.id(), now());
                    }
                    return new Dispatch(prepared, permit);
                });
                if (dispatch.permit().code() != ResultCode.OK || !dispatch.permit().created()) {
                    String reason = execution.resumable() && dispatch.permit().code() == ResultCode.ALREADY_COMMITTED
                            ? "SEND_UNCERTAIN" : dispatch.permit().code().name();
                    if (execution.resumable() && "RATE_LIMITED".equals(reason)) {
                        return new CalendarCollectionResult(candidate.id(), "DEFERRED", reason);
                    }
                    return finish(candidate, "INCOMPLETE", reason, execution);
                }
                CalendarPageResponse response;
                Instant requestedAt = now();
                try {
                    execution.atomic(() -> null); // Recheck ownership immediately before the external boundary.
                    response = client.fetch(request);
                }
                catch (com.bettingproject.operations.application.jobs.JobLeaseLostException lost) { throw lost; }
                catch (RuntimeException failure) {
                    response = new CalendarPageResponse(requestedAt, now(), 0, new byte[0], null, "UNCERTAIN_RESPONSE");
                }
                CalendarPageResponse received = response;
                execution.atomic(() -> {
                    evidence.recordResponse(dispatch.page(), command.capability().provider(), received);
                    return null;
                });
                page = store.findPage(dispatch.page().id()).orElseThrow();
            }
            if (!"RECEIVED".equals(page.responseCode())) {
                return finish(candidate, "INCOMPLETE", page.responseCode(), execution);
            }
            final CalendarPageRecord receivedPage = page;
            Interpretation interpretation = execution.atomic(() -> {
                try { return new Interpretation(derivation.parse(candidate, receivedPage), null); }
                catch (CalendarPageParseException failure) { return new Interpretation(null, failure.code()); }
            });
            if (interpretation.error() != null) {
                return finish(candidate, "INCOMPLETE", interpretation.error(), execution);
            }
            ParsedCalendarPage parsed = interpretation.parsed();
            boolean coherent = (total == null || total == parsed.totalCount()) && hashes.add(page.rawSha256());
            for (var fixture : parsed.snapshot().fixtures()) {
                coherent &= fixtureIds.add(fixture.providerFixtureId());
            }
            if (!coherent || parsed.nextOffset() != null && parsed.nextOffset() <= offset) {
                execution.atomic(() -> { evidence.refused(receivedPage, "collection-pagination-v1", "INCOMPLETE_PAGINATION"); return null; });
                return finish(candidate, "INCOMPLETE", "INCOMPLETE_PAGINATION", execution);
            }
            total = parsed.totalCount();
            execution.atomic(() -> {
                boolean applied = execution.resumable() && store.derivations(receivedPage.id()).stream()
                        .anyMatch(item -> "APPLIED".equals(item.outcome()));
                if (!applied) { derivation.apply(candidate, receivedPage, parsed); }
                return null;
            });
            if (parsed.nextOffset() == null) {
                return finish(candidate, "COMPLETED", null, execution);
            }
            offset = parsed.nextOffset();
        }
        return finish(candidate, "INCOMPLETE", "PAGE_LIMIT", execution);
    }

    private CalendarCollectionResult existing(CalendarCollectionRecord record, String fingerprint) {
        return fingerprint.equals(record.commandSha256())
                ? new CalendarCollectionResult(record.id(), record.status(), record.reasonCode())
                : new CalendarCollectionResult(record.id(), "REFUSED", "IDEMPOTENCY_CONFLICT");
    }

    private CalendarCollectionResult finish(CalendarCollectionRecord record, String status, String reason, CalendarExecution execution) {
        execution.atomic(() -> { store.finish(record.id(), status, reason, now()); return null; });
        return new CalendarCollectionResult(record.id(), status, reason);
    }

    public static String fingerprint(CalendarCollectionCommand command) {
        var key = command.capability();
        StringBuilder canonical = new StringBuilder("calendar-collection-v1");
        for (String value : List.of(command.windowId().toString(), key.provider(), key.providerCompetitionId(),
                key.sourceSeason(), key.sourcePhase(), key.dataType().name(), command.date().toString(),
                Integer.toString(command.seasonStartYear()))) {
            canonical.append('|').append(value.length()).append(':').append(value);
        }
        return hash(canonical.toString());
    }

    private static String hash(String value) {
        return SnapshotHasher.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record Dispatch(CalendarPageRecord page, BudgetActionResult permit) { }
    private record Interpretation(ParsedCalendarPage parsed, String error) { }
}
