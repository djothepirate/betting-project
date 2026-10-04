package com.bettingproject.collection.application.enrichment;

import java.util.List;
import java.util.UUID;

public record EnrichmentCollectionResult(Code code, String reasonCode, UUID attemptId,
        UUID rawSnapshotId, List<UUID> observationIds) {
    public enum Code { COLLECTED, ALREADY_RECORDED, REPLAYED, REFUSED, UNCERTAIN, HTTP_ERROR, INCOMPATIBLE }
    public EnrichmentCollectionResult { observationIds = List.copyOf(observationIds); }
    public static EnrichmentCollectionResult refused(String reason) {
        return new EnrichmentCollectionResult(Code.REFUSED, reason, null, null, List.of());
    }
}
