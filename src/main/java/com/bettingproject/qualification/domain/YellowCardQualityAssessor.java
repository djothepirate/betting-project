package com.bettingproject.qualification.domain;

import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ObservedScalar;
import com.bettingproject.enrichment.domain.ParsedEvents;
import com.bettingproject.enrichment.domain.ParsedPlayerStatistics;
import com.bettingproject.enrichment.domain.ProviderEvent;
import com.bettingproject.enrichment.domain.ProviderPlayerStatistics;
import com.bettingproject.enrichment.domain.ProviderStatistic;
import com.bettingproject.enrichment.domain.PlayerStatisticsTeam;
import java.util.LinkedHashSet;
import java.util.List;

/** Reports only a missing yellow-card metric when a unique exact team/player/name match exists. */
public final class YellowCardQualityAssessor {

    private YellowCardQualityAssessor() {
    }

    public static List<PlayerQualityConcern> assess(ParsedEvents events, ParsedPlayerStatistics playerStats) {
        if (events == null || playerStats == null) {
            throw new IllegalArgumentException("parsed source families are required");
        }
        var concerns = new LinkedHashSet<PlayerQualityConcern>();
        for (ProviderEvent event : events.events()) {
            if (!isYellowCard(event) || !event.playerId().isNumeric() || !isText(event.playerName())) {
                continue;
            }
            List<PlayerStatisticsTeam> teamMatches = playerStats.teams().stream()
                    .filter(team -> team.providerTeamId().equals(event.providerTeamId()))
                    .toList();
            if (teamMatches.size() != 1 || teamMatches.getFirst().state() != EnrichmentObservationState.AVAILABLE) {
                continue;
            }
            List<ProviderPlayerStatistics> playerMatches = teamMatches.getFirst().players().stream()
                    .filter(player -> player.providerPlayerId().equals(event.playerId().value()))
                    .filter(player -> player.sourceName().equals(event.playerName().value())
                            || isText(player.fullName()) && player.fullName().value().equals(event.playerName().value()))
                    .toList();
            if (playerMatches.size() != 1) {
                continue;
            }
            ProviderPlayerStatistics player = playerMatches.getFirst();
            if (!hasYellowCardMetric(player.statistics())) {
                concerns.add(new PlayerQualityConcern(QualityIssueCode.MISSING_PLAYER_YELLOW_CARD,
                        event.providerTeamId(), player.providerPlayerId(), null));
            }
        }
        return List.copyOf(concerns);
    }

    private static boolean isYellowCard(ProviderEvent event) {
        return isText(event.sourceType())
                && (event.sourceType().value().equals("Yellow Card")
                || event.sourceType().value().equals("Second Yellow Card"));
    }

    private static boolean isText(ObservedScalar value) {
        return value.state() == EnrichmentObservationState.AVAILABLE
                && value.type() == com.bettingproject.enrichment.domain.ObservedScalarType.TEXT;
    }

    private static boolean hasYellowCardMetric(List<ProviderStatistic> statistics) {
        return statistics.stream().anyMatch(metric -> metric.sourceName().equals("cardsYellow")
                && metric.value().state() == EnrichmentObservationState.AVAILABLE
                && metric.value().isNumeric());
    }
}
