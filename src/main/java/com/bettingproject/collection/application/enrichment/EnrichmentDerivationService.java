package com.bettingproject.collection.application.enrichment;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.bettingproject.collection.application.RawSnapshotReader;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ParsedEvents;
import com.bettingproject.enrichment.domain.ParsedLineup;
import com.bettingproject.enrichment.domain.ParsedMatchDetail;
import com.bettingproject.enrichment.domain.ParsedPlayerStatistics;
import com.bettingproject.enrichment.domain.ParsedTeamStatistics;
import com.bettingproject.enrichment.domain.LineupPlayerEvidence;
import com.bettingproject.enrichment.domain.LineupSide;
import com.bettingproject.enrichment.domain.ProviderEnrichmentObservation;
import com.bettingproject.enrichment.domain.ProviderEvent;
import com.bettingproject.enrichment.domain.ProviderPlayerStatistics;
import com.bettingproject.enrichment.domain.PlayerStatisticsTeam;
import com.bettingproject.enrichment.domain.ObservedScalar;
import com.bettingproject.enrichment.domain.ObservedScalarType;
import com.bettingproject.qualification.domain.CrossEndpointPlayerIdentityAssessor;
import com.bettingproject.qualification.domain.PlayerEndpointIdentityEvidence;
import com.bettingproject.qualification.domain.PlayerQualityAssessor;
import com.bettingproject.qualification.domain.PlayerQualityConcern;
import com.bettingproject.qualification.domain.QualityEntityScope;
import com.bettingproject.qualification.domain.QualityFinding;
import com.bettingproject.qualification.domain.QualityIssueCode;
import com.bettingproject.qualification.domain.YellowCardQualityAssessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

/** Derives immutable provider-neutral observations from already stored bytes; it has no transport dependency. */
@Service
@Profile({"control-api", "batch-worker"})
public class EnrichmentDerivationService {
    private final EnrichmentCollectionStore attempts;
    private final RawSnapshotReader snapshots;
    private final EnrichmentCollectionTransactions transactions;
    private final List<EnrichmentPayloadParser<?>> parsers;
    private final EnrichmentFinalStatusPolicy finalStatuses;
    private final EnrichmentQualityEvidenceReader qualityEvidence;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock;

    public EnrichmentDerivationService(EnrichmentCollectionStore attempts, RawSnapshotReader snapshots,
            EnrichmentCollectionTransactions transactions, List<EnrichmentPayloadParser<?>> parsers,
            EnrichmentFinalStatusPolicy finalStatuses, EnrichmentQualityEvidenceReader qualityEvidence,
            Clock clock) {
        this.attempts = attempts;
        this.snapshots = snapshots;
        this.transactions = transactions;
        this.parsers = List.copyOf(parsers);
        this.finalStatuses = finalStatuses;
        this.qualityEvidence = qualityEvidence;
        this.clock = clock;
    }

    public EnrichmentCollectionResult replay(UUID attemptId) {
        return replay(attemptId, EnrichmentExecution.DIRECT);
    }

