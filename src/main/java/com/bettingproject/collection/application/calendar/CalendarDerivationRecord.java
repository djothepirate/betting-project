package com.bettingproject.collection.application.calendar;

import java.time.Instant;
import java.util.UUID;

public record CalendarDerivationRecord(
        UUID id, UUID pageId, String parserVersion, String outcome,
        UUID derivedSnapshotId, String rawSha256, Instant evaluatedAt) {
}
