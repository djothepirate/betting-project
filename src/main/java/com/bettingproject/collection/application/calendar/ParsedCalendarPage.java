package com.bettingproject.collection.application.calendar;

import com.bettingproject.collection.application.CalendarSnapshot;
import java.util.Objects;

/** A complete interpretation of one native page, not a claim of collection exhaustiveness. */
public record ParsedCalendarPage(CalendarSnapshot snapshot, Integer nextOffset, long totalCount) {
    public ParsedCalendarPage {
        Objects.requireNonNull(snapshot, "snapshot");
        if (totalCount < snapshot.fixtures().size() || (nextOffset != null && nextOffset <= 0)) {
            throw new IllegalArgumentException("invalid parsed page bounds");
        }
    }
}
