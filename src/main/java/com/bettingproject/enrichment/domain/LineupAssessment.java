package com.bettingproject.enrichment.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Structural lineup assessment; it does not infer provider confirmation or actual kickoff. */
public final class LineupAssessment {

    private static final int EXPECTED_STARTERS = 11;
    private final LineupStatus status;
    private final int homeStarterCount;
    private final int awayStarterCount;
    private final Instant receivedAt;

    private LineupAssessment(LineupStatus status, int homeStarterCount, int awayStarterCount, Instant receivedAt) {
        this.status = Objects.requireNonNull(status, "status");
        if (homeStarterCount < 0 || awayStarterCount < 0) {
            throw new IllegalArgumentException("starter counts must not be negative");
        }
        if (status == LineupStatus.ABSENT && (homeStarterCount != 0 || awayStarterCount != 0)) {
            throw new IllegalArgumentException("an absent lineup has no starters on either side");
        }
        if ((status == LineupStatus.COMPLETE || status == LineupStatus.COMPLETE_LATE)
                && (homeStarterCount != EXPECTED_STARTERS || awayStarterCount != EXPECTED_STARTERS)) {
            throw new IllegalArgumentException("a complete lineup has eleven starters on each side");
        }
        if (status == LineupStatus.INCOMPLETE && homeStarterCount == 0 && awayStarterCount == 0) {
            throw new IllegalArgumentException("an incomplete lineup must contain at least one observed starter");
        }
        this.homeStarterCount = homeStarterCount;
        this.awayStarterCount = awayStarterCount;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    }

    public static LineupAssessment assess(
            LineupSide home,
            LineupSide away,
            Instant scheduledKickoff,
            Instant receivedAt) {
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(away, "away");
        Objects.requireNonNull(scheduledKickoff, "scheduledKickoff");
        Objects.requireNonNull(receivedAt, "receivedAt");

        int homeCount = home.starterProviderIds().size();
        int awayCount = away.starterProviderIds().size();
        LineupStatus status;
        if (!home.fieldPresent() || !away.fieldPresent()) {
            status = LineupStatus.UNKNOWN;
        } else if (homeCount == 0 && awayCount == 0) {
            status = LineupStatus.ABSENT;
        } else if (isComplete(home.starterProviderIds()) && isComplete(away.starterProviderIds())) {
            status = receivedAt.isBefore(scheduledKickoff)
                    ? LineupStatus.COMPLETE
                    : LineupStatus.COMPLETE_LATE;
        } else {
            status = LineupStatus.INCOMPLETE;
        }
        return new LineupAssessment(status, homeCount, awayCount, receivedAt);
    }

    public boolean isPrematchComplete() {
        return status == LineupStatus.COMPLETE;
    }

    public LineupStatus status() {
        return status;
    }

    /** JavaBean accessor keeps the persisted provider-neutral representation explicit. */
    public LineupStatus getStatus() {
        return status;
    }

    public int homeStarterCount() {
        return homeStarterCount;
    }

    public int getHomeStarterCount() {
        return homeStarterCount;
    }

    public int awayStarterCount() {
        return awayStarterCount;
    }

    public int getAwayStarterCount() {
        return awayStarterCount;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    private static boolean isComplete(java.util.List<String> providerIds) {
        if (providerIds.size() != EXPECTED_STARTERS) {
            return false;
        }
        Set<String> unique = new HashSet<>();
        for (String providerId : providerIds) {
            if (providerId == null || providerId.isBlank() || containsControl(providerId)
                    || !unique.add(providerId)) {
                return false;
            }
        }
        return unique.size() == EXPECTED_STARTERS;
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
