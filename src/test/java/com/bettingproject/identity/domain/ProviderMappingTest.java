package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderMappingTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-11T11:00:00Z");

    @Test
    void confirmedMappingRequiresCanonicalIdentity() {
        assertThatThrownBy(() -> new ProviderMapping(
                UUID.randomUUID(), "highlightly", ProviderEntityType.TEAM, "hly-hirnyk",
                null, "", "", 1.0, MappingStatus.CONFIRMED, NOW, NOW, 1))
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
        assertThat(mapping.version()).isEqualTo(1);

        assertThatThrownBy(() -> new ProviderMapping(
                UUID.randomUUID(), "highlightly", ProviderEntityType.TEAM, "hly-hirnyk",
                UUID.randomUUID(), "", "", 0.45, MappingStatus.AMBIGUOUS, NOW, NOW, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not carry");
    }

    @Test
    void storedMappingRequiresAPositiveVersion() {
        assertThatThrownBy(() -> new ProviderMapping(
                UUID.randomUUID(), "highlightly", ProviderEntityType.TEAM, "hly-hirnyk",
                null, "", "", null, MappingStatus.REJECTED, NOW, NOW, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must be at least 1");
    }

    @Test
    void mappingKeyNormalizesContextWithoutChangingItsExactIdentity() {
        ProviderMappingKey key = new ProviderMappingKey(
                " highlightly ", ProviderEntityType.TEAM, " hly-hirnyk ", null, " GROUP_A ");

        assertThat(key.provider()).isEqualTo("highlightly");
        assertThat(key.providerEntityId()).isEqualTo("hly-hirnyk");
        assertThat(key.season()).isEmpty();
        assertThat(key.phase()).isEqualTo("GROUP_A");
    }

    @Test
    void confirmationPreservesIdentityAndAdvancesVersion() {
        ProviderMapping initial = ProviderMapping.ambiguous(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "2026", "", 0.45, NOW);
        UUID canonicalEntityId = UUID.randomUUID();

        ProviderMapping confirmed = initial.confirm(canonicalEntityId, LATER);

        assertThat(confirmed.id()).isEqualTo(initial.id());
        assertThat(confirmed.key()).isEqualTo(initial.key());
        assertThat(confirmed.createdAt()).isEqualTo(initial.createdAt());
        assertThat(confirmed.updatedAt()).isEqualTo(LATER);
        assertThat(confirmed.version()).isEqualTo(2);
        assertThat(confirmed.status()).isEqualTo(MappingStatus.CONFIRMED);
        assertThat(confirmed.canonicalEntityId()).isEqualTo(canonicalEntityId);
        assertThat(confirmed.confidence()).isEqualTo(1.0);
    }

    @Test
    void rejectionPreservesIdentityAndAdvancesVersionWithoutTargetOrConfidence() {
        ProviderMapping initial = ProviderMapping.confirmed(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", UUID.randomUUID(),
                "2026", "", NOW);

        ProviderMapping rejected = initial.reject(LATER);

        assertThat(rejected.id()).isEqualTo(initial.id());
        assertThat(rejected.key()).isEqualTo(initial.key());
        assertThat(rejected.createdAt()).isEqualTo(initial.createdAt());
        assertThat(rejected.updatedAt()).isEqualTo(LATER);
        assertThat(rejected.version()).isEqualTo(2);
        assertThat(rejected.status()).isEqualTo(MappingStatus.REJECTED);
        assertThat(rejected.canonicalEntityId()).isNull();
        assertThat(rejected.confidence()).isNull();
    }

    @Test
    void rejectedFactoryCreatesVersionOneWithoutTargetOrConfidence() {
        ProviderMapping rejected = ProviderMapping.rejected(
                "highlightly", ProviderEntityType.TEAM, "hly-hirnyk", "2026", "", NOW);

        assertThat(rejected.version()).isEqualTo(1);
        assertThat(rejected.status()).isEqualTo(MappingStatus.REJECTED);
        assertThat(rejected.canonicalEntityId()).isNull();
        assertThat(rejected.confidence()).isNull();
    }
}
