package com.bettingproject.collection.application.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.application.capability.ProviderRoutingService;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.collection.domain.capability.*;
import com.bettingproject.operations.application.jobs.JobTransactions;
import com.bettingproject.operations.domain.JobModel.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit dated plans, never a quota-window factory or an implicit recurring schedule. */
@Service
@Profile("control-api")
@Transactional
public class CalendarJobPlanningService {
    public record Binding(ProviderCapabilityKey capability, UUID windowId, int seasonStartYear) {
        public Binding {
            Objects.requireNonNull(windowId);
            new CalendarPageRequest(capability, LocalDate.of(2026, 1, 1), seasonStartYear, 0, 100);
        }
    }
    public record RoutePlan(CapabilityRouteKey route, List<Binding> bindings) {
        public RoutePlan {
            Objects.requireNonNull(route);
            bindings = List.copyOf(bindings);
            if (!Set.of("PPL", "PD", "DED", "ELC").contains(route.competitionCode())
                    || route.dataType() != CapabilityDataType.CALENDAR || bindings.size() > 10
                    || bindings.stream().map(Binding::capability).distinct().count() != bindings.size()) {
                throw new IllegalArgumentException("Invalid calendar route plan");
            }
        }
    }
    public record Planned(String provider, UUID jobId, String code) { }

    private final ProviderCapabilityRegistry registry;
    private final ProviderRoutingService routing;
    private final JobTransactions jobs;
    private final CalendarJobInputStore inputs;
    private final CalendarCollectionStore collections;
    private final CalendarDerivationService derivation;

    public CalendarJobPlanningService(ProviderCapabilityRegistry registry, ProviderRoutingService routing,
            JobTransactions jobs, CalendarJobInputStore inputs, CalendarCollectionStore collections,
            CalendarDerivationService derivation) {
        this.registry = registry; this.routing = routing; this.jobs = jobs; this.inputs = inputs;
        this.collections = collections; this.derivation = derivation;
    }

    public List<Planned> planDay(UUID planId, LocalDate date, Instant dueAt, List<RoutePlan> routes) {
        Objects.requireNonNull(planId); Objects.requireNonNull(date); Objects.requireNonNull(dueAt);
        routes = List.copyOf(routes);
        if (routes.isEmpty() || routes.size() > 4 || routes.stream().map(RoutePlan::route).distinct().count() != routes.size()) {
            throw new IllegalArgumentException("Invalid calendar daily plan");
        }
        var result = new ArrayList<Planned>();
        for (RoutePlan plan : routes) {
            var selected = routing.route(plan.route());
            if (selected.primary().isEmpty()) { result.add(new Planned(null, null, "NO_PRIMARY")); continue; }
            var capabilities = new ArrayList<ProviderCapability>();
            capabilities.add(selected.primary().orElseThrow());
            capabilities.addAll(selected.controls());
            for (ProviderCapability capability : capabilities) {
                var key = capability.key();
                var binding = plan.bindings().stream().filter(item -> item.capability().equals(key)).findFirst();
                if (binding.isEmpty()) { result.add(new Planned(key.provider(), null, "MISSING_EXACT_WINDOW_BINDING")); continue; }
                Binding bound = binding.get();
                var command = new CalendarCollectionCommand(UUID.randomUUID(), bound.windowId(), key, date, bound.seasonStartYear());
                // The plan identity and exact provider key are stable across retries, not the budget window.
                String keyHash = digest(planId.toString(), key.provider(), key.providerCompetitionId(), key.sourceSeason(), key.sourcePhase());
                Enqueued queued = enqueueDiscovery("calendar-plan:" + keyHash, command, dueAt, 3);
                result.add(new Planned(key.provider(), queued.job().id(), queued.created() ? "CREATED" : "ALREADY_PLANNED"));
            }
        }
        return List.copyOf(result);
    }

    public Enqueued enqueueDiscovery(String key, CalendarCollectionCommand command, Instant dueAt, int maxAttempts) {
        var capability = registry.find(command.capability());
        if (capability.isEmpty() || !capability.get().operational()
                || !collections.windowMatchesProvider(command.windowId(), command.capability().provider())) {
            throw new IllegalArgumentException("Calendar capability or window unavailable");
        }
        String parser = derivation.parserVersion(command.capability().provider());
        String fingerprint = digest("calendar-job-v1", CalendarCollectionService.fingerprint(command), registry.documentSha256(), parser);
        Enqueued queued = jobs.enqueue(new Submission(key, Type.CALENDAR_DISCOVERY, fingerprint, dueAt, maxAttempts));
        var canonical = new CalendarCollectionCommand(queued.job().id(), command.windowId(), command.capability(),
                command.date(), command.seasonStartYear());
        inputs.insertIfAbsent(new CalendarJobInput(queued.job().id(), Type.CALENDAR_DISCOVERY, canonical,
                null, parser, registry.documentSha256()));
        return queued;
    }

    public Enqueued enqueueReplay(String key, UUID pageId, Instant dueAt, int maxAttempts) {
        var page = collections.findPage(pageId).orElseThrow(() -> new IllegalArgumentException("Page unavailable"));
        if (!"RECEIVED".equals(page.responseCode())) { throw new IllegalArgumentException("Page has no complete response"); }
        var collection = collections.findCollection(page.collectionId()).orElseThrow();
        String parser = derivation.parserVersion(collection.capability().provider());
        String fingerprint = digest("calendar-replay-job-v1", pageId.toString(), parser, registry.documentSha256());
        Enqueued queued = jobs.enqueue(new Submission(key, Type.REPLAY_NORMALIZATION, fingerprint, dueAt, maxAttempts));
        inputs.insertIfAbsent(new CalendarJobInput(queued.job().id(), Type.REPLAY_NORMALIZATION, null,
                pageId, parser, registry.documentSha256()));
        return queued;
    }

    private static String digest(String... values) {
        StringBuilder source = new StringBuilder();
        for (String value : values) { source.append(value.length()).append(':').append(value); }
        return SnapshotHasher.sha256(source.toString().getBytes(StandardCharsets.UTF_8));
    }
}