    /** Replays stored bytes while fencing every durable application boundary when invoked by a job. */
    public EnrichmentCollectionResult replay(UUID attemptId, EnrichmentExecution execution) {
        EnrichmentCollectionAttempt attempt = attempts.findAttempt(attemptId).orElse(null);
        if (attempt == null) {
            return EnrichmentCollectionResult.refused("ATTEMPT_NOT_FOUND");
        }
        if (attempt.state() != EnrichmentAttemptState.RECEIVED || attempt.rawSnapshotId() == null) {
            return new EnrichmentCollectionResult(EnrichmentCollectionResult.Code.REFUSED,
                    "RAW_RESPONSE_NOT_AVAILABLE", attempt.id(), attempt.rawSnapshotId(), List.of());
        }
        RawSnapshot raw = snapshots.find(attempt.rawSnapshotId()).orElse(null);
        if (raw == null || !matchesProvenance(attempt, raw)) {
            appendRejected(attempt, "PROVENANCE_MISMATCH", execution);
            return new EnrichmentCollectionResult(EnrichmentCollectionResult.Code.REFUSED,
                    "PROVENANCE_MISMATCH", attempt.id(), attempt.rawSnapshotId(), List.of());
        }
        EnrichmentPayloadParser<?> parser = parsers.stream()
                .filter(item -> item.provider().equals(attempt.capability().provider())
                        && item.family() == attempt.family() && item.version().equals(attempt.parserVersion()))
                .findFirst().orElse(null);
        if (parser == null) {
            appendRejected(attempt, "UNSUPPORTED_PARSER", execution);
            return new EnrichmentCollectionResult(EnrichmentCollectionResult.Code.REFUSED,
                    "UNSUPPORTED_PARSER", attempt.id(), attempt.rawSnapshotId(), List.of());
        }

        Object parsed;
        String incompatibleReason = null;
        try {
            parsed = parser.parse(raw.payload(), attempt.kickoffAt(), attempt.receivedAt());
            incompatibleReason = exactIdentityMismatch(attempt, parsed);
        }
        catch (EnrichmentParseException failure) {
            parsed = null;
            incompatibleReason = failure.code();
        }
        catch (RuntimeException failure) {
            parsed = null;
            incompatibleReason = "INCOMPATIBLE_PAYLOAD";
        }

        EnrichmentObservationState state = incompatibleReason == null ? state(attempt.family(), parsed)
                : EnrichmentObservationState.INCOMPATIBLE;
        String representation;
        if (incompatibleReason == null) {
            try {
                representation = json.writeValueAsString(parsed);
            }
            catch (RuntimeException failure) {
                incompatibleReason = "INCOMPATIBLE_REPRESENTATION";
                state = EnrichmentObservationState.INCOMPATIBLE;
                representation = incompatibleRepresentation(incompatibleReason);
            }
        }
        else {
            representation = incompatibleRepresentation(incompatibleReason);
        }

        UUID observationId = UUID.randomUUID();
        var observation = new ProviderEnrichmentObservation(observationId, attempt.admissionId(),
                attempt.canonicalFixtureId(), attempt.budgetIntentId(), attempt.rawSnapshotId(),
                attempt.capability().provider(), attempt.providerFixtureId(), attempt.logicalCompetition(),
                attempt.logicalSeason(), attempt.logicalPhase(), sourceSeason(attempt, parsed),
                sourcePhase(attempt, parsed), attempt.family(), state, raw.sha256(), attempt.parserVersion(),
                attempt.requestedAt(), attempt.receivedAt(), null);
        var findings = incompatibleReason == null ? findings(attempt, parsed, state, observationId)
                : List.<QualityFinding>of();
        var write = new EnrichmentObservationWrite(observation, representation,
                SnapshotHasher.sha256(representation.getBytes(java.nio.charset.StandardCharsets.UTF_8)), findings);
        String outcome = incompatibleReason == null ? "APPLIED" : "INCOMPATIBLE";
        var audit = new EnrichmentDerivationRecord(UUID.randomUUID(), attempt.id(), attempt.family(),
                attempt.parserVersion(), outcome, observationId, raw.sha256(), now());
        EnrichmentFinalStatusEvidence finalEvidence = null;
        if (incompatibleReason == null && parsed instanceof ParsedMatchDetail detail
                && finalStatuses.isFinal(attempt.capability().provider(), detail.providerStatus().value())) {
            finalEvidence = new EnrichmentFinalStatusEvidence(attempt.receivedAt(), finalStatuses.version());
        }
        EnrichmentFinalStatusEvidence applicationEvidence = finalEvidence;
        UUID stored = execution.atomic(() -> transactions.appendDerivation(write, audit, applicationEvidence));
        return new EnrichmentCollectionResult(incompatibleReason == null
                ? EnrichmentCollectionResult.Code.REPLAYED : EnrichmentCollectionResult.Code.INCOMPATIBLE,
                incompatibleReason, attempt.id(), rawId(attempt), List.of(stored));
    }

    private void appendRejected(EnrichmentCollectionAttempt attempt, String outcome, EnrichmentExecution execution) {
        EnrichmentDerivationRecord rejected = new EnrichmentDerivationRecord(UUID.randomUUID(), attempt.id(),
                attempt.family(), attempt.parserVersion(), outcome, null, attempt.payloadSha256(), now());
        execution.atomic(() -> { transactions.appendRejectedDerivation(rejected); return null; });
    }

    private boolean matchesProvenance(EnrichmentCollectionAttempt attempt, RawSnapshot raw) {
        return raw.provider().equals(attempt.capability().provider())
                && raw.endpoint().equals(attempt.logicalEndpoint())
                && raw.connectorVersion().equals(attempt.connectorVersion())
                && raw.sha256().equals(attempt.payloadSha256())
                && SnapshotHasher.sha256(raw.payload()).equals(attempt.payloadSha256());
    }

