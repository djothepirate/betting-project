package com.bettingproject.collection.domain.capability;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.bettingproject.collection.application.capability.CapabilityTestSamples.*;
import static org.assertj.core.api.Assertions.*;

class ProviderCapabilityTest {
    @ParameterizedTest
    @EnumSource(CapabilityStatus.class)
    void checksEveryStatusRoleCombination(CapabilityStatus status) {
        var base = registry().find(key("synthetic-primary", "PPL")).orElseThrow();
        for (CapabilityAuthorityRole role : CapabilityAuthorityRole.values()) {
            boolean valid = switch (status) {
                case PRIMARY -> role == CapabilityAuthorityRole.PRIMARY;
                case CONTROL -> role == CapabilityAuthorityRole.CONTROL;
                case CALENDAR_ONLY -> true;
                case PILOT, NON_APPLICABLE, BLOCKED_BY_PLAN -> role == CapabilityAuthorityRole.UNASSIGNED;
            };
            if (valid) {
                var entry = new ProviderCapability(base.key(), base.route(), status, role, true, base.evidence());
                assertThat(entry.operational()).isEqualTo(role != CapabilityAuthorityRole.UNASSIGNED);
                assertThat(new ProviderCapability(base.key(), base.route(), status, role, false, List.of()).operational())
                        .isFalse();
            }
            else {
                assertThatIllegalArgumentException().isThrownBy(() ->
                        new ProviderCapability(base.key(), base.route(), status, role, true, base.evidence()));
            }
        }
    }

    @Test
    void requiresEvidenceForEnabledEntriesAndChecksDataTypes() {
        var base = registry().find(key("synthetic-primary", "PPL")).orElseThrow();
        assertThatIllegalArgumentException().isThrownBy(() -> new ProviderCapability(
                base.key(), base.route(), base.status(), base.authorityRole(), true, List.of()));
        var key = new ProviderCapabilityKey("synthetic", "comp", "s", "p", CapabilityDataType.LINEUP);
        var route = new CapabilityRouteKey("PPL", "s", "p", CapabilityDataType.LINEUP);
        assertThatIllegalArgumentException().isThrownBy(() -> new ProviderCapability(
                key, route, CapabilityStatus.CALENDAR_ONLY, CapabilityAuthorityRole.PRIMARY, true, base.evidence()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProviderCapability(
                base.key(), route, base.status(), base.authorityRole(), true, base.evidence()));
    }

    @Test
    void rejectsInvalidTextsAndEvidenceWithoutInventingValues() {
        for (String value : List.of("", " ", " padded", "control\n", "x".repeat(201))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ProviderCapabilityKey(
                    "provider", value, "season", "phase", CapabilityDataType.CALENDAR));
        }
        assertThatNullPointerException().isThrownBy(() -> new CapabilityEvidenceReference("test", null, "a".repeat(64)));
        assertThatIllegalArgumentException().isThrownBy(() -> new CapabilityEvidenceReference("test", Instant.EPOCH, "bad"));
    }
}
