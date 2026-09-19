package com.bettingproject.collection.application.calendar;

import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import java.time.LocalDate;
import java.util.Objects;

public record CalendarPageRequest(
        ProviderCapabilityKey capability, LocalDate date, int seasonStartYear, int offset, int limit) {
    public CalendarPageRequest {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(date, "date");
        if (capability.dataType() != CapabilityDataType.CALENDAR
                || !(capability.provider().equals("highlightly")
                || capability.provider().equals("football-data.org"))
                || seasonStartYear < 1000 || seasonStartYear > 9999
                || offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid calendar page request");
        }
    }
}
