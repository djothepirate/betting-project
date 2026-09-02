package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FixtureChronologyPolicyTest {

    private static final Instant LAST_AUTHORITY_TIME = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant OLDER_TIME = Instant.parse("2026-09-01T11:59:59Z");
    private static final Instant NEWER_TIME = Instant.parse("2026-09-01T12:00:01Z");

    private static final UUID COMPETITION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_COMPETITION_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SEASON_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_SEASON_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID HOME_TEAM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID AWAY_TEAM_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_TEAM_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final Instant KICKOFF = Instant.parse("2026-09-05T19:00:00Z");

    private final FixtureChronologyPolicy policy = new FixtureChronologyPolicy();

    @Test
    void olderObservationIsStaleEvenWhenFactsAreIdentical() {
        FixtureCanonicalFacts current = facts(FixtureStatus.SCHEDULED);

        assertThat(policy.evaluate(LAST_AUTHORITY_TIME, current, OLDER_TIME, current))
                .isEqualTo(FixtureApplicationOutcome.STALE);
    }

    @Test
    void olderObservationIsStaleBeforeAnOtherwiseInvalidTerminalRegressionIsEvaluated() {
        FixtureCanonicalFacts current = facts(FixtureStatus.FINISHED);
        FixtureCanonicalFacts candidate = facts(FixtureStatus.SCHEDULED);

        assertThat(policy.evaluate(LAST_AUTHORITY_TIME, current, OLDER_TIME, candidate))
                .isEqualTo(FixtureApplicationOutcome.STALE);
    }

    @Test
    void equalAuthorityTimeWithIdenticalFactsIsUnchanged() {
        FixtureCanonicalFacts current = facts(FixtureStatus.SCHEDULED);

        assertThat(policy.evaluate(LAST_AUTHORITY_TIME, current, LAST_AUTHORITY_TIME, current))
                .isEqualTo(FixtureApplicationOutcome.UNCHANGED);
    }

    @ParameterizedTest(name = "equal authority time conflicts when {0} differs")
    @MethodSource("eachConflictingCanonicalFact")
    void equalAuthorityTimeWithAnyContradictoryCanonicalFactIsAConflict(
            String changedFact,
            FixtureCanonicalFacts candidate) {
        FixtureCanonicalFacts current = facts(FixtureStatus.SCHEDULED);

        assertThat(policy.evaluate(LAST_AUTHORITY_TIME, current, LAST_AUTHORITY_TIME, candidate))
                .as(changedFact)
                .isEqualTo(FixtureApplicationOutcome.EQUAL_AUTHORITY_TIME_CONFLICT);
    }

    @Test
    void equalAuthorityTimeConflictTakesPrecedenceOverTerminalTransitionValidation() {
        FixtureCanonicalFacts current = facts(FixtureStatus.CANCELLED);
        FixtureCanonicalFacts candidate = facts(FixtureStatus.FINISHED);

        assertThat(policy.evaluate(LAST_AUTHORITY_TIME, current, LAST_AUTHORITY_TIME, candidate))
                .isEqualTo(FixtureApplicationOutcome.EQUAL_AUTHORITY_TIME_CONFLICT);
    }

    @Test
    void newerObservationWithIdenticalFactsIsUnchanged() {
        FixtureCanonicalFacts current = facts(FixtureStatus.POSTPONED);

        assertThat(policy.evaluate(LAST_AUTHORITY_TIME, current, NEWER_TIME, current))
                .isEqualTo(FixtureApplicationOutcome.UNCHANGED);
    }

    @Test
    void historicalFixtureWithoutAuthorityIsUnchangedWhenFactsAreIdentical() {
        FixtureCanonicalFacts current = facts(FixtureStatus.SCHEDULED);

        assertThat(policy.evaluate(null, current, NEWER_TIME, current))
                .isEqualTo(FixtureApplicationOutcome.UNCHANGED);
    }

    @Test
    void historicalFixtureWithoutAuthorityCanApplyDifferentFactsThroughAnAllowedTransition() {
        FixtureCanonicalFacts current = facts(FixtureStatus.POSTPONED);
        FixtureCanonicalFacts candidate = factsWithPhase(FixtureStatus.SCHEDULED, "RESCHEDULED");

        assertThat(policy.evaluate(null, current, NEWER_TIME, candidate))
                .isEqualTo(FixtureApplicationOutcome.UPDATED);
    }

    @Test
    void historicalFixtureWithoutAuthorityStillRejectsATerminalRegression() {
        FixtureCanonicalFacts current = facts(FixtureStatus.FINISHED);
        FixtureCanonicalFacts candidate = facts(FixtureStatus.SCHEDULED);

        assertThat(policy.evaluate(null, current, NEWER_TIME, candidate))
                .isEqualTo(FixtureApplicationOutcome.INVALID_TRANSITION);
    }

    @ParameterizedTest(name = "newer {0} -> {1} produces {2}")
    @MethodSource("completeStatusTransitionMatrix")
    void newerObservationUsesTheCompleteStatusTransitionMatrix(
            FixtureStatus currentStatus,
            FixtureStatus candidateStatus,
            FixtureApplicationOutcome expectedOutcome) {
        FixtureCanonicalFacts current = facts(currentStatus);
        FixtureCanonicalFacts candidate = factsWithPhase(candidateStatus, "UPDATED_PHASE");

        assertThat(policy.evaluate(LAST_AUTHORITY_TIME, current, NEWER_TIME, candidate))
                .isEqualTo(expectedOutcome);
    }

    @Test
    void factsCanBeProjectedFromACanonicalFixtureWithoutTimestampsOrAuthorityMetadata() {
        FixtureAuthorityStamp authority = new FixtureAuthorityStamp(
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                LAST_AUTHORITY_TIME,
                "highlightly",
                "calendar-policy-1");
        CanonicalFixture fixture = new CanonicalFixture(
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                COMPETITION_ID,
                SEASON_ID,
                HOME_TEAM_ID,
                AWAY_TEAM_ID,
                true,
                false,
                KICKOFF,
                FixtureStatus.SCHEDULED,
                " REGULAR_SEASON ",
                authority,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"));

        assertThat(FixtureCanonicalFacts.from(fixture))
                .isEqualTo(new FixtureCanonicalFacts(
                        COMPETITION_ID,
                        SEASON_ID,
                        HOME_TEAM_ID,
                        AWAY_TEAM_ID,
                        true,
                        false,
                        KICKOFF,
                        FixtureStatus.SCHEDULED,
                        "REGULAR_SEASON"));
    }

    @Test
    void factsAndCandidateObservationTimeAreMandatory() {
        FixtureCanonicalFacts current = facts(FixtureStatus.SCHEDULED);

        assertThatNullPointerException()
                .isThrownBy(() -> policy.evaluate(LAST_AUTHORITY_TIME, null, NEWER_TIME, current))
                .withMessage("currentFacts");
        assertThatNullPointerException()
                .isThrownBy(() -> policy.evaluate(LAST_AUTHORITY_TIME, current, null, current))
                .withMessage("candidateObservedAt");
        assertThatNullPointerException()
                .isThrownBy(() -> policy.evaluate(LAST_AUTHORITY_TIME, current, NEWER_TIME, null))
                .withMessage("candidateFacts");
    }

    private static Stream<Arguments> eachConflictingCanonicalFact() {
        return Stream.of(
                Arguments.of("competition", new FixtureCanonicalFacts(
                        OTHER_COMPETITION_ID, SEASON_ID, HOME_TEAM_ID, AWAY_TEAM_ID,
                        null, false, KICKOFF, FixtureStatus.SCHEDULED, "REGULAR_SEASON")),
                Arguments.of("season", new FixtureCanonicalFacts(
                        COMPETITION_ID, OTHER_SEASON_ID, HOME_TEAM_ID, AWAY_TEAM_ID,
                        null, false, KICKOFF, FixtureStatus.SCHEDULED, "REGULAR_SEASON")),
                Arguments.of("home participant", new FixtureCanonicalFacts(
                        COMPETITION_ID, SEASON_ID, OTHER_TEAM_ID, AWAY_TEAM_ID,
                        null, false, KICKOFF, FixtureStatus.SCHEDULED, "REGULAR_SEASON")),
                Arguments.of("away participant", new FixtureCanonicalFacts(
                        COMPETITION_ID, SEASON_ID, HOME_TEAM_ID, OTHER_TEAM_ID,
                        null, false, KICKOFF, FixtureStatus.SCHEDULED, "REGULAR_SEASON")),
                Arguments.of("participant order", new FixtureCanonicalFacts(
                        COMPETITION_ID, SEASON_ID, AWAY_TEAM_ID, HOME_TEAM_ID,
                        null, false, KICKOFF, FixtureStatus.SCHEDULED, "REGULAR_SEASON")),
                Arguments.of("participantsUnordered", new FixtureCanonicalFacts(
                        COMPETITION_ID, SEASON_ID, HOME_TEAM_ID, AWAY_TEAM_ID,
                        null, true, KICKOFF, FixtureStatus.SCHEDULED, "REGULAR_SEASON")),
                Arguments.of("kickoff", new FixtureCanonicalFacts(
                        COMPETITION_ID, SEASON_ID, HOME_TEAM_ID, AWAY_TEAM_ID,
                        null, false, KICKOFF.plusSeconds(3600), FixtureStatus.SCHEDULED, "REGULAR_SEASON")),
                Arguments.of("phase", factsWithPhase(FixtureStatus.SCHEDULED, "PLAY_OFF")),
                Arguments.of("status", facts(FixtureStatus.POSTPONED)),
                Arguments.of("neutralVenue", new FixtureCanonicalFacts(
                        COMPETITION_ID, SEASON_ID, HOME_TEAM_ID, AWAY_TEAM_ID,
                        true, false, KICKOFF, FixtureStatus.SCHEDULED, "REGULAR_SEASON")));
    }

    private static Stream<Arguments> completeStatusTransitionMatrix() {
        return Stream.of(FixtureStatus.values())
                .flatMap(current -> Stream.of(FixtureStatus.values())
                        .map(candidate -> Arguments.of(
                                current,
                                candidate,
                                expectedNewerOutcome(current, candidate))));
    }

    private static FixtureApplicationOutcome expectedNewerOutcome(
            FixtureStatus current,
            FixtureStatus candidate) {
        if (current == candidate || current == FixtureStatus.SCHEDULED || current == FixtureStatus.POSTPONED) {
            return FixtureApplicationOutcome.UPDATED;
        }
        return FixtureApplicationOutcome.INVALID_TRANSITION;
    }

    private static FixtureCanonicalFacts facts(FixtureStatus status) {
        return factsWithPhase(status, "REGULAR_SEASON");
    }

    private static FixtureCanonicalFacts factsWithPhase(FixtureStatus status, String phase) {
        return new FixtureCanonicalFacts(
                COMPETITION_ID,
                SEASON_ID,
                HOME_TEAM_ID,
                AWAY_TEAM_ID,
                null,
                false,
                KICKOFF,
                status,
                phase);
    }
}
