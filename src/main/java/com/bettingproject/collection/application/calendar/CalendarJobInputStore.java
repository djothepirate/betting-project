package com.bettingproject.collection.application.calendar;

import java.util.Optional;
import java.util.UUID;

public interface CalendarJobInputStore {
    void insertIfAbsent(CalendarJobInput input);
    Optional<CalendarJobInput> find(UUID jobId);
}
