package com.bettingproject.collection.application.calendar;

public interface CalendarPageClient {
    String provider();

    boolean available();

    CalendarPageResponse fetch(CalendarPageRequest request);
}
