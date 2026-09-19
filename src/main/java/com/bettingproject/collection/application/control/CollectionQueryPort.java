package com.bettingproject.collection.application.control;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.shared.application.ReadPage;

/** Safe projections only: no raw payload, account reference, credential or idempotency key. */
public interface CollectionQueryPort {
    record Filter(String provider, String status, UUID windowId, String code) {
        public Filter { text(provider, 64); text(status, 32); text(code, 64); }
    }
    record WindowView(UUID id, String provider, Instant startsAt, Instant endsAt, String state,
            long version, Long capacity, long projectLimit, long reserve, long sharedInitial,
            long projectInitial, Integer cadenceLimit, Long cadencePeriodMs, boolean quotaInconsistent,
            Long quotaRemaining, Instant quotaObservedAt, Instant quotaValidUntil,
            long reserved, long committed, Instant createdAt, Instant updatedAt) implements ReadPage.Timed {
        public Instant sortTime() { return updatedAt; }
    }
    record IntentView(UUID id, UUID windowId, String logicalEndpoint, String state, long version,
            Instant committedAt, Instant resultAt, Integer httpStatus, Instant createdAt, Instant updatedAt)
            implements ReadPage.Timed { public Instant sortTime() { return createdAt; } }
    record IncidentView(UUID id, UUID windowId, UUID intentId, String provider, String code, Instant createdAt)
            implements ReadPage.Timed { public Instant sortTime() { return createdAt; } }
    record BudgetEventView(UUID id, UUID windowId, UUID intentId, String type, String reasonCode, Instant createdAt)
            implements ReadPage.Timed { public Instant sortTime() { return createdAt; } }
    record CalendarView(UUID id, UUID windowId, String provider, String providerCompetitionId,
            String sourceSeason, String sourcePhase, LocalDate date, String status, String reasonCode,
            String registrySha256, Instant createdAt, Instant updatedAt) implements ReadPage.Timed {
        public Instant sortTime() { return createdAt; }
    }
    record PageView(UUID id, UUID collectionId, UUID intentId, UUID auditId, int pageNumber,
            String responseCode, Integer httpStatus, Long quotaRemaining, UUID rawSnapshotId,
            String rawSha256, String connectorVersion, Instant requestedAt, Instant receivedAt)
            implements ReadPage.Timed { public Instant sortTime() { return requestedAt; } }

    List<WindowView> windows(Filter filter, ReadPage.Request page);
    Optional<WindowView> window(UUID id);
    List<IntentView> intents(UUID windowId, String state, ReadPage.Request page);
    List<IncidentView> incidents(Filter filter, ReadPage.Request page);
    List<BudgetEventView> budgetEvents(UUID windowId, ReadPage.Request page);
    List<CalendarView> calendars(Filter filter, LocalDate date, ReadPage.Request page);
    Optional<CalendarView> calendar(UUID id);
    List<PageView> pages(UUID collectionId, ReadPage.Request page);

    private static void text(String value, int max) {
        if (value != null && (value.isBlank() || value.length() > max || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl))) { throw new IllegalArgumentException("Invalid read filter"); }
    }
}
