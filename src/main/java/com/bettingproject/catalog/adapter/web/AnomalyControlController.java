package com.bettingproject.catalog.adapter.web;

import java.util.Set;
import java.util.UUID;

import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyEventView;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyQueryFilter;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyView;
import com.bettingproject.catalog.application.AnomalyQueryService;
import com.bettingproject.catalog.application.CatalogQueryPage;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.ProviderEntityType;
import org.springframework.context.annotation.Profile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/catalog/anomalies")
@Profile("control-api")
public class AnomalyControlController {

    private static final String LIST_SCOPE = "anomalies";
    private static final String EVENT_SCOPE = "anomaly-events";
    private static final Set<String> LIST_PARAMETERS = Set.of(
            "limit", "cursor", "status", "provider", "code", "entityType",
            "snapshotId", "providerEntityId");
    private static final Set<String> PAGE_PARAMETERS = Set.of("limit", "cursor");

    private final AnomalyQueryService queryService;
    private final CatalogCursorCodec cursorCodec;

    public AnomalyControlController(
            AnomalyQueryService queryService,
            CatalogCursorCodec cursorCodec) {
        this.queryService = queryService;
        this.cursorCodec = cursorCodec;
    }

    @GetMapping
    public CatalogPageResponse<AnomalyView> list(
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, LIST_PARAMETERS);
        AnomalyStatus status = parameters.optionalEnum("status", AnomalyStatus.class);
        if (status == null) {
            status = AnomalyStatus.OPEN;
        }
        String provider = parameters.optionalNonBlank("provider", 64);
        NormalizationAnomalyCode code = parameters.optionalEnum(
                "code", NormalizationAnomalyCode.class);
        ProviderEntityType entityType = parameters.optionalEnum(
                "entityType", ProviderEntityType.class);
        UUID snapshotId = parameters.optionalUuid("snapshotId");
        String providerEntityId = parameters.optionalNonBlank("providerEntityId", 200);
        AnomalyQueryFilter filter = new AnomalyQueryFilter(
                status, provider, code, entityType, snapshotId, providerEntityId);
        String fingerprint = CatalogFilterFingerprint.builder()
                .add("status", status)
                .add("provider", provider)
                .add("code", code)
                .add("entityType", entityType)
                .add("snapshotId", snapshotId)
                .add("providerEntityId", providerEntityId)
                .sha256();
        TimestampKeysetAnchor anchor = decodeTimestamp(
                parameters.cursor(), LIST_SCOPE, fingerprint);
        CatalogQueryPage<AnomalyView> page = queryService.list(
                filter, anchor, parameters.limit());
        return timestampPage(
                page,
                LIST_SCOPE,
                fingerprint,
                item -> new TimestampKeysetAnchor(item.createdAt(), item.id()));
    }

    @GetMapping("/{id}")
    public AnomalyView detail(
            @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters.validate(rawParameters, Set.of());
        return queryService.findById(id)
                .orElseThrow(() -> CatalogWebException.notFound("ANOMALY_NOT_FOUND"));
    }

    @GetMapping("/{id}/events")
    public CatalogPageResponse<AnomalyEventView> events(
            @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> rawParameters) {
        CatalogQueryParameters parameters = CatalogQueryParameters.validate(
                rawParameters, PAGE_PARAMETERS);
        if (queryService.findById(id).isEmpty()) {
            throw CatalogWebException.notFound("ANOMALY_NOT_FOUND");
        }
        String fingerprint = CatalogFilterFingerprint.builder()
                .add("anomalyId", id)
                .sha256();
        TimestampKeysetAnchor anchor = decodeTimestamp(
                parameters.cursor(), EVENT_SCOPE, fingerprint);
        CatalogQueryPage<AnomalyEventView> page = queryService.listEvents(
                id, anchor, parameters.limit());
        return timestampPage(
                page,
                EVENT_SCOPE,
                fingerprint,
                item -> new TimestampKeysetAnchor(item.createdAt(), item.id()));
    }

    private TimestampKeysetAnchor decodeTimestamp(
            String cursor,
            String scope,
            String fingerprint) {
        return cursor == null
                ? null
                : cursorCodec.decodeTimestamp(cursor, scope, fingerprint);
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
}
