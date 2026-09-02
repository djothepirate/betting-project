package com.bettingproject.catalog.application;

import java.util.Objects;

public record CalendarAuthorityKey(
        String provider,
        String providerCompetitionId,
        String season,
        String phase,
        CalendarAuthorityDataType dataType) {

    public CalendarAuthorityKey {
        provider = requireExactText(provider, "provider");
        providerCompetitionId = requireExactText(providerCompetitionId, "providerCompetitionId");
        season = requireExactText(season, "season");
        phase = requireExactText(phase, "phase");
        dataType = Objects.requireNonNull(dataType, "dataType");
    }

    private static String requireExactText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " must not have leading or trailing whitespace");
        }
        return value;
    }
}
