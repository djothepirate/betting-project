package com.bettingproject.collection.application.enrichment;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.application.capability.ProviderRoutingService;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentDispatchWindow;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepStatus;
import com.bettingproject.operations.application.jobs.JobTransactions;
import com.bettingproject.operations.domain.JobModel.Submission;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates one durable job per exact, already admitted milestone; it never authorizes a provider call. */
@Service
@Profile({"control-api", "batch-worker"})
public class EnrichmentJobPlanner implements EnrichmentDispatchPlanner {
    private static final List<EnrichmentFamily> POSTMATCH_FAMILIES = List.of(
            EnrichmentFamily.MATCH_DETAIL, EnrichmentFamily.TEAM_STATS,
            EnrichmentFamily.EVENTS, EnrichmentFamily.PLAYER_STATS);

    private final EnrichmentCollectionStore contexts;
    private final EnrichmentPlanExecutionStore steps;
    private final EnrichmentJobInputStore inputs;
    private final ProviderCapabilityRegistry registry;
    private final ProviderRoutingService routing;
    private final JobTransactions jobs;
    private final List<EnrichmentProviderClient> clients;
    private final List<EnrichmentPayloadParser<?>> parsers;
    private final Clock clock;

    public EnrichmentJobPlanner(EnrichmentCollectionStore contexts, EnrichmentPlanExecutionStore steps,
            EnrichmentJobInputStore inputs, ProviderCapabilityRegistry registry, ProviderRoutingService routing,
            JobTransactions jobs, List<EnrichmentProviderClient> clients,
            List<EnrichmentPayloadParser<?>> parsers, Clock clock) {
        this.contexts = Objects.requireNonNull(contexts);
        this.steps = Objects.requireNonNull(steps);
        this.inputs = Objects.requireNonNull(inputs);
        this.registry = Objects.requireNonNull(registry);
        this.routing = Objects.requireNonNull(routing);
        this.jobs = Objects.requireNonNull(jobs);
        this.clients = List.copyOf(clients);
        this.parsers = List.copyOf(parsers);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    @Override public void planAdmission(UUID admissionId) {
        for (EnrichmentPlanStepCode code : List.of(EnrichmentPlanStepCode.LINEUP_T_MINUS_30,
                EnrichmentPlanStepCode.LINEUP_T_MINUS_15, EnrichmentPlanStepCode.DETAIL_AT_KICKOFF,
                EnrichmentPlanStepCode.DETAIL_PLUS_45)) {
            plan(admissionId, code);
        }
    }

    @Transactional
    @Override public void planArmedPostmatch(UUID admissionId, List<EnrichmentPlanStepCode> armedSteps) {
        for (EnrichmentPlanStepCode code : List.copyOf(armedSteps)) {
            if (code != EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL
                    && code != EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60) {
                throw new IllegalArgumentException("only final-triggered steps may be dispatched here");
            }
            plan(admissionId, code);
        }
    }

    private void plan(UUID admissionId, EnrichmentPlanStepCode code) {
        var currentStatus = steps.stepStatus(admissionId, code).orElse(null);
        if (currentStatus != EnrichmentPlanStepStatus.PLANNED) { return; }
        var context = contexts.findContext(admissionId, code).orElse(null);
        if (context == null) {
            skip(admissionId, code, "CANONICAL_CONTEXT_UNAVAILABLE");
            return;
        }
        if (!context.planRegistrySha256().equals(registry.documentSha256())) {
            skip(admissionId, code, "REGISTRY_CHANGED");
            return;
        }

        ProviderCapability calendar = registry.find(new ProviderCapabilityKey(context.authorityProvider(),
                context.authorityCompetitionId(), context.authoritySourceSeason(), context.authoritySourcePhase(),
                CapabilityDataType.CALENDAR)).orElse(null);
        if (calendar == null || !calendar.operational()
                || calendar.authorityRole() != CapabilityAuthorityRole.PRIMARY) {
            skip(admissionId, code, "PRIMARY_CALENDAR_ROUTE_UNAVAILABLE");
            return;
        }

        List<EnrichmentFamily> wanted = switch (code) {
            case LINEUP_T_MINUS_30, LINEUP_T_MINUS_15 -> List.of(EnrichmentFamily.LINEUP);
            case DETAIL_AT_KICKOFF, DETAIL_PLUS_45 -> List.of(EnrichmentFamily.MATCH_DETAIL);
            case POSTMATCH_AFTER_FINAL, POSTMATCH_RECHECK_FINAL_PLUS_60 -> POSTMATCH_FAMILIES;
        };
        List<EnrichmentJobRoute> routes = new ArrayList<>();
        String exclusion = "NO_PRIMARY_ROUTE";
        for (EnrichmentFamily family : wanted) {
            CapabilityDataType type = CapabilityDataType.valueOf(family.name());
            CapabilityRouteKey requested = new CapabilityRouteKey(calendar.route().competitionCode(),
                    context.logicalSeason(), context.logicalPhase(), type);
            var selected = routing.route(requested).primary();
            if (selected.isEmpty()) { continue; }
            ProviderCapability capability = selected.get();
            if (!capability.route().equals(requested) || capability.authorityRole() != CapabilityAuthorityRole.PRIMARY
                    || !capability.operational()) { continue; }
            EnrichmentPayloadParser<?> parser = parsers.stream().filter(item ->
                    item.provider().equals(capability.key().provider()) && item.family() == family
                            && item.preferredForCollection())
                    .findFirst().orElse(null);
            if (parser == null) { exclusion = "PARSER_UNAVAILABLE"; continue; }
            EnrichmentProviderClient client = clients.stream().filter(item ->
                    item.provider().equals(capability.key().provider()) && item.supports(family))
                    .findFirst().orElse(null);
            if (client == null || !client.available()) { exclusion = "PROVIDER_DISABLED"; continue; }
            if (!contexts.budgetWindowMatchesProvider(context.planBudgetWindowId(), capability.key().provider())) {
                exclusion = "BUDGET_WINDOW_PROVIDER_MISMATCH";
                continue;
            }
            List<String> mappings = contexts.confirmedFixtureMappings(context.canonicalFixtureId(), capability.key());
            if (mappings.size() != 1 || !mappings.getFirst().matches("[0-9]+")) {
                exclusion = mappings.isEmpty() ? "CONFIRMED_FIXTURE_MAPPING_REQUIRED" : "AMBIGUOUS_FIXTURE_MAPPING";
                continue;
            }
            routes.add(new EnrichmentJobRoute(family, capability.key(), parser.version()));
        }

        if (routes.isEmpty()) {
            skip(admissionId, code, exclusion);
            return;
        }
        EnrichmentDispatchWindow dispatch;
        try {
            dispatch = EnrichmentDispatchWindow.forStep(context.step(), context.kickoffAt());
        }
        catch (IllegalArgumentException invalidSchedule) {
            skip(admissionId, code, "INVALID_DISPATCH_SCHEDULE");
            return;
        }
        String routeHash = routeHash(routes);
        String contentHash = SnapshotHasher.sha256(("enrichment-job-v1\n" + admissionId + "\n" + code + "\n"
                + context.planBudgetWindowId() + "\n" + registry.documentSha256() + "\n" + routeHash)
                .getBytes(StandardCharsets.UTF_8));
        var queued = jobs.enqueue(new Submission("enrichment-step:" + context.step().id(),
                EnrichmentJobInput.expectedType(code), contentHash, dispatch.opensAt(), 3));
        EnrichmentJobInput input = new EnrichmentJobInput(queued.job().id(), admissionId, context.step().id(), code,
                EnrichmentJobInput.expectedType(code), context.planBudgetWindowId(), registry.documentSha256(),
                routeHash, routes, clock.instant().truncatedTo(ChronoUnit.MICROS));
        inputs.insertIfAbsentAndResolve(input);
        if (!steps.markStep(admissionId, code, EnrichmentPlanStepStatus.QUEUED, null)
                && steps.stepStatus(admissionId, code).orElse(null) != EnrichmentPlanStepStatus.QUEUED) {
            throw new IllegalStateException("enrichment step could not be queued atomically");
        }
    }

    private void skip(UUID admissionId, EnrichmentPlanStepCode code, String reason) {
        if (!steps.markStep(admissionId, code, EnrichmentPlanStepStatus.SKIPPED, reason)
                && steps.stepStatus(admissionId, code).orElse(null) == EnrichmentPlanStepStatus.PLANNED) {
            throw new IllegalStateException("enrichment step skip compare-and-set failed");
        }
    }

    private static String routeHash(List<EnrichmentJobRoute> routes) {
        String canonical = routes.stream().sorted((left, right) -> left.family().compareTo(right.family()))
                .map(route -> route.family() + "|" + route.capability().provider() + "|"
                        + route.capability().providerCompetitionId() + "|" + route.capability().sourceSeason() + "|"
                        + route.capability().sourcePhase() + "|" + route.parserVersion() + "\n")
                .reduce("enrichment-routes-v1\n", String::concat);
        return SnapshotHasher.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
