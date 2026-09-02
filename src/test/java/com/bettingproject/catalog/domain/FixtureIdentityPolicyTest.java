package com.bettingproject.catalog.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureIdentityPolicyTest {

    private final FixtureIdentityPolicy policy = new FixtureIdentityPolicy();
    private final UUID homeTeamId = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private final UUID awayTeamId = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    void exactOrderMatchesAndPreservesTheSourceOrder() {
        FixtureIdentityMatch match = policy.match(
                homeTeamId,
                awayTeamId,
                homeTeamId,
                awayTeamId,
                null,
                false);

        assertThat(match).isEqualTo(FixtureIdentityMatch.EXACT_ORDER);
        assertThat(match.matches()).isTrue();
        assertThat(match.sourceOrderInverted()).isFalse();
    }

    @Test
    void invertedOrderMatchesOnlyWhenTheSourceExplicitlyDeclaresParticipantsUnordered() {
        FixtureIdentityMatch match = policy.match(
                homeTeamId,
                awayTeamId,
                awayTeamId,
                homeTeamId,
                null,
                true);

        assertThat(match).isEqualTo(FixtureIdentityMatch.INVERTED_ORDER_ALLOWED);
        assertThat(match.matches()).isTrue();
        assertThat(match.sourceOrderInverted()).isTrue();
    }

    @Test
    void invertedOrderDoesNotMatchWhenTheSourceKeepsParticipantsOrdered() {
        FixtureIdentityMatch match = policy.match(
                homeTeamId,
                awayTeamId,
                awayTeamId,
                homeTeamId,
                null,
                false);

        assertThat(match).isEqualTo(FixtureIdentityMatch.NO_MATCH);
        assertThat(match.matches()).isFalse();
        assertThat(match.sourceOrderInverted()).isFalse();
    }

    @Test
    void neutralVenueNeverMakesAnOrderedInversionMatch() {
        FixtureIdentityMatch neutral = policy.match(
                homeTeamId,
                awayTeamId,
                awayTeamId,
                homeTeamId,
                true,
                false);
        FixtureIdentityMatch nonNeutral = policy.match(
                homeTeamId,
                awayTeamId,
                awayTeamId,
                homeTeamId,
                false,
                false);

        assertThat(neutral).isEqualTo(FixtureIdentityMatch.NO_MATCH);
        assertThat(nonNeutral).isEqualTo(FixtureIdentityMatch.NO_MATCH);
    }

    @Test
    void rejectsNonDistinctCanonicalOrSourceParticipants() {
        assertThatThrownBy(() -> policy.match(
                homeTeamId,
                homeTeamId,
                homeTeamId,
                awayTeamId,
                null,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical participants must be different");

        assertThatThrownBy(() -> policy.match(
                homeTeamId,
                awayTeamId,
                awayTeamId,
                awayTeamId,
                null,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source participants must be different");
    }
}
