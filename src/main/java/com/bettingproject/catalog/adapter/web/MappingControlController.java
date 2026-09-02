package com.bettingproject.catalog.adapter.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.catalog.application.CatalogQueryPage;
import com.bettingproject.catalog.application.CorrelationKeysetAnchor;
import com.bettingproject.catalog.application.MappingDecisionCommand;
import com.bettingproject.catalog.application.MappingDecisionResult;
import com.bettingproject.catalog.application.MappingDecisionService;
import com.bettingproject.catalog.application.MappingQueryPort.DecisionAnomalyView;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionView;
import com.bettingproject.catalog.application.MappingQueryPort.MappingQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingView;
import com.bettingproject.catalog.application.MappingQueryService;
import com.bettingproject.catalog.application.NormalizationReplayStatus;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayRequestView;
import com.bettingproject.catalog.application.ReplayQueryService;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.ProviderEntityType;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/catalog")
@Profile("control-api")
public class MappingControlController {

    private static final String MAPPING_SCOPE = "provider-mappings";
    private static final String DECISION_SCOPE = "mapping-decisions";
    private static final String CORRELATION_SCOPE = "mapping-decision-anomalies";
    private static final Set<String> MAPPING_PARAMETERS = Set.of(
            "limit", "cursor", "status", "provider", "entityType", "providerEntityId",
            "season", "phase", "canonicalEntityId");
    private static final Set<String> DECISION_PARAMETERS = Set.of(
            "limit", "cursor", "mappingId");
    private static final Set<String> PAGE_PARAMETERS = Set.of("limit", "cursor");

    private final MappingQueryService mappingQueryService;
    private final ReplayQueryService replayQueryService;
    private final MappingDecisionService decisionService;
    private final StrictCatalogCommandParser commandParser;
    private final CatalogCursorCodec cursorCodec;

    public MappingControlController(
            MappingQueryService mappingQueryService,
            ReplayQueryService replayQueryService,
            MappingDecisionService decisionService,
            StrictCatalogCommandParser commandParser,
            CatalogCursorCodec cursorCodec) {
        this.mappingQueryService = mappingQueryService;
        this.replayQueryService = replayQueryService;
        this.decisionService = decisionService;
        this.commandParser = commandParser;
        this.cursorCodec = cursorCodec;
    }

