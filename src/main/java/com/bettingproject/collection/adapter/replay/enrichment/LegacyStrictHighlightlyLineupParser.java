package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentPayloadParser;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.LineupSide;
import com.bettingproject.enrichment.domain.ParsedLineup;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** v1 compatibility parser: reproduces the historical ID-only derived representation. */
@Component
public final class LegacyStrictHighlightlyLineupParser implements EnrichmentPayloadParser<ParsedLineup> {
    private final StrictHighlightlyLineupParser current = new StrictHighlightlyLineupParser();

    @Override public String provider() { return "highlightly"; }
    @Override public EnrichmentFamily family() { return EnrichmentFamily.LINEUP; }
    @Override public String version() { return "highlightly-lineup-v1"; }
    @Override public boolean preferredForCollection() { return false; }

    @Override
    public ParsedLineup parse(byte[] payload, Instant scheduledKickoff, Instant receivedAt) {
        ParsedLineup parsed = current.parse(payload, scheduledKickoff, receivedAt);
        return new ParsedLineup(parsed.state(), parsed.homeTeamProviderId(), idsOnly(parsed.home()),
                parsed.awayTeamProviderId(), idsOnly(parsed.away()), parsed.assessment(), parsed.receivedAt());
    }

    private static LineupSide idsOnly(LineupSide side) {
        return new LineupSide(side.fieldPresent(), side.starterProviderIds(), List.<com.bettingproject.enrichment.domain.LineupPlayerEvidence>of());
    }
}
