package com.bettingproject.enrichment.domain;

import java.util.HashSet;
import java.util.List;

/** Box-score entries remain grouped by the provider's exact team reference. */
public record PlayerStatisticsTeam(
        String providerTeamId,
        EnrichmentObservationState state,
        List<ProviderPlayerStatistics> players) {

    public PlayerStatisticsTeam {
        if (providerTeamId == null || providerTeamId.isBlank() || providerTeamId.length() > 200
                || providerTeamId.codePoints().anyMatch(Character::isISOControl)
                || state == null || players == null) {
            throw new IllegalArgumentException("team player statistics are invalid");
        }
        players = List.copyOf(players);
        var ids = new HashSet<String>();
        for (ProviderPlayerStatistics player : players) {
            if (!ids.add(player.providerPlayerId())) {
                throw new IllegalArgumentException("player IDs must be unique within a team response");
            }
        }
        if (state == EnrichmentObservationState.EMPTY && !players.isEmpty()
                || state == EnrichmentObservationState.AVAILABLE && players.isEmpty()) {
            throw new IllegalArgumentException("player list does not match its state");
        }
        if (state != EnrichmentObservationState.EMPTY && state != EnrichmentObservationState.AVAILABLE
                && state != EnrichmentObservationState.NOT_PRESENT
                && state != EnrichmentObservationState.NULL_VALUE) {
            throw new IllegalArgumentException("unsupported player list state");
        }
    }
}
