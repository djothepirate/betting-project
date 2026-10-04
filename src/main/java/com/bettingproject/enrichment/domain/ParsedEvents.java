package com.bettingproject.enrichment.domain;

import java.util.List;

/** Source-ordered event observations with no inferred period or elapsed time. */
public record ParsedEvents(EnrichmentObservationState state, List<ProviderEvent> events) {
    public ParsedEvents {
        if (state == null || events == null) {
            throw new IllegalArgumentException("state and events are required");
        }
        events = List.copyOf(events);
        if (state == EnrichmentObservationState.EMPTY && !events.isEmpty()) {
            throw new IllegalArgumentException("empty response cannot contain events");
        }
        if (state == EnrichmentObservationState.AVAILABLE && events.isEmpty()) {
            throw new IllegalArgumentException("available response must contain events");
        }
        if (state != EnrichmentObservationState.EMPTY && state != EnrichmentObservationState.AVAILABLE) {
            throw new IllegalArgumentException("unsupported event response state");
        }
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).sourceOrdinal() != i) {
                throw new IllegalArgumentException("event source order must be contiguous");
            }
        }
    }
}
