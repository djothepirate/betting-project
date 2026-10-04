package com.bettingproject.collection.application.enrichment;

import java.util.Objects;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentFamily;

/** Exact PRIMARY route frozen into a persisted job input. */
public record EnrichmentJobRoute(EnrichmentFamily family, ProviderCapabilityKey capability,
        String parserVersion) {
    public EnrichmentJobRoute {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(capability, "capability");
        if (capability.dataType() != com.bettingproject.collection.domain.capability.CapabilityDataType.valueOf(family.name())) {
            throw new IllegalArgumentException("route data type must match its family");
        }
        parserVersion = require(parserVersion, "parserVersion");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 64
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
