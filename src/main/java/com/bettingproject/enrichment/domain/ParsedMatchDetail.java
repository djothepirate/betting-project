package com.bettingproject.enrichment.domain;

import java.time.Instant;
import java.util.Objects;

/** Strictly parsed native detail; no logical route or actual kickoff is inferred here. */
public record ParsedMatchDetail(
        String providerFixtureId,
        String providerCompetitionId,
        ObservedScalar sourceSeasonReference,
        ObservedScalar sourcePhaseReference,
        ObservedScalar competitionName,
        ObservedScalar competitionCountryCode,
        ObservedScalar competitionType,
        Instant scheduledKickoff,
        ObservedScalar providerStatus,
        ProviderTeamReference homeTeam,
        ProviderTeamReference awayTeam,
        ObservedScalar sourceHomeScore,
        ObservedScalar sourceAwayScore,
        EnrichmentObservationState statisticsState,
        EnrichmentObservationState eventsState) {

    public ParsedMatchDetail {
        providerFixtureId = require(providerFixtureId, "providerFixtureId", 200);
        providerCompetitionId = require(providerCompetitionId, "providerCompetitionId", 200);
        sourceSeasonReference = Objects.requireNonNull(sourceSeasonReference, "sourceSeasonReference");
        sourcePhaseReference = Objects.requireNonNull(sourcePhaseReference, "sourcePhaseReference");
        if (sourceSeasonReference.state() != EnrichmentObservationState.AVAILABLE) {
            throw new IllegalArgumentException("source season reference is required");
        }
        competitionName = Objects.requireNonNull(competitionName, "competitionName");
        if (competitionName.state() == EnrichmentObservationState.AVAILABLE
                && competitionName.type() != ObservedScalarType.TEXT) {
            throw new IllegalArgumentException("competitionName must be text when present");
        }
        competitionCountryCode = Objects.requireNonNull(competitionCountryCode, "competitionCountryCode");
        competitionType = Objects.requireNonNull(competitionType, "competitionType");
        scheduledKickoff = Objects.requireNonNull(scheduledKickoff, "scheduledKickoff");
        providerStatus = Objects.requireNonNull(providerStatus, "providerStatus");
        if (providerStatus.state() != EnrichmentObservationState.AVAILABLE
                || providerStatus.type() != ObservedScalarType.TEXT) {
            throw new IllegalArgumentException("provider status must be present as text");
        }
        homeTeam = Objects.requireNonNull(homeTeam, "homeTeam");
        awayTeam = Objects.requireNonNull(awayTeam, "awayTeam");
        if (homeTeam.providerTeamId().equals(awayTeam.providerTeamId())) {
            throw new IllegalArgumentException("provider team IDs must differ");
        }
        sourceHomeScore = Objects.requireNonNull(sourceHomeScore, "sourceHomeScore");
        sourceAwayScore = Objects.requireNonNull(sourceAwayScore, "sourceAwayScore");
        statisticsState = Objects.requireNonNull(statisticsState, "statisticsState");
        eventsState = Objects.requireNonNull(eventsState, "eventsState");
        requireFieldState(statisticsState, "statisticsState");
        requireFieldState(eventsState, "eventsState");
    }

    private static void requireFieldState(EnrichmentObservationState state, String name) {
        if (state != EnrichmentObservationState.NOT_PRESENT && state != EnrichmentObservationState.NULL_VALUE
                && state != EnrichmentObservationState.EMPTY && state != EnrichmentObservationState.AVAILABLE) {
            throw new IllegalArgumentException(name + " is invalid");
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
