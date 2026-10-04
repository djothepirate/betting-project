package com.bettingproject.enrichment.domain;

import java.util.HashSet;
import java.util.List;

/** Parsed box-score response; never joins players to another endpoint on ID alone. */
public record ParsedPlayerStatistics(EnrichmentObservationState state, List<PlayerStatisticsTeam> teams) {
    public ParsedPlayerStatistics {
        if (state == null || teams == null) {
            throw new IllegalArgumentException("state and teams are required");
        }
        teams = List.copyOf(teams);
        var ids = new HashSet<String>();
        for (PlayerStatisticsTeam team : teams) {
            if (!ids.add(team.providerTeamId())) {
                throw new IllegalArgumentException("team IDs must be unique");
            }
        }
        if (state == EnrichmentObservationState.EMPTY && !teams.isEmpty()
                || state == EnrichmentObservationState.AVAILABLE && teams.isEmpty()) {
            throw new IllegalArgumentException("response does not match its state");
        }
        if (state != EnrichmentObservationState.EMPTY && state != EnrichmentObservationState.AVAILABLE
                && state != EnrichmentObservationState.PARTIAL) {
            throw new IllegalArgumentException("unsupported response state");
        }
        if (state == EnrichmentObservationState.PARTIAL
                && (teams.isEmpty() || teams.stream().noneMatch(team -> team.state() == EnrichmentObservationState.AVAILABLE)
                || teams.stream().allMatch(team -> team.state() == EnrichmentObservationState.AVAILABLE))) {
            throw new IllegalArgumentException("partial response must include both available and unavailable teams");
        }
    }
}
