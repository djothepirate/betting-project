package com.bettingproject.collection.application.capability;

import java.util.ArrayList;
import java.util.List;

import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.CapabilityStatus;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.bettingproject.collection.application.capability.CapabilityTestSamples.*;
import static org.assertj.core.api.Assertions.*;

class ProviderRoutingServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"PPL", "PD", "DED", "ELC"})
    void routesEachCoreCompetitionExactly(String code) {
        var result = new ProviderRoutingService(registry()).route(route(code));
        assertThat(result.status()).isEqualTo(ProviderRoutingResult.Status.READY);
        assertThat(result.primary().orElseThrow().key()).isEqualTo(key("synthetic-primary", code));
        assertThat(result.controls()).extracting(c -> c.key().provider()).containsExactly("synthetic-control");
        assertThat(result.documentSha256()).isEqualTo(registry().documentSha256());
        assertThat(result.registryVersion()).isEqualTo("synthetic-mvp-001-v1");
        if (code.equals("PPL")) {
            assertThat(result.exclusions()).extracting(ProviderRoutingResult.Exclusion::reason)
                    .containsExactly(ProviderRoutingResult.ExclusionReason.INACTIVE);
        }
    }

    @Test
    void neverPromotesControlAndOrdersControlsDeterministically() {
        var base = registry().find(key("synthetic-control", "PPL")).orElseThrow();
        var a = copy(base, "a-control", base.status(), base.authorityRole(), true);
        var z = copy(base, "z-control", base.status(), base.authorityRole(), true);
        var result = new ProviderRoutingService(configured(List.of(z, a))).route(route("PPL"));
        assertThat(result.status()).isEqualTo(ProviderRoutingResult.Status.NO_PRIMARY);
        assertThat(result.primary()).isEmpty();
        assertThat(result.controls()).extracting(c -> c.key().provider()).containsExactly("a-control", "z-control");
    }

    @Test
    void returnsAllExclusionsWithoutAssigningAuthority() {
        var base = registry().find(key("synthetic-primary", "PPL")).orElseThrow();
        var candidates = List.of(
                copy(base, "a", CapabilityStatus.PILOT, CapabilityAuthorityRole.UNASSIGNED, true),
                copy(base, "b", CapabilityStatus.NON_APPLICABLE, CapabilityAuthorityRole.UNASSIGNED, true),
                copy(base, "c", CapabilityStatus.BLOCKED_BY_PLAN, CapabilityAuthorityRole.UNASSIGNED, true),
                copy(base, "d", CapabilityStatus.CALENDAR_ONLY, CapabilityAuthorityRole.UNASSIGNED, true));
        var result = new ProviderRoutingService(configured(candidates)).route(route("PPL"));
        assertThat(result.primary()).isEmpty();
        assertThat(result.controls()).isEmpty();
        assertThat(result.exclusions()).extracting(ProviderRoutingResult.Exclusion::reason).containsExactly(
                ProviderRoutingResult.ExclusionReason.PILOT, ProviderRoutingResult.ExclusionReason.NON_APPLICABLE,
                ProviderRoutingResult.ExclusionReason.BLOCKED_BY_PLAN,
                ProviderRoutingResult.ExclusionReason.UNASSIGNED_AUTHORITY);
    }

    @Test
    void rejectsDuplicateProviderKeysAndMultipleOperationalPrimaries() {
        var base = registry().find(key("synthetic-primary", "PPL")).orElseThrow();
        assertThatIllegalArgumentException().isThrownBy(() -> configured(List.of(base, base)));
        var other = copy(base, "other-primary", base.status(), base.authorityRole(), true);
        assertThatIllegalArgumentException().isThrownBy(() -> configured(List.of(base, other)));
        assertThat(configured(List.of(base, copy(other, "other-primary", other.status(),
                other.authorityRole(), false))).candidates(route("PPL"))).hasSize(2);
    }

    @Test
    void missingOrDifferentContextAndLiteralWildcardsDoNotMatch() {
        var registry = registry();
        for (String marker : List.of("*", "?", "%")) {
            assertThat(registry.find(key("synthetic-primary" + marker, "PPL"))).isEmpty();
            assertThat(registry.candidates(route("PPL" + marker))).isEmpty();
        }
        assertThat(registry.candidates(route("ppl"))).isEmpty();
        assertThat(registry.candidates(new CapabilityRouteKey("PPL", "2027/2028", "REGULAR_SEASON",
                CapabilityDataType.CALENDAR))).isEmpty();
        assertThat(registry.candidates(new CapabilityRouteKey("PPL", "2026/2027", "FINAL",
                CapabilityDataType.CALENDAR))).isEmpty();
        assertThat(new ProviderRoutingService(registry).route(route("UNKNOWN")).status())
                .isEqualTo(ProviderRoutingResult.Status.NO_PRIMARY);
    }

    @Test
    void registryAndEvidenceAreDefensivelyCopied() {
        var base = registry().find(key("synthetic-primary", "PPL")).orElseThrow();
        var evidence = new ArrayList<>(base.evidence());
        var copied = new ProviderCapability(base.key(), base.route(), base.status(), base.authorityRole(), true, evidence);
        var entries = new ArrayList<>(List.of(copied));
        var registry = configured(entries);
        evidence.clear();
        entries.clear();
        assertThat(registry.candidates(route("PPL"))).hasSize(1);
        assertThat(copied.evidence()).hasSize(1);
        assertThatThrownBy(() -> registry.candidates(route("PPL")).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static ProviderCapabilityRegistry configured(List<ProviderCapability> entries) {
        return new ConfiguredProviderCapabilityRegistry("test-v1", "a".repeat(64), entries);
    }

    private static ProviderCapability copy(ProviderCapability base, String provider, CapabilityStatus status,
            CapabilityAuthorityRole role, boolean enabled) {
        var k = base.key();
        return new ProviderCapability(new ProviderCapabilityKey(provider, k.providerCompetitionId(),
                k.sourceSeason(), k.sourcePhase(), k.dataType()), base.route(), status, role, enabled, base.evidence());
    }
}
