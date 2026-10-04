package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentParseException;
import com.bettingproject.collection.application.enrichment.EnrichmentPayloadParser;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ParsedEvents;
import com.bettingproject.enrichment.domain.ProviderEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Offline parser that retains the source event order and time notation verbatim. */
@Component
public final class StrictHighlightlyEventsParser implements EnrichmentPayloadParser<ParsedEvents> {
    private static final String VERSION = "highlightly-events-v1";

    @Override public String provider() { return "highlightly"; }
    @Override public EnrichmentFamily family() { return EnrichmentFamily.EVENTS; }
    @Override public String version() { return VERSION; }

    @Override
    public ParsedEvents parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
        Objects.requireNonNull(receivedAt, "receivedAt");
        try {
            JsonNode root = StrictEnrichmentJson.requireArray(StrictEnrichmentJson.parse(payload));
            var events = new ArrayList<ProviderEvent>();
            for (JsonNode value : root) {
                JsonNode event = StrictEnrichmentJson.requireObject(value);
                JsonNode team = StrictEnrichmentJson.requireObject(event.get("team"));
                events.add(new ProviderEvent(events.size(), StrictEnrichmentJson.requiredId(team, "id"),
                        StrictEnrichmentJson.scalar(event, "time"),
                        StrictEnrichmentJson.scalar(event, "type"),
                        StrictEnrichmentJson.optionalPositiveIdScalar(event, "playerId"),
                        StrictEnrichmentJson.scalar(event, "player"),
                        StrictEnrichmentJson.optionalPositiveIdScalar(event, "assistingPlayerId"),
                        StrictEnrichmentJson.scalar(event, "assist"),
                        StrictEnrichmentJson.scalar(event, "substituted")));
            }
            return new ParsedEvents(events.isEmpty() ? EnrichmentObservationState.EMPTY
                    : EnrichmentObservationState.AVAILABLE, events);
        } catch (EnrichmentParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw StrictEnrichmentJson.incompatible();
        }
    }
}
