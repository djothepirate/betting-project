package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureApplicationLogTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void primaryApplicationCarriesItsPolicyContext() {
        FixtureApplicationLog log = new FixtureApplicationLog(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                FixtureApplicationOutcome.CREATED,
                null,
                CalendarAuthorityRole.PRIMARY,
                " calendar-policy-1 ",
                EVALUATED_AT);

        assertThat(log.authorityRole()).isEqualTo(CalendarAuthorityRole.PRIMARY);
        assertThat(log.policyVersion()).isEqualTo("calendar-policy-1");
    }

    @Test
    void authorityRoleAndPolicyVersionAreAnAllOrNothingPair() {
        assertThatThrownBy(() -> new FixtureApplicationLog(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                FixtureApplicationOutcome.UNCHANGED, null,
                CalendarAuthorityRole.CONTROL, null, EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both be present");
    }

    @Test
    void blockedApplicationRequiresAReasonCodeButNotACanonicalFixture() {
        FixtureApplicationLog log = new FixtureApplicationLog(
                UUID.randomUUID(), UUID.randomUUID(), null, null,
                FixtureApplicationOutcome.BLOCKED, " MISSING_MAPPING ",
                null, null, EVALUATED_AT);

        assertThat(log.canonicalFixtureId()).isNull();
        assertThat(log.reasonCode()).isEqualTo("MISSING_MAPPING");
    }

    @Test
    void staleApplicationRequiresAReasonCode() {
        assertThatThrownBy(() -> new FixtureApplicationLog(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                FixtureApplicationOutcome.STALE, null,
                null, null, EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCode");
    }

    @Test
    void successfulApplicationRejectsAReasonCode() {
        assertThatThrownBy(() -> new FixtureApplicationLog(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                FixtureApplicationOutcome.UPDATED, "SHOULD_NOT_BE_PRESENT",
                null, null, EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be absent");
    }
}
