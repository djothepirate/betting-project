package com.bettingproject.collection.application.control;

import java.time.LocalDate;
import java.util.Optional;
import com.bettingproject.enrichment.domain.DailyEnrichmentPlan;

/** Persistence port. lockDate is transaction-scoped and serializes the seven-match cap. */
public interface EnrichmentAdmissionStore {
    void lockIdempotencyKey(String idempotencyKey);
    void lockDate(LocalDate utcDate);
    Optional<DailyEnrichmentPlan> findByIdempotencyKey(String idempotencyKey);
    Optional<DailyEnrichmentPlan> findByDate(LocalDate utcDate);
    boolean insert(DailyEnrichmentPlan plan);
}
