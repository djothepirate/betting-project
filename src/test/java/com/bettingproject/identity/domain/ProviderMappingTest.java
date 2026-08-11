package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderMappingTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    @Test
    void confirmedMappingRequiresCanonicalIdentity() {
        assertThatThrownBy(() -> new ProviderMapping(
                UUID.randomUUID(), "highlightly", ProviderEntityType.TEAM, "hly-hirnyk",
                null, "", "", 1.0, MappingStatus.CONFIRMED, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a canonical entity");
    }

    @Test
    void ambiguousMappingCannotSilentlyCarryCanonicalIdentity() {
        ProviderMapping mapping = ProviderMapping.ambiguous(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "", "", 0.45, NOW);

        assertThat(mapping.status()).isEqualTo(MappingStatus.AMBIGUOUS);
        assertThat(mapping.canonicalEntityId()).isNull();
        assertThat(mapping.confidence()).isEqualTo(0.45);

        assertThatThrownBy(() -> new ProviderMapping(
                UUID.randomUUID(), "highlightly", ProviderEntityType.TEAM, "hly-hirnyk",
                UUID.randomUUID(), "", "", 0.45, MappingStatus.AMBIGUOUS, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not carry");
    }
}
