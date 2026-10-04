package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentParseException;
import com.bettingproject.collection.application.enrichment.EnrichmentPayloadParser;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.LineupAssessment;
import com.bettingproject.enrichment.domain.LineupPlayerEvidence;
import com.bettingproject.enrichment.domain.LineupSide;
import com.bettingproject.enrichment.domain.LineupStatus;
import com.bettingproject.enrichment.domain.ParsedLineup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Offline parser for the Highlightly lineup response shape captured by ENR-001 fixtures. */
@Component
public final class StrictHighlightlyLineupParser implements EnrichmentPayloadParser<ParsedLineup> {
    private static final String PROVIDER = "highlightly";
    private static final String VERSION = "highlightly-lineup-v2";
    private static final int MAX_PAYLOAD_BYTES = 5 * 1024 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public EnrichmentFamily family() {
        return EnrichmentFamily.LINEUP;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public ParsedLineup parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
        Objects.requireNonNull(scheduledKickoff, "scheduledKickoff");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw incompatible();
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            if (root == null || !root.isObject()) {
                throw incompatible();
            }
            Side home = side(root.get("homeTeam"));
            Side away = side(root.get("awayTeam"));
            if (home == null || away == null || home.providerTeamId() == null || away.providerTeamId() == null
                    || home.providerTeamId().equals(away.providerTeamId())) {
                throw incompatible();
            }

            LineupSide homeLineup = home.lineup();
            LineupSide awayLineup = away.lineup();
            LineupAssessment assessment = LineupAssessment.assess(
                    homeLineup, awayLineup, scheduledKickoff, receivedAt);
            EnrichmentObservationState state = state(home, away, assessment.status());
            return new ParsedLineup(state, home.providerTeamId(), homeLineup,
                    away.providerTeamId(), awayLineup, assessment, receivedAt);
        } catch (EnrichmentParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw incompatible();
        }
    }

    private static Side side(JsonNode team) {
        if (team == null || team.isNull() || !team.isObject()) {
            return null;
        }
        String teamId = numericId(team.get("id"));
        if (teamId == null) {
            return null;
        }
        JsonNode lineup = team.get("initialLineup");
        if (lineup == null || lineup.isNull()) {
            return new Side(teamId, new LineupSide(false, java.util.List.of()), lineup == null
                    ? EnrichmentObservationState.NOT_PRESENT : EnrichmentObservationState.NULL_VALUE);
        }
        if (!lineup.isArray()) {
            throw incompatible();
        }
        var starterIds = new ArrayList<String>();
        var starterEvidence = new ArrayList<LineupPlayerEvidence>();
        for (JsonNode formationLine : lineup) {
            if (!formationLine.isArray()) {
                throw incompatible();
            }
            for (JsonNode player : formationLine) {
                if (!player.isObject()) {
                    throw incompatible();
                }
                String playerId = numericId(player.get("id"));
                // Preserve the starter slot. A blank ID prevents a false complete classification.
                starterIds.add(playerId == null ? "" : playerId);
                // Keep evidence aligned with the raw starter position even when its ID is unusable.
                starterEvidence.add(new LineupPlayerEvidence(playerId == null ? "" : playerId,
                        StrictEnrichmentJson.textScalar(player, "name"),
                        StrictEnrichmentJson.textScalar(player, "position")));
            }
        }
        return new Side(teamId, new LineupSide(true, starterIds, starterEvidence),
                starterIds.isEmpty() ? EnrichmentObservationState.EMPTY : EnrichmentObservationState.AVAILABLE);
    }

    private static EnrichmentObservationState state(Side home, Side away, LineupStatus status) {
        if (status == LineupStatus.COMPLETE || status == LineupStatus.COMPLETE_LATE) {
            return EnrichmentObservationState.AVAILABLE;
        }
        if (home.state() == EnrichmentObservationState.EMPTY
                && away.state() == EnrichmentObservationState.EMPTY) {
            return EnrichmentObservationState.EMPTY;
        }
        if (home.state() == EnrichmentObservationState.NOT_PRESENT
                && away.state() == EnrichmentObservationState.NOT_PRESENT) {
            return EnrichmentObservationState.NOT_PRESENT;
        }
        if (home.state() == EnrichmentObservationState.NULL_VALUE
                && away.state() == EnrichmentObservationState.NULL_VALUE) {
            return EnrichmentObservationState.NULL_VALUE;
        }
        return EnrichmentObservationState.PARTIAL;
    }

    private static String numericId(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            return null;
        }
        return Long.toString(value.longValue());
    }

    private static EnrichmentParseException incompatible() {
        return new EnrichmentParseException("INCOMPATIBLE_PAYLOAD");
    }

    private record Side(String providerTeamId, LineupSide lineup, EnrichmentObservationState state) { }
}
