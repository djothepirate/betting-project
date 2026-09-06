package com.bettingproject.collection.application.capability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.bettingproject.collection.adapter.configuration.ProviderCapabilityDocumentParser;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;

public final class CapabilityTestSamples {
    private CapabilityTestSamples() {
    }

    public static String document() {
        try (var input = CapabilityTestSamples.class.getResourceAsStream(
                "/fixtures/mvp001/provider-capabilities.synthetic.json")) {
            return new String(java.util.Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static ProviderCapabilityRegistry registry() {
        return new ProviderCapabilityDocumentParser().parse(document().getBytes(StandardCharsets.UTF_8));
    }

    public static CapabilityRouteKey route(String code) {
        return new CapabilityRouteKey(code, "2026/2027", "REGULAR_SEASON", CapabilityDataType.CALENDAR);
    }

    public static ProviderCapabilityKey key(String provider, String code) {
        return new ProviderCapabilityKey(provider, "synthetic-" + code.toLowerCase(java.util.Locale.ROOT),
                "2026/2027", "REGULAR_SEASON", CapabilityDataType.CALENDAR);
    }
}
