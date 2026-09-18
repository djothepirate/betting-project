package com.bettingproject.collection.application.calendar;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;

public record CalendarCollectionCommand(UUID id, UUID windowId, ProviderCapabilityKey capability,
        LocalDate date, int seasonStartYear) {
    public CalendarCollectionCommand {
        Objects.requireNonNull(id);
        Objects.requireNonNull(windowId);
        new CalendarPageRequest(capability, date, seasonStartYear, 0, 100);
    }
}
