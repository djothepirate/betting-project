package com.bettingproject.collection.application.enrichment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.bettingproject.collection.application.budget.BudgetCommands;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.CapabilityStatus;
import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Synchronous one-attempt use case. It never schedules, polls, or retries a provider request. */
@Service
@Profile({"control-api", "batch-worker"})
@Transactional(propagation = Propagation.NEVER)
public class EnrichmentCollectionService {
    private final ProviderCapabilityRegistry registry;
    private final EnrichmentCollectionStore store;
    private final EnrichmentCollectionTransactions transactions;
    private final EnrichmentDerivationService derivations;
    private final List<EnrichmentProviderClient> clients;
    private final List<EnrichmentPayloadParser<?>> parsers;
    private final Clock clock;

    public EnrichmentCollectionService(ProviderCapabilityRegistry registry, EnrichmentCollectionStore store,
            EnrichmentCollectionTransactions transactions, EnrichmentDerivationService derivations,
            List<EnrichmentProviderClient> clients, List<EnrichmentPayloadParser<?>> parsers, Clock clock) {
        this.registry = registry;
        this.store = store;
        this.transactions = transactions;
        this.derivations = derivations;
        this.clients = List.copyOf(clients);
        this.parsers = List.copyOf(parsers);
        this.clock = clock;
    }

    public EnrichmentCollectionResult collect(EnrichmentCollectionCommand command) {
        return collect(command, null);
    }

