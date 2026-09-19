package com.bettingproject.collection.application.calendar;

import java.time.Instant;

/** Interprets already retained provider bytes; implementations never perform I/O. */
public interface CalendarPageParser {
    String provider();
    String version();
    ParsedCalendarPage parse(CalendarPageRequest request, byte[] body, Instant observedAt);
}
