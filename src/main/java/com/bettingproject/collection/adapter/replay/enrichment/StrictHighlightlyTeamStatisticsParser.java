package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentParseException;
import com.bettingproject.collection.application.enrichment.EnrichmentPayloadParser;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ParsedTeamStatistics;
import com.bettingproject.enrichment.domain.ProviderStatistic;
import com.bettingproject.enrichment.domain.TeamStatisticsBlock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Offline parser for the Highlightly reduced team-statistics endpoint shape. */
@Component
public final class StrictHighlightlyTeamStatisticsParser implements EnrichmentPayloadParser<ParsedTeamStatistics> {
    private static final String VERSION = "highlightly-team-stats-v1";

    @Override public String provider() { return "highlightly"; }
    @Override public EnrichmentFamily family() { return EnrichmentFamily.TEAM_STATS; }
    @Override public String version() { return VERSION; }

    @Override
    public ParsedTeamStatistics parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
        Objects.requireNonNull(receivedAt, "receivedAt");
        try {
            JsonNode root = StrictEnrichmentJson.requireArray(StrictEnrichmentJson.parse(payload));
            var teams = new ArrayList<TeamStatisticsBlock>();
            var ids = new HashSet<String>();
            for (JsonNode entry : root) {
                JsonNode team = StrictEnrichmentJson.requireObject(entry.get("team"));
                String teamId = StrictEnrichmentJson.requiredId(team, "id");
                if (!ids.add(teamId)) {
                    throw StrictEnrichmentJson.incompatible();
                }
                EnrichmentObservationState state = StrictEnrichmentJson.arrayFieldState(entry, "statistics");
                var statistics = new ArrayList<ProviderStatistic>();
                if (state == EnrichmentObservationState.AVAILABLE) {
                    for (JsonNode metric : entry.get("statistics")) {
                        StrictEnrichmentJson.requireObject(metric);
                        String name = StrictEnrichmentJson.requiredText(metric, "displayName", 128);
                        statistics.add(new ProviderStatistic(name, StrictEnrichmentJson.scalar(metric, "value")));
                    }
                }
                teams.add(new TeamStatisticsBlock(teamId, state, statistics));
            }
            if (teams.isEmpty()) {
                return new ParsedTeamStatistics(EnrichmentObservationState.EMPTY, teams);
            }
            boolean allAvailable = teams.stream()
                    .allMatch(team -> team.state() == EnrichmentObservationState.AVAILABLE);
            return new ParsedTeamStatistics(allAvailable ? EnrichmentObservationState.AVAILABLE
                    : EnrichmentObservationState.PARTIAL, teams);
        } catch (EnrichmentParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw StrictEnrichmentJson.incompatible();
        }
    }
}
