package com.bettingproject.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureAuthorityStampTest {

    @Test
    void trimsProviderAndPolicyVersion() {
        FixtureAuthorityStamp stamp = new FixtureAuthorityStamp(
                UUID.randomUUID(),
                Instant.parse("2026-09-01T12:00:00Z"),
                " highlightly ",
                " calendar-policy-1 ");

        assertThat(stamp.provider()).isEqualTo("highlightly");
        assertThat(stamp.policyVersion()).isEqualTo("calendar-policy-1");
    }

    @Test
    void rejectsIncompleteAuthorityContext() {
        assertThatThrownBy(() -> new FixtureAuthorityStamp(
                UUID.randomUUID(),
                Instant.parse("2026-09-01T12:00:00Z"),
                "highlightly",
                " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyVersion");
    }
}
