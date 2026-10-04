package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentParseException;
import com.bettingproject.collection.application.enrichment.EnrichmentPayloadParser;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.ObservedScalar;
import com.bettingproject.enrichment.domain.ObservedScalarType;
import com.bettingproject.enrichment.domain.ParsedMatchDetail;
import com.bettingproject.enrichment.domain.ProviderTeamReference;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Offline parser for football-data.org native match-detail snapshots. */
@Component
public final class StrictFootballDataDetailParser implements EnrichmentPayloadParser<ParsedMatchDetail> {
    private static final String VERSION = "football-data-match-detail-v1";
    private static final Set<String> KNOWN_STATUSES = Set.of(
            "SCHEDULED", "TIMED", "POSTPONED", "CANCELLED", "FINISHED", "AWARDED",
            "IN_PLAY", "PAUSED", "SUSPENDED", "EXTRA_TIME", "PENALTY_SHOOTOUT");

    @Override public String provider() { return "football-data.org"; }
    @Override public EnrichmentFamily family() { return EnrichmentFamily.MATCH_DETAIL; }
    @Override public String version() { return VERSION; }

    @Override
    public ParsedMatchDetail parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
        Objects.requireNonNull(receivedAt, "receivedAt");
        try {
            JsonNode root = StrictEnrichmentJson.requireObject(StrictEnrichmentJson.parse(payload));
            JsonNode competition = StrictEnrichmentJson.requireObject(root.get("competition"));
            JsonNode season = StrictEnrichmentJson.requireObject(root.get("season"));
            String status = StrictEnrichmentJson.requiredText(root, "status", 64);
            if (!KNOWN_STATUSES.contains(status)) {
                throw StrictEnrichmentJson.incompatible();
            }
            JsonNode score = StrictEnrichmentJson.nestedObject(root, "score", "fullTime");
            return new ParsedMatchDetail(
                    StrictEnrichmentJson.requiredId(root, "id"),
                    StrictEnrichmentJson.requiredId(competition, "id"),
                    positiveSeason(StrictEnrichmentJson.scalar(season, "id")),
                    StrictEnrichmentJson.textScalar(root, "stage"),
                    StrictEnrichmentJson.textScalar(competition, "name"),
                    StrictEnrichmentJson.nestedScalar(root, "area", "code"),
                    StrictEnrichmentJson.textScalar(competition, "type"),
                    StrictEnrichmentJson.requiredInstant(root, "utcDate"),
                    StrictEnrichmentJson.scalar(root, "status"),
                    team(StrictEnrichmentJson.requireObject(root.get("homeTeam"))),
                    team(StrictEnrichmentJson.requireObject(root.get("awayTeam"))),
                    scoreScalar(score, "home"), scoreScalar(score, "away"),
                    StrictEnrichmentJson.arrayFieldState(root, "statistics"),
                    StrictEnrichmentJson.arrayFieldState(root, "events"));
        } catch (EnrichmentParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw StrictEnrichmentJson.incompatible();
        }
    }

    private static ObservedScalar positiveSeason(ObservedScalar season) {
        if (season.type() != ObservedScalarType.INTEGER || season.decimalValue().signum() <= 0) {
            throw StrictEnrichmentJson.incompatible();
        }
        return season;
    }

    private static ProviderTeamReference team(JsonNode value) {
        return new ProviderTeamReference(StrictEnrichmentJson.requiredId(value, "id"),
                StrictEnrichmentJson.requiredText(value, "name", 256),
                StrictEnrichmentJson.textScalar(value, "countryCode"));
    }

    private static ObservedScalar scoreScalar(JsonNode score, String field) {
        if (score == null) {
            return ObservedScalar.missing();
        }
        if (score.isNull()) {
            return ObservedScalar.nullValue();
        }
        return StrictEnrichmentJson.scalar(score, field);
    }
}
