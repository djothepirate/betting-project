package com.bettingproject.collection.application;

public record DiscoveredTeam(
        String providerTeamId,
        String name,
        String countryCode) {

    public DiscoveredTeam {
        providerTeamId = normalize(providerTeamId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("team name must not be blank");
        }
        name = name.trim();
        countryCode = normalize(countryCode);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
