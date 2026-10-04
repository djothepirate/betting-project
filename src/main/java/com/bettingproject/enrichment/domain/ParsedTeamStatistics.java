package com.bettingproject.enrichment.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parsed team-statistics response; source team order is preserved but is not semantic. */
public record ParsedTeamStatistics(EnrichmentObservationState state, List<TeamStatisticsBlock> teams) {
    public ParsedTeamStatistics {
        if (state == null || teams == null) {
            throw new IllegalArgumentException("state and teams are required");
        }
        teams = List.copyOf(teams);
        Set<String> teamIds = new HashSet<>();
        for (TeamStatisticsBlock team : teams) {
            if (!teamIds.add(team.providerTeamId())) {
                throw new IllegalArgumentException("provider team IDs must be unique");
            }
        }
        if (state == EnrichmentObservationState.EMPTY && !teams.isEmpty()) {
            throw new IllegalArgumentException("empty response cannot contain team blocks");
        }
        if (state == EnrichmentObservationState.AVAILABLE && teams.isEmpty()) {
            throw new IllegalArgumentException("available response must contain team blocks");
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
