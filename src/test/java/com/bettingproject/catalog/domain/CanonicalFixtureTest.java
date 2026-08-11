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
        CanonicalFixture fixture = new CanonicalFixture(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-08-15T12:00:00Z"),
                FixtureStatus.SCHEDULED, "REGULAR_SEASON", createdAt, createdAt);

        CanonicalFixture revised = fixture.revise(
                Instant.parse("2026-08-16T12:00:00Z"),
                FixtureStatus.POSTPONED,
                "REGULAR_SEASON",
                Instant.parse("2026-08-12T10:00:00Z"));

        assertThat(revised.id()).isEqualTo(fixture.id());
        assertThat(revised.createdAt()).isEqualTo(createdAt);
        assertThat(revised.kickoff()).hasToString("2026-08-16T12:00:00Z");
        assertThat(revised.status()).isEqualTo(FixtureStatus.POSTPONED);
    }
}
