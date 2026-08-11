package com.bettingproject.collection.application;

public record DiscoveredCompetition(
        String providerCompetitionId,
        String name,
        String countryCode,
        String type,
        String season,
        String phase) {

    public DiscoveredCompetition {
        providerCompetitionId = normalize(providerCompetitionId);
        name = normalize(name);
        countryCode = normalize(countryCode);
        type = normalize(type);
        season = normalize(season);
        phase = normalize(phase);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
