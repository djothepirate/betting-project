package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalFixtureTest {

    @Test
    void homeAndAwayTeamsMustBeDifferent() {
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-11T10:00:00Z");

        assertThatThrownBy(() -> new CanonicalFixture(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), teamId, teamId,
                now, FixtureStatus.SCHEDULED, "REGULAR_SEASON", now, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");
    }

    @Test
    void revisionKeepsCanonicalIdentityAndCreationTime() {
        Instant createdAt = Instant.parse("2026-08-11T10:00:00Z");
        FixtureAuthorityStamp authority = new FixtureAuthorityStamp(
                UUID.randomUUID(),
                Instant.parse("2026-08-11T09:55:00Z"),
                "highlightly",
                "calendar-authority-test-1");
        CanonicalFixture fixture = new CanonicalFixture(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                true, true,
                Instant.parse("2026-08-15T12:00:00Z"),
                FixtureStatus.SCHEDULED, "REGULAR_SEASON", authority, createdAt, createdAt);

        CanonicalFixture revised = fixture.revise(
                Instant.parse("2026-08-16T12:00:00Z"),
                FixtureStatus.POSTPONED,
                "REGULAR_SEASON",
                Instant.parse("2026-08-12T10:00:00Z"));

        assertThat(revised.id()).isEqualTo(fixture.id());
        assertThat(revised.createdAt()).isEqualTo(createdAt);
        assertThat(revised.kickoff()).hasToString("2026-08-16T12:00:00Z");
        assertThat(revised.status()).isEqualTo(FixtureStatus.POSTPONED);
        assertThat(revised.neutralVenue()).isTrue();
        assertThat(revised.participantsUnordered()).isTrue();
        assertThat(revised.lastAuthority()).isEqualTo(authority);
    }

    @Test
    void neutralVenueAndParticipantOrderRemainIndependent() {
        Instant now = Instant.parse("2026-08-11T10:00:00Z");
        CanonicalFixture fixture = new CanonicalFixture(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                true, false,
                Instant.parse("2026-08-15T12:00:00Z"),
                FixtureStatus.SCHEDULED, "FINAL", null, now, now);

        assertThat(fixture.neutralVenue()).isTrue();
        assertThat(fixture.participantsUnordered()).isFalse();
        assertThat(fixture.lastAuthority()).isNull();

        CanonicalFixture revised = fixture.revise(
                fixture.kickoff(),
                fixture.status(),
                fixture.phase(),
                null,
                true,
                Instant.parse("2026-08-11T11:00:00Z"));

        assertThat(revised.neutralVenue()).isNull();
        assertThat(revised.participantsUnordered()).isTrue();
    }

    @Test
    void applyingPrimaryFactsUpdatesFactsAndAuthorityWithoutChangingIdentity() {
        Instant createdAt = Instant.parse("2026-08-11T10:00:00Z");
        CanonicalFixture fixture = new CanonicalFixture(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                null, false,
                Instant.parse("2026-08-15T12:00:00Z"),
                FixtureStatus.SCHEDULED, "REGULAR_SEASON", null, createdAt, createdAt);
        FixtureCanonicalFacts revisedFacts = new FixtureCanonicalFacts(
                fixture.competitionId(),
                fixture.seasonId(),
                fixture.homeTeamId(),
                fixture.awayTeamId(),
                true,
                true,
                Instant.parse("2026-08-16T12:00:00Z"),
                FixtureStatus.POSTPONED,
                "REGULAR_SEASON");
        FixtureAuthorityStamp authority = new FixtureAuthorityStamp(
                UUID.randomUUID(),
                Instant.parse("2026-08-12T09:55:00Z"),
                "synthetic-primary",
                "synthetic-policy-v1");
        Instant appliedAt = Instant.parse("2026-08-12T10:00:00Z");

        CanonicalFixture revised = fixture.apply(revisedFacts, authority, appliedAt);

        assertThat(revised.id()).isEqualTo(fixture.id());
        assertThat(revised.competitionId()).isEqualTo(fixture.competitionId());
        assertThat(revised.seasonId()).isEqualTo(fixture.seasonId());
        assertThat(revised.homeTeamId()).isEqualTo(fixture.homeTeamId());
        assertThat(revised.awayTeamId()).isEqualTo(fixture.awayTeamId());
        assertThat(revised.createdAt()).isEqualTo(createdAt);
        assertThat(revised.updatedAt()).isEqualTo(appliedAt);
        assertThat(FixtureCanonicalFacts.from(revised)).isEqualTo(revisedFacts);
        assertThat(revised.lastAuthority()).isEqualTo(authority);
    }

    @Test
    void advancingAuthorityPreservesEveryCanonicalFact() {
        Instant createdAt = Instant.parse("2026-08-11T10:00:00Z");
        FixtureAuthorityStamp previousAuthority = new FixtureAuthorityStamp(
                UUID.randomUUID(),
                Instant.parse("2026-08-11T09:55:00Z"),
                "synthetic-primary",
                "synthetic-policy-v1");
        CanonicalFixture fixture = new CanonicalFixture(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                true, false,
                Instant.parse("2026-08-15T12:00:00Z"),
                FixtureStatus.SCHEDULED, "FINAL", previousAuthority, createdAt, createdAt);
        FixtureCanonicalFacts factsBeforeAdvance = FixtureCanonicalFacts.from(fixture);
        FixtureAuthorityStamp laterAuthority = new FixtureAuthorityStamp(
                UUID.randomUUID(),
                Instant.parse("2026-08-12T09:55:00Z"),
                "synthetic-primary",
                "synthetic-policy-v2");
        Instant evaluatedAt = Instant.parse("2026-08-12T10:00:00Z");

        CanonicalFixture advanced = fixture.advanceAuthority(laterAuthority, evaluatedAt);

        assertThat(advanced.id()).isEqualTo(fixture.id());
        assertThat(advanced.createdAt()).isEqualTo(createdAt);
        assertThat(advanced.updatedAt()).isEqualTo(evaluatedAt);
        assertThat(FixtureCanonicalFacts.from(advanced)).isEqualTo(factsBeforeAdvance);
        assertThat(advanced.lastAuthority()).isEqualTo(laterAuthority);
    }

    @Test
    void applyingFactsCannotChangeCanonicalIdentity() {
        Instant now = Instant.parse("2026-08-11T10:00:00Z");
        CanonicalFixture fixture = new CanonicalFixture(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                now, FixtureStatus.SCHEDULED, "REGULAR_SEASON", now, now);
        FixtureCanonicalFacts conflictingFacts = new FixtureCanonicalFacts(
                fixture.competitionId(),
                fixture.seasonId(),
                fixture.awayTeamId(),
                fixture.homeTeamId(),
                null,
                false,
                fixture.kickoff(),
                fixture.status(),
                fixture.phase());
        FixtureAuthorityStamp authority = new FixtureAuthorityStamp(
                UUID.randomUUID(), now, "synthetic-primary", "synthetic-policy-v1");

        assertThatThrownBy(() -> fixture.apply(conflictingFacts, authority, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity");
    }
}
