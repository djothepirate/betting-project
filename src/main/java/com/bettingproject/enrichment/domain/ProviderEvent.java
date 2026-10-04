package com.bettingproject.enrichment.domain;

import java.util.Objects;

/** One event in source order. Time notation is not interpreted as an actual match clock. */
public record ProviderEvent(
        int sourceOrdinal,
        String providerTeamId,
        ObservedScalar sourceTimeNotation,
        ObservedScalar sourceType,
        ObservedScalar playerId,
        ObservedScalar playerName,
        ObservedScalar assistingPlayerId,
        ObservedScalar assistName,
        ObservedScalar substitutedPlayerName) {

    public ProviderEvent {
        if (sourceOrdinal < 0) {
            throw new IllegalArgumentException("sourceOrdinal must not be negative");
        }
        if (providerTeamId == null || providerTeamId.isBlank() || providerTeamId.length() > 200
                || providerTeamId.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("providerTeamId is invalid");
        }
        sourceTimeNotation = Objects.requireNonNull(sourceTimeNotation, "sourceTimeNotation");
        sourceType = Objects.requireNonNull(sourceType, "sourceType");
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName");
        assistingPlayerId = Objects.requireNonNull(assistingPlayerId, "assistingPlayerId");
        assistName = Objects.requireNonNull(assistName, "assistName");
        substitutedPlayerName = Objects.requireNonNull(substitutedPlayerName, "substitutedPlayerName");
        if (sourceTimeNotation.type() != ObservedScalarType.TEXT
                || sourceType.type() != ObservedScalarType.TEXT) {
            throw new IllegalArgumentException("event source time and type must be text");
        }
        requireOptionalPositiveId(playerId, "playerId");
        requireOptionalPositiveId(assistingPlayerId, "assistingPlayerId");
    }

    private static void requireOptionalPositiveId(ObservedScalar scalar, String name) {
        if (scalar.state() == EnrichmentObservationState.AVAILABLE
                && (scalar.type() != ObservedScalarType.INTEGER || scalar.decimalValue().signum() <= 0)) {
            throw new IllegalArgumentException(name + " must be a positive source identifier");
        }
    }
}
