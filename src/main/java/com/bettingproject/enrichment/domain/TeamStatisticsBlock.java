package com.bettingproject.enrichment.domain;

import java.util.List;

/** Statistics grouped by exact provider team ID, never by array position. */
public record TeamStatisticsBlock(
        String providerTeamId,
        EnrichmentObservationState state,
        List<ProviderStatistic> statistics) {

    public TeamStatisticsBlock {
        if (providerTeamId == null || providerTeamId.isBlank() || providerTeamId.length() > 200
                || providerTeamId.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("providerTeamId is invalid");
        }
        if (state == null || statistics == null) {
            throw new IllegalArgumentException("state and statistics are required");
        }
        statistics = List.copyOf(statistics);
        switch (state) {
            case NOT_PRESENT, NULL_VALUE, EMPTY -> {
                if (!statistics.isEmpty()) {
                    throw new IllegalArgumentException("empty statistics state cannot contain metrics");
                }
            }
            case AVAILABLE -> {
                if (statistics.isEmpty()) {
                    throw new IllegalArgumentException("available statistics must contain metrics");
                }
            }
            default -> throw new IllegalArgumentException("unsupported team statistics state");
        }
    }
}
