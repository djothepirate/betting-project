package com.bettingproject.collection.application.calendar;

import java.time.Instant;
import java.util.UUID;

public record CalendarPageRecord(
        UUID id, UUID collectionId, UUID intentId, UUID auditId, int pageNumber,
        int pageOffset, int pageLimit, String connectorVersion, UUID rawSnapshotId,
        String rawSha256, Instant requestedAt, Instant receivedAt, Integer httpStatus,
        Long quotaRemaining, String responseCode) {
}
