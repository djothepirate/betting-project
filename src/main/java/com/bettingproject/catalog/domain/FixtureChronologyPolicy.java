package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Orders fixture observations by their source observation time and validates status transitions.
 */
public final class FixtureChronologyPolicy {

    public FixtureApplicationOutcome evaluate(
            Instant lastAuthorityObservedAt,
            FixtureCanonicalFacts currentFacts,
            Instant candidateObservedAt,
            FixtureCanonicalFacts candidateFacts) {
        Objects.requireNonNull(currentFacts, "currentFacts");
        Objects.requireNonNull(candidateObservedAt, "candidateObservedAt");
        Objects.requireNonNull(candidateFacts, "candidateFacts");

        boolean sameFacts = currentFacts.equals(candidateFacts);
        if (lastAuthorityObservedAt == null) {
            return decideNewerFacts(currentFacts, candidateFacts, sameFacts);
        }

        int chronology = candidateObservedAt.compareTo(lastAuthorityObservedAt);
        if (chronology < 0) {
            return FixtureApplicationOutcome.STALE;
        }

        if (chronology == 0) {
            return sameFacts
                    ? FixtureApplicationOutcome.UNCHANGED
                    : FixtureApplicationOutcome.EQUAL_AUTHORITY_TIME_CONFLICT;
        }

        return decideNewerFacts(currentFacts, candidateFacts, sameFacts);
    }

    private FixtureApplicationOutcome decideNewerFacts(
            FixtureCanonicalFacts currentFacts,
            FixtureCanonicalFacts candidateFacts,
            boolean sameFacts) {
        if (sameFacts) {
            return FixtureApplicationOutcome.UNCHANGED;
        }
        if (!allowsTransition(currentFacts.status(), candidateFacts.status())) {
            return FixtureApplicationOutcome.INVALID_TRANSITION;
        }
        return FixtureApplicationOutcome.UPDATED;
    }

    private boolean allowsTransition(FixtureStatus current, FixtureStatus candidate) {
        if (current == candidate) {
            return true;
        }
        return switch (current) {
            case SCHEDULED, POSTPONED -> true;
            case FINISHED, CANCELLED -> false;
        };
    }
}
