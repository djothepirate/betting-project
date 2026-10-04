package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentParseException;
import com.bettingproject.collection.application.enrichment.EnrichmentPayloadParser;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ParsedMatchDetail;
import com.bettingproject.enrichment.domain.ProviderTeamReference;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Offline parser for Highlightly native match-detail snapshots. */
@Component
public final class StrictHighlightlyDetailParser implements EnrichmentPayloadParser<ParsedMatchDetail> {
    private static final String VERSION = "highlightly-match-detail-v1";
    private static final Set<String> KNOWN_STATUSES = Set.of(
            "Not started", "Finished", "Finished after penalties", "Finished after extra time",
            "Postponed", "Cancelled", "In progress", "Half time", "Halftime", "Live");

    @Override public String provider() { return "highlightly"; }
    @Override public EnrichmentFamily family() { return EnrichmentFamily.MATCH_DETAIL; }
    @Override public String version() { return VERSION; }

    @Override
    public ParsedMatchDetail parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
        Objects.requireNonNull(receivedAt, "receivedAt");
        try {
            JsonNode root = StrictEnrichmentJson.requireObject(StrictEnrichmentJson.parse(payload));
            JsonNode league = StrictEnrichmentJson.requireObject(root.get("league"));
            JsonNode state = StrictEnrichmentJson.requireObject(root.get("state"));
            String status = StrictEnrichmentJson.requiredText(state, "description", 128);
            if (!KNOWN_STATUSES.contains(status)) {
                throw StrictEnrichmentJson.incompatible();
            }
            JsonNode home = StrictEnrichmentJson.requireObject(root.get("homeTeam"));
            JsonNode away = StrictEnrichmentJson.requireObject(root.get("awayTeam"));
            JsonNode score = StrictEnrichmentJson.nestedObject(state, "score", "current");
            return new ParsedMatchDetail(
                    StrictEnrichmentJson.requiredId(root, "id"),
                    StrictEnrichmentJson.requiredId(league, "id"),
                    positiveSeason(StrictEnrichmentJson.scalar(league, "season")),
                    StrictEnrichmentJson.textScalar(root, "round"),
                    StrictEnrichmentJson.textScalar(league, "name"),
                    StrictEnrichmentJson.nestedScalar(root, "country", "code"),
                    StrictEnrichmentJson.textScalar(league, "type"),
                    StrictEnrichmentJson.requiredInstant(root, "date"),
                    StrictEnrichmentJson.scalar(state, "description"),
                    team(home), team(away),
                    scoreScalar(score, "home"), scoreScalar(score, "away"),
                    StrictEnrichmentJson.arrayFieldState(root, "statistics"),
                    StrictEnrichmentJson.arrayFieldState(root, "events"));
        } catch (EnrichmentParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw StrictEnrichmentJson.incompatible();
        }
    }

    private static com.bettingproject.enrichment.domain.ObservedScalar positiveSeason(
            com.bettingproject.enrichment.domain.ObservedScalar season) {
        if (!season.isNumeric() || season.decimalValue().signum() <= 0
                || season.type() != com.bettingproject.enrichment.domain.ObservedScalarType.INTEGER) {
            throw StrictEnrichmentJson.incompatible();
        }
        return season;
    }

    private static ProviderTeamReference team(JsonNode value) {
        return new ProviderTeamReference(StrictEnrichmentJson.requiredId(value, "id"),
                StrictEnrichmentJson.requiredText(value, "name", 256),
                StrictEnrichmentJson.textScalar(value, "countryCode"));
    }

    private static com.bettingproject.enrichment.domain.ObservedScalar scoreScalar(JsonNode score, String field) {
        if (score == null) {
            return com.bettingproject.enrichment.domain.ObservedScalar.missing();
        }
        if (score.isNull()) {
            return com.bettingproject.enrichment.domain.ObservedScalar.nullValue();
        }
        return StrictEnrichmentJson.scalar(score, field);
    }
}
