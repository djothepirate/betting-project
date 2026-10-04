package com.bettingproject.collection.application.control;

import java.util.Objects;
import com.bettingproject.enrichment.domain.DailyEnrichmentPlan;

public record DailyEnrichmentAdmissionResult(Code code, DailyEnrichmentPlan plan,
        DailySelectionService.Code previewCode, boolean created) {
    public enum Code { CREATED, ALREADY_CREATED, IDEMPOTENCY_CONFLICT, DAY_ALREADY_PLANNED, PREVIEW_REFUSED, NO_ELIGIBLE_FIXTURES }
    public DailyEnrichmentAdmissionResult { Objects.requireNonNull(code); }
}
