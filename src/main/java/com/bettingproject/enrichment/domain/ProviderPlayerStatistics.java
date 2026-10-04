package com.bettingproject.enrichment.domain;

import java.util.List;
import java.util.Objects;

/** One player's source statistics, without cross-endpoint identity assumptions. */
public record ProviderPlayerStatistics(
        String providerPlayerId,
        String sourceName,
        ObservedScalar fullName,
        ObservedScalar matchRating,
        ObservedScalar shirtNumber,
        ObservedScalar position,
        ObservedScalar captain,
        ObservedScalar substitute,
        ObservedScalar offsides,
        ObservedScalar minutesPlayed,
        EnrichmentObservationState statisticsState,
        List<ProviderStatistic> statistics) {

    public ProviderPlayerStatistics {
        providerPlayerId = require(providerPlayerId, "providerPlayerId", 200);
        sourceName = require(sourceName, "sourceName", 256);
        fullName = Objects.requireNonNull(fullName, "fullName");
        matchRating = Objects.requireNonNull(matchRating, "matchRating");
        shirtNumber = Objects.requireNonNull(shirtNumber, "shirtNumber");
        position = Objects.requireNonNull(position, "position");
        captain = Objects.requireNonNull(captain, "captain");
        substitute = Objects.requireNonNull(substitute, "substitute");
        offsides = Objects.requireNonNull(offsides, "offsides");
        minutesPlayed = Objects.requireNonNull(minutesPlayed, "minutesPlayed");
        requireType(position, ObservedScalarType.TEXT, "position");
        requireType(captain, ObservedScalarType.BOOLEAN, "captain");
        requireType(substitute, ObservedScalarType.BOOLEAN, "substitute");
        requireNonNegativeInteger(shirtNumber, "shirtNumber");
        requireNonNegativeInteger(offsides, "offsides");
        requireNonNegativeInteger(minutesPlayed, "minutesPlayed");
        statisticsState = Objects.requireNonNull(statisticsState, "statisticsState");
        statistics = List.copyOf(Objects.requireNonNull(statistics, "statistics"));
        if (statisticsState == EnrichmentObservationState.EMPTY && !statistics.isEmpty()
                || statisticsState == EnrichmentObservationState.AVAILABLE && statistics.isEmpty()) {
            throw new IllegalArgumentException("player metrics do not match their state");
        }
        if (statisticsState != EnrichmentObservationState.NOT_PRESENT
                && statisticsState != EnrichmentObservationState.NULL_VALUE
                && statisticsState != EnrichmentObservationState.EMPTY
                && statisticsState != EnrichmentObservationState.AVAILABLE) {
            throw new IllegalArgumentException("unsupported player metric state");
        }
    }

    private static void requireType(ObservedScalar scalar, ObservedScalarType expected, String name) {
        if (scalar.state() == EnrichmentObservationState.AVAILABLE && scalar.type() != expected) {
            throw new IllegalArgumentException(name + " has an invalid source type");
        }
    }

    private static void requireNonNegativeInteger(ObservedScalar scalar, String name) {
        if (scalar.state() == EnrichmentObservationState.AVAILABLE
                && (scalar.type() != ObservedScalarType.INTEGER || scalar.decimalValue().signum() < 0)) {
            throw new IllegalArgumentException(name + " must be a non-negative integer");
        }
    }

    private static String require(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
