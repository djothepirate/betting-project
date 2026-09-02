package com.bettingproject.catalog.adapter.web;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.catalog.application.AttemptKeysetAnchor;
import com.bettingproject.catalog.application.CatalogQueryPage;
import com.bettingproject.catalog.application.CorrelationKeysetAnchor;
import com.bettingproject.catalog.application.NormalizationReplayCommand;
import com.bettingproject.catalog.application.NormalizationReplayExecutionResult;
import com.bettingproject.catalog.application.NormalizationReplayExecutionService;
import com.bettingproject.catalog.application.NormalizationReplayOrigin;
import com.bettingproject.catalog.application.NormalizationReplayRequestResult;
import com.bettingproject.catalog.application.NormalizationReplayRequestService;
import com.bettingproject.catalog.application.NormalizationReplayStatus;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayAnomalyEventView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayApplicationView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayAttemptView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayQueryFilter;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayRequestView;
import com.bettingproject.catalog.application.ReplayQueryService;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
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
@RequestMapping("/internal/catalog/replay-requests")
@Profile("control-api")
public class ReplayControlController {

    private static final String REQUEST_SCOPE = "replay-requests";
    private static final String ATTEMPT_SCOPE = "replay-attempts";
    private static final String APPLICATION_SCOPE = "replay-applications";
    private static final String ANOMALY_EVENT_SCOPE = "replay-anomaly-events";
    private static final Set<String> REQUEST_PARAMETERS = Set.of(
            "limit", "cursor", "status", "origin", "snapshotId", "mappingDecisionId");
    private static final Set<String> PAGE_PARAMETERS = Set.of("limit", "cursor");

    private final ReplayQueryService queryService;
    private final NormalizationReplayRequestService requestService;
    private final NormalizationReplayExecutionService executionService;
    private final StrictCatalogCommandParser commandParser;
    private final CatalogCursorCodec cursorCodec;

    public ReplayControlController(
            ReplayQueryService queryService,
            NormalizationReplayRequestService requestService,
            NormalizationReplayExecutionService executionService,
            StrictCatalogCommandParser commandParser,
            CatalogCursorCodec cursorCodec) {
        this.queryService = queryService;
        this.requestService = requestService;
        this.executionService = executionService;
        this.commandParser = commandParser;
        this.cursorCodec = cursorCodec;
    }