    public EnrichmentCollectionResult collect(EnrichmentCollectionCommand command, String expectedParserVersion) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Enrichment collection cannot run inside a caller transaction");
        }
        EnrichmentCollectionContext stored = store.findContext(command.admissionId(), command.stepCode()).orElse(null);
        if (stored == null) { return EnrichmentCollectionResult.refused("ADMISSION_STEP_NOT_FOUND"); }
        EnrichmentCollectionContext context = resolveLogicalContext(stored);
        if (context == null) { return EnrichmentCollectionResult.refused("CALENDAR_ROUTE_UNAVAILABLE"); }
        if (command.family() == EnrichmentFamily.LINEUP && !clock.instant().isBefore(context.kickoffAt())) {
            return EnrichmentCollectionResult.refused("LINEUP_PREMATCH_WINDOW_CLOSED");
        }
        if (!List.of("highlightly", "football-data.org").contains(command.capability().provider())) {
            return EnrichmentCollectionResult.refused("PROVIDER_NOT_ALLOWED");
        }
        if (!context.planRegistrySha256().equals(registry.documentSha256())) {
            return EnrichmentCollectionResult.refused("REGISTRY_CHANGED");
        }
        if (!familyAllowed(context.step().code(), command.family())) {
            return EnrichmentCollectionResult.refused("FAMILY_NOT_ALLOWED_FOR_STEP");
        }
        if ((context.step().code() == EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL
                || context.step().code() == EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60)
                && (context.step().scheduledAt() == null || context.step().triggerObservationId() == null)) {
            return EnrichmentCollectionResult.refused("POSTMATCH_FINAL_EVIDENCE_REQUIRED");
        }
        CapabilityDataType type = dataType(command.family());
        ProviderCapabilityKey requestedKey = new ProviderCapabilityKey(command.capability().provider(),
                command.capability().providerCompetitionId(), command.capability().sourceSeason(),
                command.capability().sourcePhase(), type);
        ProviderCapability capability = registry.find(requestedKey).orElse(null);
        CapabilityRouteKey expectedRoute = new CapabilityRouteKey(context.logicalCompetition(),
                context.logicalSeason(), context.logicalPhase(), type);
        if (capability == null || !capability.operational() || !capability.route().equals(expectedRoute)
                || !registry.candidates(expectedRoute).contains(capability)) {
            return EnrichmentCollectionResult.refused("CAPABILITY_NOT_ACTIVE_FOR_EXACT_ROUTE");
        }
        EnrichmentProviderClient client = clients.stream()
                .filter(item -> item.provider().equals(capability.key().provider())
                        && item.supports(command.family())).findFirst().orElse(null);
        if (client == null || !client.available()) {
            return EnrichmentCollectionResult.refused("PROVIDER_DISABLED");
        }
        EnrichmentPayloadParser<?> parser = parsers.stream()
                .filter(item -> item.provider().equals(capability.key().provider())
                        && item.family() == command.family())
                .filter(item -> expectedParserVersion == null
                        ? item.preferredForCollection() : expectedParserVersion.equals(item.version()))
                .findFirst().orElse(null);
        if (parser == null) { return EnrichmentCollectionResult.refused("PARSER_NOT_AVAILABLE"); }
        if (expectedParserVersion != null && !expectedParserVersion.equals(parser.version())) {
            return EnrichmentCollectionResult.refused("PARSER_CHANGED");
        }
        if (!store.budgetWindowMatchesProvider(command.budgetWindowId(), capability.key().provider())) {
            return EnrichmentCollectionResult.refused("WINDOW_PROVIDER_MISMATCH");
        }
        if ("highlightly".equals(capability.key().provider())
                && !command.budgetWindowId().equals(context.planBudgetWindowId())) {
            return EnrichmentCollectionResult.refused("PLAN_BUDGET_WINDOW_MISMATCH");
        }
        List<String> mappings = store.confirmedFixtureMappings(context.canonicalFixtureId(), capability.key());
        if (mappings.size() != 1 || !mappings.getFirst().matches("[0-9]+")) {
            return EnrichmentCollectionResult.refused(mappings.isEmpty()
                    ? "CONFIRMED_FIXTURE_MAPPING_REQUIRED" : "AMBIGUOUS_FIXTURE_MAPPING");
        }
        String providerFixtureId = mappings.getFirst();
        String endpoint = logicalEndpoint(command.family());
        Instant now = now();
        String idempotencyKey = idempotencyKey(command, capability.key().provider());
        String requestHash = requestHash(context, command, capability, providerFixtureId, endpoint, parser.version());
        BudgetCommands.Reserve reserve = new BudgetCommands.Reserve(command.budgetWindowId(), idempotencyKey,
                endpoint, requestHash);
        EnrichmentPreparation prepared = transactions.prepare(reserve, intent -> new EnrichmentCollectionAttempt(
                UUID.randomUUID(), command.admissionId(), context.step().id(), command.stepCode(),
                context.canonicalFixtureId(), command.budgetWindowId(), intent.id(), UUID.randomUUID(),
                capability.key(), context.logicalCompetition(), context.logicalSeason(), context.logicalPhase(),
                providerFixtureId, command.family(), endpoint, client.connectorVersion(), parser.version(), requestHash,
                EnrichmentAttemptState.RESERVED, null, null, context.kickoffAt(), now, null, null, null, null,
                null, now, now));
        if (prepared.budgetCode() != ResultCode.OK) {
            return EnrichmentCollectionResult.refused(prepared.budgetCode().name());
        }
        EnrichmentCollectionAttempt attempt = prepared.attempt();
        if (attempt.state() == EnrichmentAttemptState.RECEIVED && attempt.rawSnapshotId() != null) {
            return derivations.replay(attempt.id());
        }
        if (attempt.state() == EnrichmentAttemptState.HTTP_ERROR) {
            return new EnrichmentCollectionResult(EnrichmentCollectionResult.Code.HTTP_ERROR,
                    "HTTP_ERROR", attempt.id(), attempt.rawSnapshotId(), List.of());
        }
        EnrichmentAuthorization permit = transactions.authorize(attempt.id());
        if (!permit.maySend()) {
            EnrichmentCollectionAttempt current = store.findAttempt(attempt.id()).orElse(attempt);
            if (current.state() == EnrichmentAttemptState.RELEASED) {
                return new EnrichmentCollectionResult(EnrichmentCollectionResult.Code.REFUSED,
                        current.reasonCode(), current.id(), current.rawSnapshotId(), List.of());
            }
            boolean resultUnknown = current.state() == EnrichmentAttemptState.UNCERTAIN
                    || current.state() == EnrichmentAttemptState.COMMITTED_FOR_SEND
                    || current.state() == EnrichmentAttemptState.RESPONSE_INTERRUPTED
                    || permit.code() == ResultCode.ALREADY_COMMITTED;
            String reason = current.reasonCode() != null ? current.reasonCode()
                    : resultUnknown ? "SEND_RESULT_UNKNOWN" : permit.code().name();
            return new EnrichmentCollectionResult(resultUnknown
                    ? EnrichmentCollectionResult.Code.UNCERTAIN : EnrichmentCollectionResult.Code.REFUSED,
                    reason,
                    current.id(), current.rawSnapshotId(), List.of());
        }

        EnrichmentProviderResponse response;
        try {
            response = client.fetch(new EnrichmentProviderRequest(attempt.id(), client.provider(), providerFixtureId,
                    command.family(), endpoint, client.connectorVersion()));
        }
        catch (RuntimeException failure) {
            response = new EnrichmentProviderResponse(now, notBefore(now(), now), null, new byte[0], null,
                    "UNCERTAIN_RESPONSE");
        }
        String outcome = outcome(response);
        RawSnapshot raw = response.httpStatus() == null || response.failureCode() != null
                ? null : RawSnapshot.capture(client.provider(), endpoint, response.completedAt(), response.body(),
                        client.connectorVersion());
        UUID rawId = transactions.recordResponse(attempt.id(), raw, response, outcome);
        EnrichmentCollectionAttempt saved = store.findAttempt(attempt.id()).orElseThrow();
        if (saved.state() == EnrichmentAttemptState.RECEIVED && rawId != null) {
            return derivations.replay(attempt.id());
        }
        EnrichmentCollectionResult.Code resultCode = saved.state() == EnrichmentAttemptState.UNCERTAIN
                || saved.state() == EnrichmentAttemptState.RESPONSE_INTERRUPTED
                        ? EnrichmentCollectionResult.Code.UNCERTAIN
                        : saved.state() == EnrichmentAttemptState.HTTP_ERROR
                                ? EnrichmentCollectionResult.Code.HTTP_ERROR : EnrichmentCollectionResult.Code.REFUSED;
        return new EnrichmentCollectionResult(resultCode, saved.reasonCode(), saved.id(), saved.rawSnapshotId(), List.of());
    }

    /** Explicit stored-data replay; it resolves the existing attempt and never obtains an HTTP permit. */
    public EnrichmentCollectionResult replay(UUID attemptId) { return derivations.replay(attemptId); }

    private EnrichmentCollectionContext resolveLogicalContext(EnrichmentCollectionContext context) {
        ProviderCapabilityKey calendarKey = new ProviderCapabilityKey(context.authorityProvider(),
                context.authorityCompetitionId(), context.authoritySourceSeason(), context.authoritySourcePhase(),
                CapabilityDataType.CALENDAR);
        ProviderCapability calendar = registry.find(calendarKey).orElse(null);
        if (calendar == null || !calendar.operational()
                || calendar.authorityRole() != CapabilityAuthorityRole.PRIMARY
                || !CapabilityStatus.PRIMARY.equals(calendar.status())) { return null; }
        return new EnrichmentCollectionContext(context.planId(), context.admissionId(), context.canonicalFixtureId(),
                context.planBudgetWindowId(), context.planRegistrySha256(), context.kickoffAt(),
                calendar.route().competitionCode(), context.logicalSeason(), context.logicalPhase(),
                context.authorityProvider(), context.authorityCompetitionId(), context.authoritySourceSeason(),
                context.authoritySourcePhase(), context.authorityProviderFixtureId(), context.step());
    }

    private static boolean familyAllowed(EnrichmentPlanStepCode step, EnrichmentFamily family) {
        return switch (step) {
            case LINEUP_T_MINUS_30, LINEUP_T_MINUS_15 -> family == EnrichmentFamily.LINEUP;
            case DETAIL_AT_KICKOFF, DETAIL_PLUS_45 -> family == EnrichmentFamily.MATCH_DETAIL;
            case POSTMATCH_AFTER_FINAL, POSTMATCH_RECHECK_FINAL_PLUS_60 -> family != EnrichmentFamily.LINEUP;
        };
    }

    private static CapabilityDataType dataType(EnrichmentFamily family) {
        return CapabilityDataType.valueOf(family.name());
    }

    private static String logicalEndpoint(EnrichmentFamily family) {
        String suffix = switch (family) {
            case MATCH_DETAIL -> "matches";
            case LINEUP -> "lineups";
            case TEAM_STATS -> "statistics";
            case EVENTS -> "events";
            case PLAYER_STATS -> "box-score";
        };
        return "enrichment/" + suffix;
    }

    private static String idempotencyKey(EnrichmentCollectionCommand command, String provider) {
        return "enrichment:" + command.admissionId() + ":" + command.stepCode().name() + ":"
                + command.family().name() + ":" + provider;
    }

    private static String requestHash(EnrichmentCollectionContext context, EnrichmentCollectionCommand command,
            ProviderCapability capability, String providerFixture, String endpoint, String parserVersion) {
        String canonical = "enrichment-request-v1\n" + context.admissionId() + "\n" + context.step().id()
                + "\n" + context.canonicalFixtureId() + "\n" + capability.key().provider() + "\n"
                + capability.key().providerCompetitionId() + "\n" + capability.key().sourceSeason() + "\n"
                + capability.key().sourcePhase() + "\n" + capability.route() + "\n" + providerFixture + "\n"
                + command.family() + "\n" + endpoint + "\n" + parserVersion;
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String outcome(EnrichmentProviderResponse response) {
        if (response.httpStatus() == null) { return "UNCERTAIN_RESPONSE"; }
        if ("SECRET_ECHO".equals(response.failureCode())) { return "SECRET_ECHO"; }
        if ("RESPONSE_TOO_LARGE".equals(response.failureCode())) { return "RESPONSE_TOO_LARGE"; }
        return response.httpStatus() >= 200 && response.httpStatus() < 300 ? "RECEIVED" : "HTTP_ERROR";
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static Instant notBefore(Instant value, Instant floor) { return value.isBefore(floor) ? floor : value; }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
