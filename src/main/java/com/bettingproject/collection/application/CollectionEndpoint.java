package com.bettingproject.collection.application;

import java.util.Locale;

public enum CollectionEndpoint {
    DETAIL("matches"),
    LINEUP("lineups"),
    STATISTICS("statistics"),
    EVENTS("events"),
    BOX_SCORE("box-score");

    private final String pathSegment;

    CollectionEndpoint(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String relativePath(String providerMatchId) {
        if (providerMatchId == null || !providerMatchId.matches("[0-9]+")) {
            throw new IllegalArgumentException("providerMatchId must contain digits only");
        }
        return "/" + pathSegment + "/" + providerMatchId;
    }

    public String fileStem() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static CollectionEndpoint fromCliValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
