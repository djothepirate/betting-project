package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureObservationTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T11:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T11:00:01Z");

    @Test
    void carriesV3SourceFactsWithoutInferringParticipantOrderFromNeutrality() {
        FixtureObservation observation = observation(
                UUID.randomUUID(),
                FixtureObservation.ObservationStatus.NORMALIZED,
                null,
                true,
                false);

        assertThat(observation.sourceSchemaVersion()).isEqualTo("cal01-fixture-v3");
        assertThat(observation.sourceSeason()).isEqualTo("2026/2027");
        assertThat(observation.sourceNeutralVenue()).isTrue();
        assertThat(observation.sourceParticipantsUnordered()).isFalse();
    }

    @Test
    void normalizedObservationRequiresCanonicalFixtureAndNoReasonCode() {
        assertThatThrownBy(() -> observation(
                null,
                FixtureObservation.ObservationStatus.NORMALIZED,
                null,
                null,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonicalFixtureId");
    }

    @Test
    void blockedObservationRequiresReasonCode() {
        assertThatThrownBy(() -> observation(
                null,
                FixtureObservation.ObservationStatus.BLOCKED,
                " ",
                null,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCode");
    }

    @Test
    void rejectsAReplaySchemaThatCannotProduceFixtureObservations() {
        assertThatThrownBy(() -> new FixtureObservation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "highlightly", "fixture-1", "competition-1", "home-1", "away-1",
                "cal01-fixture-v1", null, null, false,
                Instant.parse("2026-09-05T12:00:00Z"), "SCHEDULED", "REGULAR_SEASON",
                FixtureObservation.ObservationStatus.NORMALIZED, null, OBSERVED_AT, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceSchemaVersion");
    }

    private FixtureObservation observation(
            UUID canonicalFixtureId,
            FixtureObservation.ObservationStatus status,
            String reasonCode,
            Boolean neutralVenue,
            boolean participantsUnordered) {
        return new FixtureObservation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                canonicalFixtureId,
                "highlightly",
                "fixture-1",
                "competition-1",
                "home-1",
                "away-1",
                "cal01-fixture-v3",
                " 2026/2027 ",
                neutralVenue,
                participantsUnordered,
                Instant.parse("2026-09-05T12:00:00Z"),
                "SCHEDULED",
                "REGULAR_SEASON",
                status,
                reasonCode,
                OBSERVED_AT,
                CREATED_AT);
    }
}
