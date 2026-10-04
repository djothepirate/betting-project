package com.bettingproject.enrichment.domain;

import java.util.Objects;

/** Team identity and optional descriptive metadata as supplied by one provider. */
public record ProviderTeamReference(String providerTeamId, String sourceName, ObservedScalar countryCode) {
    public ProviderTeamReference {
        providerTeamId = require(providerTeamId, "providerTeamId", 200);
        sourceName = require(sourceName, "sourceName", 256);
        countryCode = Objects.requireNonNull(countryCode, "countryCode");
        if (countryCode.state() == EnrichmentObservationState.AVAILABLE
                && countryCode.type() != ObservedScalarType.TEXT) {
            throw new IllegalArgumentException("countryCode must be text when present");
        }
    }

    private static String require(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