    @GetMapping
    public CatalogPageResponse<ReplayRequestView> requests(
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, REQUEST_PARAMETERS);
        NormalizationReplayStatus status = parameters.optionalEnum(
                "status", NormalizationReplayStatus.class);
        NormalizationReplayOrigin origin = parameters.optionalEnum(
                "origin", NormalizationReplayOrigin.class);
        UUID snapshotId = parameters.optionalUuid("snapshotId");
        UUID mappingDecisionId = parameters.optionalUuid("mappingDecisionId");
        ReplayQueryFilter filter = new ReplayQueryFilter(
                status, origin, snapshotId, mappingDecisionId);
        String fingerprint = CatalogFilterFingerprint.builder()
                .add("status", status)
                .add("origin", origin)
                .add("snapshotId", snapshotId)
                .add("mappingDecisionId", mappingDecisionId)
                .sha256();
        TimestampKeysetAnchor anchor = decodeTimestamp(
                parameters.cursor(), REQUEST_SCOPE, fingerprint);
        CatalogQueryPage<ReplayRequestView> page = queryService.listRequests(
                filter, anchor, parameters.limit());
        String nextCursor = null;
        if (page.hasNext() && !page.items().isEmpty()) {
            ReplayRequestView last = page.items().getLast();
            nextCursor = cursorCodec.encodeTimestamp(
                    REQUEST_SCOPE,
                    fingerprint,
                    new TimestampKeysetAnchor(last.updatedAt(), last.id()));
        }
        return new CatalogPageResponse<>(page.items(), nextCursor);
    }

    @GetMapping("/{id}")
    public ReplayRequestView request(
            @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters.validate(rawParameters, Set.of());
        return requiredRequest(id);
    }

    @GetMapping("/{id}/attempts")
    public CatalogPageResponse<ReplayAttemptView> attempts(
            @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, PAGE_PARAMETERS);
        requiredRequest(id);
        String fingerprint = CatalogFilterFingerprint.builder()
                .add("requestId", id)
                .sha256();
        AttemptKeysetAnchor anchor = parameters.cursor() == null
                ? null
                : cursorCodec.decodeAttempt(
                        parameters.cursor(), ATTEMPT_SCOPE, fingerprint);
        CatalogQueryPage<ReplayAttemptView> page = queryService.listAttempts(
                id, anchor, parameters.limit());
        String nextCursor = null;
        if (page.hasNext() && !page.items().isEmpty()) {
            ReplayAttemptView last = page.items().getLast();
            nextCursor = cursorCodec.encodeAttempt(
                    ATTEMPT_SCOPE,
                    fingerprint,
                    new AttemptKeysetAnchor(last.attemptNumber(), last.id()));
        }
        return new CatalogPageResponse<>(page.items(), nextCursor);
    }

    @GetMapping("/{requestId}/attempts/{attemptId}/applications")
    public CatalogPageResponse<ReplayApplicationView> applications(
            @PathVariable UUID requestId,
            @PathVariable UUID attemptId,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, PAGE_PARAMETERS);
        requiredRequest(requestId);
        String fingerprint = correlationFingerprint(requestId, attemptId);
        CorrelationKeysetAnchor anchor = decodeCorrelation(
                parameters.cursor(), APPLICATION_SCOPE, fingerprint);
        CatalogQueryPage<ReplayApplicationView> page = queryService.listApplications(
                requestId, attemptId, anchor, parameters.limit());
        String nextCursor = null;
        if (page.hasNext() && !page.items().isEmpty()) {
            ReplayApplicationView last = page.items().getLast();
            nextCursor = cursorCodec.encodeCorrelation(
                    APPLICATION_SCOPE,
                    fingerprint,
                    new CorrelationKeysetAnchor(last.correlatedAt(), last.id()));
        }
        return new CatalogPageResponse<>(page.items(), nextCursor);
    }

    @GetMapping("/{requestId}/attempts/{attemptId}/anomaly-events")
    public CatalogPageResponse<ReplayAnomalyEventView> anomalyEvents(
            @PathVariable UUID requestId,
            @PathVariable UUID attemptId,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, PAGE_PARAMETERS);
        requiredRequest(requestId);
        String fingerprint = correlationFingerprint(requestId, attemptId);
        CorrelationKeysetAnchor anchor = decodeCorrelation(
                parameters.cursor(), ANOMALY_EVENT_SCOPE, fingerprint);
        CatalogQueryPage<ReplayAnomalyEventView> page = queryService.listAnomalyEvents(
                requestId, attemptId, anchor, parameters.limit());
        String nextCursor = null;
        if (page.hasNext() && !page.items().isEmpty()) {
            ReplayAnomalyEventView last = page.items().getLast();
            nextCursor = cursorCodec.encodeCorrelation(
                    ANOMALY_EVENT_SCOPE,
                    fingerprint,
                    new CorrelationKeysetAnchor(last.correlatedAt(), last.id()));
        }
        return new CatalogPageResponse<>(page.items(), nextCursor);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReplayRequestView> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody byte[] body,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters.validate(rawParameters, Set.of());
        commandParser.requireJsonContentType(contentType);
        NormalizationReplayCommand command = commandParser.parseReplayRequest(
                body, idempotencyKey);
        NormalizationReplayRequestResult result = requestService.request(command);
        return requestResponse(result);
    }

    @PostMapping(
            value = "/{id}/resume",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReplayRequestView> resume(
            @PathVariable UUID id,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody byte[] body,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters.validate(rawParameters, Set.of());
        commandParser.requireJsonContentType(contentType);
        long expectedVersion = commandParser.parseReplayResume(body);
        NormalizationReplayExecutionResult result = executionService.resume(
                id, expectedVersion);
        return executionResponse(result, expectedVersion);
    }

    private ResponseEntity<ReplayRequestView> requestResponse(
            NormalizationReplayRequestResult result) {
        if (result instanceof NormalizationReplayRequestResult.Created created) {
            ReplayRequestView request = requiredRequest(created.request().id());
            return ResponseEntity.accepted()
                    .location(requestLocation(request.id()))
                    .body(request);
        }
        if (result instanceof NormalizationReplayRequestResult.AlreadyCreated already) {
            ReplayRequestView request = requiredRequest(already.request().id());
            return ResponseEntity.status(request.status().terminal() ? 200 : 202)
                    .location(requestLocation(request.id()))
                    .body(request);
        }
        if (result instanceof NormalizationReplayRequestResult.Invalid invalid) {
            throw CatalogWebException.badRequest(invalid.code());
        }
        if (result instanceof NormalizationReplayRequestResult.NotFound) {
            throw CatalogWebException.notFound("SNAPSHOT_NOT_FOUND");
        }
        if (result instanceof NormalizationReplayRequestResult.AmbiguousPayloadSha256) {
            throw CatalogWebException.conflict("AMBIGUOUS_PAYLOAD_SHA256");
        }
        throw CatalogWebException.conflict("IDEMPOTENCY_CONFLICT");
    }

    private ResponseEntity<ReplayRequestView> executionResponse(
            NormalizationReplayExecutionResult result,
            long expectedVersion) {
        if (result instanceof NormalizationReplayExecutionResult.Invalid invalid) {
            throw CatalogWebException.badRequest(invalid.code());
        }
        if (result instanceof NormalizationReplayExecutionResult.NotFound) {
            throw CatalogWebException.notFound("REPLAY_REQUEST_NOT_FOUND");
        }
        if (result instanceof NormalizationReplayExecutionResult.Completed completed) {
            return ResponseEntity.ok(requiredRequest(completed.request().id()));
        }
        if (result instanceof NormalizationReplayExecutionResult.Failed failed) {
            ReplayRequestView request = requiredRequest(failed.request().id());
            return ResponseEntity.status(request.status().terminal() ? 200 : 202).body(request);
        }
        if (result instanceof NormalizationReplayExecutionResult.AlreadyTerminal terminal) {
            return ResponseEntity.ok(requiredRequest(terminal.request().id()));
        }
        NormalizationReplayExecutionResult.NotClaimed notClaimed =
                (NormalizationReplayExecutionResult.NotClaimed) result;
        ReplayRequestView request = requiredRequest(notClaimed.request().id());
        if (request.version() != expectedVersion) {
            throw CatalogWebException.conflict("VERSION_CONFLICT");
        }
        if (request.status() == NormalizationReplayStatus.RUNNING) {
            return ResponseEntity.accepted().body(request);
        }
        throw CatalogWebException.conflict("REPLAY_CLAIM_CONFLICT");
    }

    private ReplayRequestView requiredRequest(UUID id) {
        return queryService.findRequest(id)
                .orElseThrow(() -> CatalogWebException.notFound("REPLAY_REQUEST_NOT_FOUND"));
    }

    private String correlationFingerprint(UUID requestId, UUID attemptId) {
        return CatalogFilterFingerprint.builder()
                .add("requestId", requestId)
                .add("attemptId", attemptId)
                .sha256();
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

    private URI requestLocation(UUID requestId) {
        return URI.create("/internal/catalog/replay-requests/" + requestId);
    }
}
