package com.bettingproject.catalog.application;

import java.util.List;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import org.junit.jupiter.api.Test;

import static com.bettingproject.collection.application.capability.CapabilityTestSamples.*;
import static org.assertj.core.api.Assertions.*;

class RegistryCalendarAuthorityPolicyTest {
    @Test
    void resolvesOnlyActiveExactCalendarKeysAndReturnsTheDocumentHash() {
        var registry = registry();
        var policy = new RegistryCalendarAuthorityPolicy(registry);
        for (String code : List.of("PPL", "PD", "DED", "ELC")) {
            for (String provider : List.of("synthetic-primary", "synthetic-control", "synthetic-inactive", "unknown")) {
                var key = key(provider, code);
                var result = policy.resolve(new CalendarAuthorityKey(key.provider(), key.providerCompetitionId(),
                        key.sourceSeason(), key.sourcePhase(), CalendarAuthorityDataType.CALENDAR));
                assertThat(result.role()).isEqualTo(switch (provider) {
                    case "synthetic-primary" -> CalendarAuthorityRole.PRIMARY;
                    case "synthetic-control" -> CalendarAuthorityRole.CONTROL;
                    default -> CalendarAuthorityRole.UNASSIGNED;
                });
                assertThat(result.policyVersion()).isEqualTo(registry.documentSha256());
            }
        }
    }

    @Test
    void runtimeKeysOutsideConfigurationBoundsRemainUnassigned() {
        var policy = new RegistryCalendarAuthorityPolicy(registry());
        var result = policy.resolve(new CalendarAuthorityKey("x".repeat(65), "synthetic-ppl", "2026/2027",
                "REGULAR_SEASON", CalendarAuthorityDataType.CALENDAR));
        assertThat(result.role()).isEqualTo(CalendarAuthorityRole.UNASSIGNED);
    }
}