    @GetMapping("/provider-mappings")
    public CatalogPageResponse<MappingView> mappings(
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, MAPPING_PARAMETERS);
        MappingStatus status = parameters.optionalEnum("status", MappingStatus.class);
        String provider = parameters.optionalNonBlank("provider", 64);
        ProviderEntityType entityType = parameters.optionalEnum(
                "entityType", ProviderEntityType.class);
        String providerEntityId = parameters.optionalNonBlank("providerEntityId", 200);
        String season = parameters.optionalContext("season", 64);
        String phase = parameters.optionalContext("phase", 64);
        UUID canonicalEntityId = parameters.optionalUuid("canonicalEntityId");
        MappingQueryFilter filter = new MappingQueryFilter(
                status,
                provider,
                entityType,
                providerEntityId,
                season,
                phase,
                canonicalEntityId);
        String fingerprint = CatalogFilterFingerprint.builder()
                .add("status", status)
                .add("provider", provider)
                .add("entityType", entityType)
                .add("providerEntityId", providerEntityId)
                .addContext("season", parameters.contains("season"), season)
                .addContext("phase", parameters.contains("phase"), phase)
                .add("canonicalEntityId", canonicalEntityId)
                .sha256();
        TimestampKeysetAnchor anchor = decodeTimestamp(
                parameters.cursor(), MAPPING_SCOPE, fingerprint);
        CatalogQueryPage<MappingView> page = mappingQueryService.listMappings(
                filter, anchor, parameters.limit());
        return timestampPage(
                page,
                MAPPING_SCOPE,
                fingerprint,
                item -> new TimestampKeysetAnchor(item.updatedAt(), item.id()));
    }

    @GetMapping("/provider-mappings/{id}")
    public MappingView mapping(
            @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters.validate(rawParameters, Set.of());
        return mappingQueryService.findMapping(id)
                .orElseThrow(() -> CatalogWebException.notFound("MAPPING_NOT_FOUND"));
    }

    @GetMapping("/mapping-decisions")
    public CatalogPageResponse<MappingDecisionView> decisions(
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, DECISION_PARAMETERS);
        UUID mappingId = parameters.optionalUuid("mappingId");
        String fingerprint = CatalogFilterFingerprint.builder()
                .add("mappingId", mappingId)
                .sha256();
        TimestampKeysetAnchor anchor = decodeTimestamp(
                parameters.cursor(), DECISION_SCOPE, fingerprint);
        CatalogQueryPage<MappingDecisionView> page = mappingQueryService.listDecisions(
                new MappingDecisionQueryFilter(mappingId),
                anchor,
                parameters.limit());
        return timestampPage(
                page,
                DECISION_SCOPE,
                fingerprint,
                item -> new TimestampKeysetAnchor(item.createdAt(), item.id()));
    }

    @GetMapping("/mapping-decisions/{id}")
    public MappingDecisionView decision(
            @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters.validate(rawParameters, Set.of());
        return requiredDecision(id);
    }

    @GetMapping("/mapping-decisions/{id}/anomalies")
    public CatalogPageResponse<DecisionAnomalyView> decisionAnomalies(
            @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, PAGE_PARAMETERS);
        requiredDecision(id);
        String fingerprint = CatalogFilterFingerprint.builder()
                .add("decisionId", id)
                .sha256();
        CorrelationKeysetAnchor anchor = decodeCorrelation(
                parameters.cursor(), CORRELATION_SCOPE, fingerprint);
        CatalogQueryPage<DecisionAnomalyView> page = mappingQueryService
                .listCorrelatedAnomalies(id, anchor, parameters.limit());
        String nextCursor = null;
        if (page.hasNext() && !page.items().isEmpty()) {
            DecisionAnomalyView last = page.items().getLast();
            nextCursor = cursorCodec.encodeCorrelation(
                    CORRELATION_SCOPE,
                    fingerprint,
                    new CorrelationKeysetAnchor(last.correlatedAt(), last.anomaly().id()));
        }
        return new CatalogPageResponse<>(page.items(), nextCursor);
    }

    @PostMapping(
            value = "/mapping-decisions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MappingDecisionMutationResponse> decide(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody byte[] body,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters.validate(rawParameters, Set.of());
        commandParser.requireJsonContentType(contentType);
        MappingDecisionCommand command = commandParser.parseMappingDecision(
                body, idempotencyKey);
        MappingDecisionResult result = decisionService.decide(command);
        return decisionResponse(result);
    }

    private ResponseEntity<MappingDecisionMutationResponse> decisionResponse(
            MappingDecisionResult result) {
        if (result instanceof MappingDecisionResult.Applied applied) {
            MappingDecisionMutationResponse response = mutationResponse(
                    applied.decision().id(),
                    applied.correlatedAnomalyIds(),
                    applied.replayRequestIds());
            return ResponseEntity
                    .status(applied.replayRequestIds().isEmpty() ? 200 : 202)
                    .location(decisionLocation(applied.decision().id()))
                    .body(response);
        }
        if (result instanceof MappingDecisionResult.AlreadyApplied already) {
            MappingDecisionMutationResponse response = mutationResponse(
                    already.decision().id(),
                    already.correlatedAnomalyIds(),
                    already.replayRequestIds());
            boolean nonTerminal = response.replayRequests().stream()
                    .anyMatch(request -> !request.status().terminal());
            return ResponseEntity
                    .status(nonTerminal ? 202 : 200)
                    .location(decisionLocation(already.decision().id()))
                    .body(response);
        }
        if (result instanceof MappingDecisionResult.Invalid invalid) {
            throw CatalogWebException.badRequest(invalid.code());
        }
        if (result instanceof MappingDecisionResult.NotFound notFound) {
            throw CatalogWebException.notFound(resourceCode(notFound.resource()));
        }
        if (result instanceof MappingDecisionResult.VersionConflict) {
            throw CatalogWebException.conflict("VERSION_CONFLICT");
        }
        if (result instanceof MappingDecisionResult.IdempotencyConflict) {
            throw CatalogWebException.conflict("IDEMPOTENCY_CONFLICT");
        }
        if (result instanceof MappingDecisionResult.OperatorUnavailable) {
            throw CatalogWebException.unavailable("OPERATOR_UNAVAILABLE");
        }
        MappingDecisionResult.BusinessRuleViolation violation =
                (MappingDecisionResult.BusinessRuleViolation) result;
        throw CatalogWebException.unprocessable(violation.code());
    }

    private MappingDecisionMutationResponse mutationResponse(
            UUID decisionId,
            List<UUID> anomalyIds,
            List<UUID> replayRequestIds) {
        MappingDecisionView decision = requiredDecision(decisionId);
        List<ReplayRequestView> replayRequests = new ArrayList<>(replayRequestIds.size());
        for (UUID requestId : replayRequestIds) {
            replayRequests.add(replayQueryService.findRequest(requestId)
                    .orElseThrow(() -> new IllegalStateException(
                            "A replay request created by a mapping decision is missing")));
        }
        return new MappingDecisionMutationResponse(decision, anomalyIds, replayRequests);
    }

    private MappingDecisionView requiredDecision(UUID id) {
        return mappingQueryService.findDecision(id)
                .orElseThrow(() -> CatalogWebException.notFound("MAPPING_DECISION_NOT_FOUND"));
    }

    private String resourceCode(String resource) {
        return switch (resource) {
            case "provider_mapping" -> "MAPPING_NOT_FOUND";
            case "canonical_entity" -> "CANONICAL_ENTITY_NOT_FOUND";
            default -> "RESOURCE_NOT_FOUND";
        };
    }

    private URI decisionLocation(UUID decisionId) {
        return URI.create("/internal/catalog/mapping-decisions/" + decisionId);
    }

    private TimestampKeysetAnchor decodeTimestamp(
            String cursor,
            String scope,
            String fingerprint) {
        return cursor == null
                ? null
                : cursorCodec.decodeTimestamp(cursor, scope, fingerprint);
    }

    private CorrelationKeysetAnchor decodeCorrelation(
            String cursor,
            String scope,
            String fingerprint) {
        return cursor == null
                ? null
                : cursorCodec.decodeCorrelation(cursor, scope, fingerprint);
    }

    private <T> CatalogPageResponse<T> timestampPage(
            CatalogQueryPage<T> page,
            String scope,
            String fingerprint,
            TimestampAnchorExtractor<T> extractor) {
        String nextCursor = null;
        if (page.hasNext() && !page.items().isEmpty()) {
            nextCursor = cursorCodec.encodeTimestamp(
                    scope,
                    fingerprint,
                    extractor.anchor(page.items().getLast()));
        }
        return new CatalogPageResponse<>(page.items(), nextCursor);
    }

    @FunctionalInterface
    private interface TimestampAnchorExtractor<T> {
        TimestampKeysetAnchor anchor(T item);
    }

    public record MappingDecisionMutationResponse(
            MappingDecisionView decision,
            List<UUID> correlatedAnomalyIds,
            List<ReplayRequestView> replayRequests) {

        public MappingDecisionMutationResponse {
            correlatedAnomalyIds = List.copyOf(correlatedAnomalyIds);
            replayRequests = List.copyOf(replayRequests);
        }
    }
}
