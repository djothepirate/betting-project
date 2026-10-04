package com.bettingproject.qualification.domain;

import java.util.ArrayList;
import java.util.List;

/** Uses exact team/name/role/minutes context and uniqueness; never joins by player ID alone. */
public final class CrossEndpointPlayerIdentityAssessor {

    private CrossEndpointPlayerIdentityAssessor() {
    }

    public static List<PlayerQualityConcern> assess(
            List<PlayerEndpointIdentityEvidence> left,
            List<PlayerEndpointIdentityEvidence> right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("endpoint evidence lists are required");
        }
        var concerns = new ArrayList<PlayerQualityConcern>();
        for (PlayerEndpointIdentityEvidence first : left) {
            if (!first.sufficientForCrossEndpointMatch()) {
                continue;
            }
            List<PlayerEndpointIdentityEvidence> matches = right.stream()
                    .filter(PlayerEndpointIdentityEvidence::sufficientForCrossEndpointMatch)
                    .filter(second -> sameIdentityContext(first, second))
                    .toList();
            long leftMatches = left.stream()
                    .filter(PlayerEndpointIdentityEvidence::sufficientForCrossEndpointMatch)
                    .filter(candidate -> sameIdentityContext(first, candidate))
                    .count();
            if (matches.size() == 1 && leftMatches == 1) {
                PlayerEndpointIdentityEvidence second = matches.getFirst();
                if (!first.endpoint().equals(second.endpoint())
                        && !first.providerPlayerId().equals(second.providerPlayerId())) {
                    concerns.add(new PlayerQualityConcern(QualityIssueCode.CROSS_ENDPOINT_PLAYER_ID_MISMATCH,
                            first.providerTeamId(), first.providerPlayerId(), second.providerPlayerId()));
                }
            }
        }
        return List.copyOf(concerns);
    }

    private static boolean sameIdentityContext(
            PlayerEndpointIdentityEvidence left,
            PlayerEndpointIdentityEvidence right) {
        boolean identity = left.providerTeamId().equals(right.providerTeamId())
                && left.sourceName().equals(right.sourceName())
                && left.role().equals(right.role());
        if (!identity) { return false; }
        // Playing time disambiguates when both endpoints expose it; it is not inferred for lineups.
        return left.minutesPlayed() == null || right.minutesPlayed() == null
                || left.minutesPlayed().compareTo(right.minutesPlayed()) == 0;
    }
}
