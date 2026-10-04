package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentParseException;
import com.bettingproject.collection.application.enrichment.EnrichmentPayloadParser;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ParsedPlayerStatistics;
import com.bettingproject.enrichment.domain.PlayerStatisticsTeam;
import com.bettingproject.enrichment.domain.ProviderPlayerStatistics;
import com.bettingproject.enrichment.domain.ProviderStatistic;
import com.bettingproject.enrichment.domain.ObservedScalar;
import com.bettingproject.enrichment.domain.ObservedScalarType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Offline parser for the Highlightly team/player box-score response. */
@Component
public final class StrictHighlightlyPlayerStatisticsParser
        implements EnrichmentPayloadParser<ParsedPlayerStatistics> {
    private static final String VERSION = "highlightly-player-stats-v1";

    @Override public String provider() { return "highlightly"; }
    @Override public EnrichmentFamily family() { return EnrichmentFamily.PLAYER_STATS; }
    @Override public String version() { return VERSION; }

    @Override
    public ParsedPlayerStatistics parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
        Objects.requireNonNull(receivedAt, "receivedAt");
        try {
            JsonNode root = StrictEnrichmentJson.requireArray(StrictEnrichmentJson.parse(payload));
            var teams = new ArrayList<PlayerStatisticsTeam>();
            var teamIds = new HashSet<String>();
            for (JsonNode value : root) {
                JsonNode teamEntry = StrictEnrichmentJson.requireObject(value);
                JsonNode team = StrictEnrichmentJson.requireObject(teamEntry.get("team"));
                String teamId = StrictEnrichmentJson.requiredId(team, "id");
                if (!teamIds.add(teamId)) {
                    throw StrictEnrichmentJson.incompatible();
                }
                EnrichmentObservationState playerState = StrictEnrichmentJson.arrayFieldState(teamEntry, "players");
                var players = new ArrayList<ProviderPlayerStatistics>();
                if (playerState == EnrichmentObservationState.AVAILABLE) {
                    for (JsonNode playerValue : teamEntry.get("players")) {
                        players.add(player(playerValue));
                    }
                }
                teams.add(new PlayerStatisticsTeam(teamId, playerState, players));
            }
            if (teams.isEmpty()) {
                return new ParsedPlayerStatistics(EnrichmentObservationState.EMPTY, teams);
            }
            boolean allAvailable = teams.stream()
                    .allMatch(team -> team.state() == EnrichmentObservationState.AVAILABLE);
            return new ParsedPlayerStatistics(allAvailable ? EnrichmentObservationState.AVAILABLE
                    : EnrichmentObservationState.PARTIAL, teams);
        } catch (EnrichmentParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw StrictEnrichmentJson.incompatible();
        }
    }

    private static ProviderPlayerStatistics player(JsonNode value) {
        JsonNode player = StrictEnrichmentJson.requireObject(value);
        String playerId = StrictEnrichmentJson.requiredId(player, "id");
        String name = StrictEnrichmentJson.requiredText(player, "name", 256);
        ObservedScalar fullName = StrictEnrichmentJson.scalar(player, "fullName");
        ObservedScalar matchRating = StrictEnrichmentJson.scalar(player, "matchRating");
        ObservedScalar shirtNumber = StrictEnrichmentJson.scalar(player, "shirtNumber");
        ObservedScalar position = StrictEnrichmentJson.textScalar(player, "position");
        ObservedScalar captain = StrictEnrichmentJson.scalar(player, "isCaptain");
        ObservedScalar substitute = StrictEnrichmentJson.scalar(player, "isSubstitute");
        ObservedScalar offsides = StrictEnrichmentJson.scalar(player, "offsides");
        ObservedScalar minutes = StrictEnrichmentJson.scalar(player, "minutesPlayed");

        JsonNode sourceMetrics = player.get("statistics");
        EnrichmentObservationState metricsState;
        var metrics = new ArrayList<ProviderStatistic>();
        if (sourceMetrics == null) {
            metricsState = EnrichmentObservationState.NOT_PRESENT;
        } else if (sourceMetrics.isNull()) {
            metricsState = EnrichmentObservationState.NULL_VALUE;
        } else {
            StrictEnrichmentJson.requireObject(sourceMetrics);
            for (var field : sourceMetrics.properties()) {
                metrics.add(new ProviderStatistic(field.getKey(), StrictEnrichmentJson.scalar(field.getValue(), false)));
            }
            metricsState = metrics.isEmpty() ? EnrichmentObservationState.EMPTY
                    : EnrichmentObservationState.AVAILABLE;
        }
        return new ProviderPlayerStatistics(playerId, name, fullName, matchRating, shirtNumber, position,
                captain, substitute, offsides, minutes, metricsState, metrics);
    }
}
