package com.bettingproject.collection.application.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.domain.RawSnapshot;

public interface CalendarCollectionStore {
    record StoredCollection(CalendarCollectionRecord record, boolean inserted) {
    }

    StoredCollection createAndResolve(CalendarCollectionRecord collection);

    Optional<CalendarCollectionRecord> findCollection(UUID id);

    void finish(UUID id, String status, String reason, Instant now);

    CalendarPageRecord preparePage(UUID collectionId, UUID intentId, int pageNumber,
            int pageOffset, int pageLimit, String connectorVersion, Instant now);

    void recordResponse(UUID pageId, RawSnapshot snapshot, Instant requestedAt,
            Instant receivedAt, Integer httpStatus, Long quotaRemaining, String responseCode);

    List<CalendarPageRecord> pages(UUID collectionId);

    Optional<CalendarPageRecord> findPage(UUID id);

    Optional<RawSnapshot> readRaw(UUID snapshotId);

    void appendDerivation(CalendarDerivationRecord derivation);

    List<CalendarDerivationRecord> derivations(UUID pageId);

    boolean windowMatchesProvider(UUID windowId, String provider);
}
