package com.bettingproject.enrichment.domain;

import java.time.Instant;
import java.util.Objects;

/** Provider-neutral result of parsing one lineup family; raw input remains in its snapshot. */
public record ParsedLineup(
        EnrichmentObservationState state,
        String homeTeamProviderId,
        LineupSide home,
        String awayTeamProviderId,
        LineupSide away,
        LineupAssessment assessment,
        Instant receivedAt) {

    public ParsedLineup {
        state = Objects.requireNonNull(state, "state");
        homeTeamProviderId = requireId(homeTeamProviderId, "homeTeamProviderId");
        home = Objects.requireNonNull(home, "home");
        awayTeamProviderId = requireId(awayTeamProviderId, "awayTeamProviderId");
        away = Objects.requireNonNull(away, "away");
        if (homeTeamProviderId.equals(awayTeamProviderId)) {
            throw new IllegalArgumentException("home and away provider team IDs must differ");
        }
        assessment = Objects.requireNonNull(assessment, "assessment");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        if (!receivedAt.equals(assessment.receivedAt())) {
            throw new IllegalArgumentException("lineup and assessment receive times must match");
        }
        validateState(state, assessment);
    }

    private static void validateState(EnrichmentObservationState state, LineupAssessment assessment) {
        switch (state) {
            case AVAILABLE -> {
                if (!assessment.isPrematchComplete()
                        && assessment.status() != LineupStatus.COMPLETE_LATE) {
                    throw new IllegalArgumentException("available lineup must contain both complete sides");
                }
            }
            case EMPTY -> {
                if (assessment.status() != LineupStatus.ABSENT) {
                    throw new IllegalArgumentException("empty lineup must be classified absent");
                }
            }
            case NOT_PRESENT, NULL_VALUE -> {
                if (assessment.status() != LineupStatus.UNKNOWN) {
                    throw new IllegalArgumentException("missing lineup field must remain unknown");
                }
            }
            case PARTIAL -> {
                if (assessment.status() == LineupStatus.ABSENT
                        || assessment.isPrematchComplete()
                        || assessment.status() == LineupStatus.COMPLETE_LATE) {
                    throw new IllegalArgumentException("partial lineup has an inconsistent assessment");
                }
            }
            case INCOMPATIBLE -> throw new IllegalArgumentException(
                    "an incompatible payload is rejected before creating a parsed lineup");
        }
    }

    private static String requireId(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 200
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
