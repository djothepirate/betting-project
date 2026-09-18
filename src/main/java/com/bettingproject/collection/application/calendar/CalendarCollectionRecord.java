package com.bettingproject.collection.application.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;

public record CalendarCollectionRecord(
        UUID id, UUID windowId, ProviderCapabilityKey capability, LocalDate date,
        int seasonStartYear, String commandSha256, String registrySha256,
        String status, String reasonCode, Instant createdAt, Instant updatedAt) {
}