    private static String exactIdentityMismatch(EnrichmentCollectionAttempt attempt, Object parsed) {
        if (parsed instanceof ParsedMatchDetail detail
                && (!detail.providerFixtureId().equals(attempt.providerFixtureId())
                    || !detail.providerCompetitionId().equals(attempt.capability().providerCompetitionId())
                    || !detail.sourceSeasonReference().value().equals(attempt.capability().sourceSeason())
                    || detail.sourcePhaseReference().state() == EnrichmentObservationState.AVAILABLE
                        && !detail.sourcePhaseReference().value().equals(attempt.capability().sourcePhase()))) {
            return "FIXTURE_REFERENCE_MISMATCH";
        }
        return null;
    }

    private static EnrichmentObservationState state(EnrichmentFamily family, Object parsed) {
        if (family == EnrichmentFamily.MATCH_DETAIL && parsed instanceof ParsedMatchDetail) {
            return EnrichmentObservationState.AVAILABLE;
        }
        if (family == EnrichmentFamily.LINEUP && parsed instanceof ParsedLineup lineup) { return lineup.state(); }
        if (family == EnrichmentFamily.TEAM_STATS && parsed instanceof ParsedTeamStatistics stats) { return stats.state(); }
        if (family == EnrichmentFamily.EVENTS && parsed instanceof ParsedEvents events) { return events.state(); }
        if (family == EnrichmentFamily.PLAYER_STATS && parsed instanceof ParsedPlayerStatistics players) { return players.state(); }
        return EnrichmentObservationState.INCOMPATIBLE;
    }

    private static String sourceSeason(EnrichmentCollectionAttempt attempt, Object parsed) {
        if (parsed instanceof ParsedMatchDetail detail
                && detail.sourceSeasonReference().state() == EnrichmentObservationState.AVAILABLE) {
            return detail.sourceSeasonReference().value();
        }
        return attempt.capability().sourceSeason();
    }

    private static String sourcePhase(EnrichmentCollectionAttempt attempt, Object parsed) {
        if (parsed instanceof ParsedMatchDetail detail
                && detail.sourcePhaseReference().state() == EnrichmentObservationState.AVAILABLE) {
            return detail.sourcePhaseReference().value();
        }
        return attempt.capability().sourcePhase();
    }

