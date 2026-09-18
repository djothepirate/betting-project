package com.bettingproject.collection.application.calendar;

import com.bettingproject.collection.application.CalendarSnapshot;

public interface CalendarSnapshotEncoder {
    byte[] encode(CalendarSnapshot snapshot);
}
