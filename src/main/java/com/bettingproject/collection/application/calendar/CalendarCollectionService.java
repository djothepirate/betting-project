package com.bettingproject.collection.application.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        String fingerprint = fingerprint(command);
        var prior = store.findCollection(command.id());
        if (prior.isPresent()) {
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
        var candidate = new CalendarCollectionRecord(command.id(), command.windowId(), command.capability(),
                command.date(), command.seasonStartYear(), fingerprint, registry.documentSha256(),
                "RUNNING", null, now, now);
        var stored = store.createAndResolve(candidate);
        if (!stored.inserted()) {
            return existing(stored.record(), fingerprint);
        }
        int offset = 0;
        Long total = null;
        Set<String> fixtureIds = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (int number = 1; number <= 100; number++) {
            var request = new CalendarPageRequest(command.capability(), command.date(), command.seasonStartYear(),
                    offset, 100);
            var reserved = budget.reserve(new BudgetCommands.Reserve(command.windowId(),
                    "calendar:" + command.id() + ":" + offset, "calendar/matches",
                    hash(fingerprint + ":" + offset + ":100")));
            if (reserved.code() != ResultCode.OK) {
                return finish(candidate, "INCOMPLETE", reserved.code().name());
            }
            var page = store.preparePage(command.id(), reserved.intent().id(), number, offset, 100,
                    CONNECTOR_VERSION, now());
            var permit = budget.authorizeSend(reserved.intent().id());
            if (permit.code() != ResultCode.OK || !permit.created()) {
                return finish(candidate, "INCOMPLETE", permit.code().name());
            }
            CalendarPageResponse response;
            Instant requestedAt = now();
            try {
                response = client.fetch(request);
            }
            catch (RuntimeException failure) {
                // Never expose an exception message or repeat a possibly sent request.
                response = new CalendarPageResponse(requestedAt, now(), 0, new byte[0], null, "UNCERTAIN_RESPONSE");
            }
            evidence.recordResponse(page, command.capability().provider(), response);
            page = store.findPage(page.id()).orElseThrow();
            if (!"RECEIVED".equals(page.responseCode())) {
                return finish(candidate, "INCOMPLETE", page.responseCode());
            }
            ParsedCalendarPage parsed;
            try {
                parsed = derivation.parse(candidate, page);
            }
            catch (CalendarPageParseException failure) {
                return finish(candidate, "INCOMPLETE", failure.code());
            }
            boolean coherent = (total == null || total == parsed.totalCount()) && hashes.add(page.rawSha256());
            for (var fixture : parsed.snapshot().fixtures()) {
                coherent &= fixtureIds.add(fixture.providerFixtureId());
            }
            if (!coherent || parsed.nextOffset() != null && parsed.nextOffset() <= offset) {
                evidence.refused(page, "collection-pagination-v1", "INCOMPLETE_PAGINATION");
                return finish(candidate, "INCOMPLETE", "INCOMPLETE_PAGINATION");
            }
            total = parsed.totalCount();
            derivation.apply(candidate, page, parsed);
            if (parsed.nextOffset() == null) {
                return finish(candidate, "COMPLETED", null);
            }
            offset = parsed.nextOffset();
        }
        return finish(candidate, "INCOMPLETE", "PAGE_LIMIT");
    }

    private CalendarCollectionResult existing(CalendarCollectionRecord record, String fingerprint) {
        return fingerprint.equals(record.commandSha256())
                ? new CalendarCollectionResult(record.id(), record.status(), record.reasonCode())
                : new CalendarCollectionResult(record.id(), "REFUSED", "IDEMPOTENCY_CONFLICT");
    }

    private CalendarCollectionResult finish(CalendarCollectionRecord record, String status, String reason) {
        store.finish(record.id(), status, reason, now());
        return new CalendarCollectionResult(record.id(), status, reason);
    }

    private String fingerprint(CalendarCollectionCommand command) {
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
}