    private List<QualityFinding> findings(EnrichmentCollectionAttempt attempt, Object parsed,
            EnrichmentObservationState state, UUID observationId) {
        var result = new java.util.ArrayList<QualityFinding>();
        if (parsed instanceof ParsedPlayerStatistics stats) {
            for (var team : stats.teams()) {
                for (var player : team.players()) {
                    for (QualityIssueCode code : PlayerQualityAssessor.assess(player)) {
                        result.add(finding(observationId, code, QualityEntityScope.PLAYER,
                                player.providerPlayerId()));
                    }
                }
            }
        }
        if (state == EnrichmentObservationState.EMPTY) {
            prior(attempt, attempt.family()).ifPresent(ignored -> result.add(finding(observationId,
                    QualityIssueCode.EMPTY_RESPONSE_WITH_HISTORICAL_REGRESSION,
                    scope(attempt.family()), null)));
        }
        assessPairedFamilies(attempt, parsed, observationId, result);
        if (parsed instanceof ParsedMatchDetail detail && isFinal(attempt.capability().provider(), detail)) {
            for (StoredEnrichmentQualityEvidence evidence : otherProviders(attempt,
                    EnrichmentFamily.MATCH_DETAIL)) {
                ParsedMatchDetail previous = decode(evidence, ParsedMatchDetail.class).orElse(null);
                if (previous != null && isFinal(evidence.provider(), previous)
                        && scoresConflict(detail, previous)) {
                    result.add(finding(observationId, QualityIssueCode.FINAL_SCORE_CONFLICT,
                            QualityEntityScope.FIXTURE, null));
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private void assessPairedFamilies(EnrichmentCollectionAttempt attempt, Object parsed, UUID observationId,
            List<QualityFinding> result) {
        if (parsed instanceof ParsedEvents events) {
            prior(attempt, EnrichmentFamily.PLAYER_STATS).filter(evidence -> isLater(observationId, attempt, evidence))
                    .flatMap(evidence -> decode(evidence, ParsedPlayerStatistics.class))
                    .ifPresent(players -> addConcerns(result, observationId,
                            YellowCardQualityAssessor.assess(events, players)));
        }
        if (parsed instanceof ParsedPlayerStatistics players) {
            prior(attempt, EnrichmentFamily.EVENTS).filter(evidence -> isLater(observationId, attempt, evidence))
                    .flatMap(evidence -> decode(evidence, ParsedEvents.class))
                    .ifPresent(events -> addConcerns(result, observationId,
                            YellowCardQualityAssessor.assess(events, players)));
        }
        if (parsed instanceof ParsedLineup lineup) {
            prior(attempt, EnrichmentFamily.PLAYER_STATS)
                    .filter(EnrichmentDerivationService::hasLineupIdentityEvidence)
                    .filter(evidence -> isLater(observationId, attempt, evidence))
                    .flatMap(evidence -> decode(evidence, ParsedPlayerStatistics.class))
                    .ifPresent(players -> addConcerns(result, observationId,
                            CrossEndpointPlayerIdentityAssessor.assess(lineupEvidence(lineup), playerEvidence(players))));
        }
        if (parsed instanceof ParsedPlayerStatistics players) {
            prior(attempt, EnrichmentFamily.LINEUP).filter(EnrichmentDerivationService::hasLineupIdentityEvidence)
                    .filter(evidence -> isLater(observationId, attempt, evidence))
                    .flatMap(this::lineupEvidence)
                    .ifPresent(lineup -> addConcerns(result, observationId,
                            CrossEndpointPlayerIdentityAssessor.assess(lineup, playerEvidence(players))));
        }
    }

    private java.util.Optional<StoredEnrichmentQualityEvidence> prior(
            EnrichmentCollectionAttempt attempt, EnrichmentFamily family) {
        return qualityEvidence.latestForProviderFixture(attempt.canonicalFixtureId(), attempt.logicalCompetition(),
                attempt.logicalSeason(), attempt.logicalPhase(), attempt.capability().provider(),
                attempt.providerFixtureId(), family, attempt.receivedAt());
    }

    private List<StoredEnrichmentQualityEvidence> otherProviders(
            EnrichmentCollectionAttempt attempt, EnrichmentFamily family) {
        return qualityEvidence.latestPerOtherProvider(attempt.canonicalFixtureId(), attempt.logicalCompetition(),
                attempt.logicalSeason(), attempt.logicalPhase(), attempt.capability().provider(), family,
                attempt.receivedAt());
    }

    private <T> java.util.Optional<T> decode(StoredEnrichmentQualityEvidence evidence, Class<T> type) {
        try {
            return java.util.Optional.of(json.readValue(evidence.representationJson(), type));
        }
        catch (Exception invalidHistoricalRepresentation) {
            return java.util.Optional.empty();
        }
    }

    private static boolean hasLineupIdentityEvidence(StoredEnrichmentQualityEvidence evidence) {
        return "highlightly-lineup-v2".equals(evidence.parserVersion());
    }

    private java.util.Optional<List<PlayerEndpointIdentityEvidence>> lineupEvidence(
            StoredEnrichmentQualityEvidence evidence) {
        try {
            JsonNode root = json.readTree(evidence.representationJson());
            if (root == null || !root.isObject()) { return java.util.Optional.empty(); }
            var players = new java.util.ArrayList<PlayerEndpointIdentityEvidence>();
            addStoredLineupSide(players, root, "homeTeamProviderId", "home");
            addStoredLineupSide(players, root, "awayTeamProviderId", "away");
            return java.util.Optional.of(List.copyOf(players));
        }
        catch (RuntimeException invalidHistoricalRepresentation) {
            return java.util.Optional.empty();
        }
    }

    private static void addStoredLineupSide(List<PlayerEndpointIdentityEvidence> target, JsonNode root,
            String teamField, String sideField) {
        JsonNode teamId = root.get(teamField);
        JsonNode side = root.get(sideField);
        JsonNode evidence = side == null ? null : side.get("starterEvidence");
        if (teamId == null || !teamId.isString() || evidence == null || !evidence.isArray()) { return; }
        for (JsonNode player : evidence) {
            if (player == null || !player.isObject()) { continue; }
            JsonNode providerPlayerId = player.get("providerPlayerId");
            String fullName = storedAvailableText(player.get("fullName"));
            String role = storedAvailableText(player.get("role"));
            if (providerPlayerId == null || !providerPlayerId.isString() || providerPlayerId.textValue().isBlank()
                    || fullName == null || role == null) {
                continue;
            }
            target.add(new PlayerEndpointIdentityEvidence(EnrichmentFamily.LINEUP.name(), teamId.textValue(),
                    providerPlayerId.textValue(), fullName, role, null));
        }
    }

    private static String storedAvailableText(JsonNode scalar) {
        if (scalar == null || !scalar.isObject()) { return null; }
        JsonNode state = scalar.get("state");
        JsonNode type = scalar.get("type");
        JsonNode value = scalar.get("value");
        if (state == null || !state.isString() || !"AVAILABLE".equals(state.textValue())
                || type == null || !type.isString() || !"TEXT".equals(type.textValue())
                || value == null || !value.isString() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue();
    }

    private static boolean isLater(UUID currentObservationId, EnrichmentCollectionAttempt current,
            StoredEnrichmentQualityEvidence previous) {
        int chronology = current.receivedAt().compareTo(previous.receivedAt());
        return chronology > 0 || chronology == 0
                && currentObservationId.toString().compareTo(previous.observationId().toString()) > 0;
    }

    private boolean isFinal(String provider, ParsedMatchDetail detail) {
        return detail.providerStatus().state() == EnrichmentObservationState.AVAILABLE
                && detail.providerStatus().type() == ObservedScalarType.TEXT
                && finalStatuses.isFinal(provider, detail.providerStatus().value());
    }

    private static boolean scoresConflict(ParsedMatchDetail current, ParsedMatchDetail previous) {
        return current.sourceHomeScore().isNumeric() && current.sourceAwayScore().isNumeric()
                && previous.sourceHomeScore().isNumeric() && previous.sourceAwayScore().isNumeric()
                && (current.sourceHomeScore().decimalValue().compareTo(previous.sourceHomeScore().decimalValue()) != 0
                    || current.sourceAwayScore().decimalValue().compareTo(previous.sourceAwayScore().decimalValue()) != 0);
    }

    private static List<PlayerEndpointIdentityEvidence> lineupEvidence(ParsedLineup lineup) {
        var result = new java.util.ArrayList<PlayerEndpointIdentityEvidence>();
        addLineupSide(result, EnrichmentFamily.LINEUP.name(), lineup.homeTeamProviderId(), lineup.home());
        addLineupSide(result, EnrichmentFamily.LINEUP.name(), lineup.awayTeamProviderId(), lineup.away());
        return List.copyOf(result);
    }

    private static void addLineupSide(List<PlayerEndpointIdentityEvidence> target, String endpoint,
            String teamId, LineupSide side) {
        for (LineupPlayerEvidence player : side.starterEvidence()) {
            if (player.providerPlayerId().isBlank() || !isText(player.fullName()) || !isText(player.role())) {
                continue;
            }
            target.add(new PlayerEndpointIdentityEvidence(endpoint, teamId, player.providerPlayerId(),
                    player.fullName().value(), player.role().value(), null));
        }
    }

    private static List<PlayerEndpointIdentityEvidence> playerEvidence(ParsedPlayerStatistics stats) {
        var result = new java.util.ArrayList<PlayerEndpointIdentityEvidence>();
        for (PlayerStatisticsTeam team : stats.teams()) {
            for (ProviderPlayerStatistics player : team.players()) {
                if (!isText(player.fullName()) || !isText(player.position())) { continue; }
                java.math.BigDecimal minutes = player.minutesPlayed().isNumeric()
                        ? player.minutesPlayed().decimalValue() : null;
                result.add(new PlayerEndpointIdentityEvidence(EnrichmentFamily.PLAYER_STATS.name(),
                        team.providerTeamId(), player.providerPlayerId(), player.fullName().value(),
                        player.position().value(), minutes));
            }
        }
        return List.copyOf(result);
    }

    private static boolean isText(ObservedScalar value) {
        return value.state() == EnrichmentObservationState.AVAILABLE
                && value.type() == ObservedScalarType.TEXT && !value.value().isBlank();
    }

    private void addConcerns(List<QualityFinding> findings, UUID observationId,
            List<PlayerQualityConcern> concerns) {
        for (PlayerQualityConcern concern : concerns) {
            findings.add(finding(observationId, concern.code(), QualityEntityScope.PLAYER,
                    concern.providerEntityId()));
        }
    }

    private QualityFinding finding(UUID observationId, QualityIssueCode code, QualityEntityScope scope,
            String providerEntityId) {
        return new QualityFinding(UUID.randomUUID(), observationId, code, scope, providerEntityId, now());
    }

    private static QualityEntityScope scope(EnrichmentFamily family) {
        return switch (family) {
            case LINEUP, PLAYER_STATS -> QualityEntityScope.PLAYER;
            case TEAM_STATS -> QualityEntityScope.TEAM;
            case EVENTS -> QualityEntityScope.EVENT;
            case MATCH_DETAIL -> QualityEntityScope.FIXTURE;
        };
    }

    private static String incompatibleRepresentation(String reason) {
        return "{\"state\":\"INCOMPATIBLE\",\"reasonCode\":\"" + reason + "\"}";
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static UUID rawId(EnrichmentCollectionAttempt attempt) { return attempt.rawSnapshotId(); }
}
