package com.bettingproject.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Compares source participants with a canonical participant order without
 * rewriting either pair.
 */
public final class FixtureIdentityPolicy {

    public FixtureIdentityMatch match(
            UUID canonicalHomeTeamId,
            UUID canonicalAwayTeamId,
            UUID sourceHomeTeamId,
            UUID sourceAwayTeamId,
            Boolean sourceNeutralVenue,
            boolean sourceParticipantsUnordered) {
        requireDistinctParticipants(canonicalHomeTeamId, canonicalAwayTeamId, "canonical");
        requireDistinctParticipants(sourceHomeTeamId, sourceAwayTeamId, "source");

        boolean exactOrder = canonicalHomeTeamId.equals(sourceHomeTeamId)
                && canonicalAwayTeamId.equals(sourceAwayTeamId);
        if (exactOrder) {
            return FixtureIdentityMatch.EXACT_ORDER;
        }

        boolean invertedOrder = canonicalHomeTeamId.equals(sourceAwayTeamId)
                && canonicalAwayTeamId.equals(sourceHomeTeamId);
        if (sourceParticipantsUnordered && invertedOrder) {
            return FixtureIdentityMatch.INVERTED_ORDER_ALLOWED;
        }

        // sourceNeutralVenue is intentionally not consulted: neutral ground never
        // grants permission to ignore the provider participant order.
        return FixtureIdentityMatch.NO_MATCH;
    }

    private void requireDistinctParticipants(UUID homeTeamId, UUID awayTeamId, String pairName) {
        Objects.requireNonNull(homeTeamId, pairName + " homeTeamId");
        Objects.requireNonNull(awayTeamId, pairName + " awayTeamId");
        if (homeTeamId.equals(awayTeamId)) {
            throw new IllegalArgumentException(pairName + " participants must be different");
        }
    }
}
