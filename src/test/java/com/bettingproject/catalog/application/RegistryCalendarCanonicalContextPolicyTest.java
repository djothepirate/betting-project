package com.bettingproject.catalog.application;

import com.bettingproject.catalog.adapter.configuration.CalendarCanonicalContextConfiguration;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityEvidenceReference;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.CapabilityStatus;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryCalendarCanonicalContextPolicyTest {
    private static final CalendarAuthorityKey HIGHLIGHTLY = new CalendarAuthorityKey(
            "highlightly", "920001", "2030", "Regular Season - 1", CalendarAuthorityDataType.CALENDAR);
    private static final CalendarAuthorityKey FOOTBALL_DATA = new CalendarAuthorityKey(
            "football-data.org", "SYN", "950001", "REGULAR_SEASON", CalendarAuthorityDataType.CALENDAR);
    private static final CapabilityRouteKey ROUTE = new CapabilityRouteKey(
            "SYN", "2030", "REGULAR_SEASON", CapabilityDataType.CALENDAR);

    @Test
    void exactExplicitRoutesJoinDistinctSourceVocabulariesWithoutRewritingTheirKeys() {
        var policy = policy(List.of(
                capability(HIGHLIGHTLY, CapabilityStatus.PRIMARY, CapabilityAuthorityRole.PRIMARY, true),
                capability(FOOTBALL_DATA, CapabilityStatus.CONTROL, CapabilityAuthorityRole.CONTROL, true)));
        var expected = new CalendarCanonicalContext("2030", "REGULAR_SEASON");
        assertThat(policy.resolve(HIGHLIGHTLY)).contains(expected);
        assertThat(policy.resolve(FOOTBALL_DATA)).contains(expected);
        assertThat(HIGHLIGHTLY.season()).isEqualTo("2030");
        assertThat(HIGHLIGHTLY.phase()).isEqualTo("Regular Season - 1");
        assertThat(FOOTBALL_DATA.season()).isEqualTo("950001");
        assertThat(FOOTBALL_DATA.phase()).isEqualTo("REGULAR_SEASON");
    }

    @Test
    void absentInactivePilotOrUnassignedEntriesDoNotSupplyACanonicalSubstitution() {
        assertThat(policy(List.of()).resolve(HIGHLIGHTLY)).isEmpty();
        assertThat(policy(List.of(capability(HIGHLIGHTLY, CapabilityStatus.PRIMARY,
                CapabilityAuthorityRole.PRIMARY, false))).resolve(HIGHLIGHTLY)).isEmpty();
        assertThat(policy(List.of(capability(HIGHLIGHTLY, CapabilityStatus.PILOT,
                CapabilityAuthorityRole.UNASSIGNED, true))).resolve(HIGHLIGHTLY)).isEmpty();
        assertThat(policy(List.of(capability(HIGHLIGHTLY, CapabilityStatus.CALENDAR_ONLY,
                CapabilityAuthorityRole.UNASSIGNED, true))).resolve(HIGHLIGHTLY)).isEmpty();
    }

    @Test
    void calendarOnlyWithExplicitAuthorityUsesOnlyItsExactRoute() {
        var policy = policy(List.of(capability(HIGHLIGHTLY, CapabilityStatus.CALENDAR_ONLY,
                CapabilityAuthorityRole.CONTROL, true)));
        assertThat(policy.resolve(HIGHLIGHTLY)).contains(new CalendarCanonicalContext("2030", "REGULAR_SEASON"));
        assertThat(policy.resolve(FOOTBALL_DATA)).isEmpty();
        assertThat(policy.resolve(new CalendarAuthorityKey(HIGHLIGHTLY.provider(), HIGHLIGHTLY.providerCompetitionId(),
                "2031", HIGHLIGHTLY.phase(), CalendarAuthorityDataType.CALENDAR))).isEmpty();
        assertThat(policy.resolve(new CalendarAuthorityKey(HIGHLIGHTLY.provider(), HIGHLIGHTLY.providerCompetitionId(),
                HIGHLIGHTLY.season(), "Regular Season - 2", CalendarAuthorityDataType.CALENDAR))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "?", "%"})
    void literalRuntimeCharactersNeverSearchForANeighboringAssignment(String literal) {
        var policy = policy(List.of(capability(HIGHLIGHTLY, CapabilityStatus.PRIMARY,
                CapabilityAuthorityRole.PRIMARY, true)));
        assertThat(policy.resolve(new CalendarAuthorityKey(HIGHLIGHTLY.provider() + literal,
                HIGHLIGHTLY.providerCompetitionId(), HIGHLIGHTLY.season(), HIGHLIGHTLY.phase(),
                CalendarAuthorityDataType.CALENDAR))).isEmpty();
        assertThat(policy.resolve(new CalendarAuthorityKey(HIGHLIGHTLY.provider(),
                HIGHLIGHTLY.providerCompetitionId() + literal, HIGHLIGHTLY.season(), HIGHLIGHTLY.phase(),
                CalendarAuthorityDataType.CALENDAR))).isEmpty();
        assertThat(policy.resolve(new CalendarAuthorityKey(HIGHLIGHTLY.provider(),
                HIGHLIGHTLY.providerCompetitionId(), HIGHLIGHTLY.season() + literal, HIGHLIGHTLY.phase(),
                CalendarAuthorityDataType.CALENDAR))).isEmpty();
        assertThat(policy.resolve(new CalendarAuthorityKey(HIGHLIGHTLY.provider(),
                HIGHLIGHTLY.providerCompetitionId(), HIGHLIGHTLY.season(), HIGHLIGHTLY.phase() + literal,
                CalendarAuthorityDataType.CALENDAR))).isEmpty();
    }

    @Test
    void outOfConfigurationBoundsIsAbsentRatherThanAnExceptionOrFallback() {
        var policy = policy(List.of());
        assertThat(policy.resolve(new CalendarAuthorityKey("x".repeat(65), "920001", "2030",
                "Regular Season - 1", CalendarAuthorityDataType.CALENDAR))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"control-api", "batch-worker", "replay"})
    void contextPolicyIsConfinedToDatabaseProfiles(String profile) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.getEnvironment().setActiveProfiles(profile);
            context.registerBean(ProviderCapabilityRegistry.class, () -> registry(List.of()));
            context.register(CalendarCanonicalContextConfiguration.class);
            context.refresh();
            assertThat(context.getBeansOfType(CalendarCanonicalContextPolicy.class))
                    .hasSize(profile.equals("replay") ? 0 : 1);
        }
    }

    @Test
    void canonicalContextRemainsExactAndCannotInventAnEmptyValue() {
        assertThatThrownBy(() -> new CalendarCanonicalContext("", "REGULAR_SEASON"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarCanonicalContext("2030", " REGULAR_SEASON"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CalendarCanonicalContext("2030*", "phase?"))
                .isEqualTo(new CalendarCanonicalContext("2030*", "phase?"));
    }

    private static RegistryCalendarCanonicalContextPolicy policy(List<ProviderCapability> entries) {
        return new RegistryCalendarCanonicalContextPolicy(registry(entries));
    }

    private static ProviderCapabilityRegistry registry(List<ProviderCapability> entries) {
        return new ProviderCapabilityRegistry() {
            @Override public String registryVersion() { return "synthetic-context-v1"; }
            @Override public String documentSha256() { return "a".repeat(64); }
            @Override public Optional<ProviderCapability> find(ProviderCapabilityKey key) {
                return entries.stream().filter(capability -> capability.key().equals(key)).findFirst();
            }
            @Override public List<ProviderCapability> candidates(CapabilityRouteKey route) {
                throw new AssertionError("No route or neighboring lookup may replace exact source lookup");
            }
        };
    }

    private static ProviderCapability capability(CalendarAuthorityKey key, CapabilityStatus status,
            CapabilityAuthorityRole role, boolean active) {
        return new ProviderCapability(new ProviderCapabilityKey(key.provider(), key.providerCompetitionId(),
                key.season(), key.phase(), CapabilityDataType.CALENDAR), ROUTE, status, role, active,
                List.of(new CapabilityEvidenceReference("synthetic-context", java.time.Instant.parse("2030-01-01T00:00:00Z"), "a".repeat(64))));
    }
}
