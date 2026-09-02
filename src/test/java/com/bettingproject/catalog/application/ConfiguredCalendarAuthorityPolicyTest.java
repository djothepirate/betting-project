package com.bettingproject.catalog.application;

import java.util.List;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredCalendarAuthorityPolicyTest {

    private static final String POLICY_VERSION = "synthetic-policy-v1";

    @Test
    void resolvesExactSyntheticPrimaryAndControlAssignments() {
        CalendarAuthorityKey primaryKey = key("synthetic-provider-a", "competition-a", "2026", "REGULAR");
        CalendarAuthorityKey controlKey = key("synthetic-provider-b", "competition-a", "2026", "REGULAR");
        CalendarAuthorityPolicy policy = new ConfiguredCalendarAuthorityPolicy(
                POLICY_VERSION,
                List.of(
                        new CalendarAuthorityAssignment(primaryKey, CalendarAuthorityRole.PRIMARY),
                        new CalendarAuthorityAssignment(controlKey, CalendarAuthorityRole.CONTROL)));

        assertThat(policy.resolve(primaryKey))
                .isEqualTo(new CalendarAuthorityResolution(CalendarAuthorityRole.PRIMARY, POLICY_VERSION));
        assertThat(policy.resolve(controlKey))
                .isEqualTo(new CalendarAuthorityResolution(CalendarAuthorityRole.CONTROL, POLICY_VERSION));
    }

    @Test
    void returnsUnassignedForEveryNonExactKeyWithoutImplicitPromotion() {
        CalendarAuthorityKey configuredKey = key(
                "synthetic-provider-a",
                "competition-a",
                "2026",
                "REGULAR");
        CalendarAuthorityPolicy policy = new ConfiguredCalendarAuthorityPolicy(
                POLICY_VERSION,
                List.of(new CalendarAuthorityAssignment(configuredKey, CalendarAuthorityRole.PRIMARY)));

        assertThat(policy.resolve(key("Synthetic-provider-a", "competition-a", "2026", "REGULAR")).role())
                .isEqualTo(CalendarAuthorityRole.UNASSIGNED);
        assertThat(policy.resolve(key("synthetic-provider-a", "competition-b", "2026", "REGULAR")).role())
                .isEqualTo(CalendarAuthorityRole.UNASSIGNED);
        assertThat(policy.resolve(key("synthetic-provider-a", "competition-a", "2027", "REGULAR")).role())
                .isEqualTo(CalendarAuthorityRole.UNASSIGNED);
        assertThat(policy.resolve(key("synthetic-provider-a", "competition-a", "2026", "PLAYOFF")).role())
                .isEqualTo(CalendarAuthorityRole.UNASSIGNED);
    }

    @Test
    void runtimeKeysPreserveLiteralWildcardCharactersWithoutInterpretingThem() {
        CalendarAuthorityKey provider = key("synthetic-*", "competition-a", "2026", "REGULAR");
        CalendarAuthorityKey competition = key(
                "synthetic-provider", "competition-?", "2026", "REGULAR");
        CalendarAuthorityKey season = key(
                "synthetic-provider", "competition-a", "20%", "REGULAR");
        CalendarAuthorityKey phase = key(
                "synthetic-provider", "competition-a", "2026", "REGULAR*");

        assertThat(provider.provider()).isEqualTo("synthetic-*");
        assertThat(competition.providerCompetitionId()).isEqualTo("competition-?");
        assertThat(season.season()).isEqualTo("20%");
        assertThat(phase.phase()).isEqualTo("REGULAR*");
    }

    @Test
    void configuredPolicyRejectsAssignmentsContainingWildcardCharacters() {
        assertThatThrownBy(() -> policyWithPrimaryAssignment(
                key("synthetic-*", "competition-a", "2026", "REGULAR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcards");
        assertThatThrownBy(() -> policyWithPrimaryAssignment(
                key("synthetic-provider", "competition-?", "2026", "REGULAR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcards");
        assertThatThrownBy(() -> policyWithPrimaryAssignment(
                key("synthetic-provider", "competition-a", "20%", "REGULAR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcards");
        assertThatThrownBy(() -> policyWithPrimaryAssignment(
                key("synthetic-provider", "competition-a", "2026", "REGULAR*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcards");
    }

    @Test
    void rejectsWhitespaceInsteadOfNormalizingKeys() {
        assertThatThrownBy(() -> key(" synthetic-provider", "competition-a", "2026", "REGULAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whitespace");
    }

    @Test
    void rejectsDuplicateAndConflictingAssignments() {
        CalendarAuthorityKey key = key("synthetic-provider", "competition-a", "2026", "REGULAR");

        assertThatThrownBy(() -> new ConfiguredCalendarAuthorityPolicy(
                POLICY_VERSION,
                List.of(
                        new CalendarAuthorityAssignment(key, CalendarAuthorityRole.PRIMARY),
                        new CalendarAuthorityAssignment(key, CalendarAuthorityRole.PRIMARY))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");

        assertThatThrownBy(() -> new ConfiguredCalendarAuthorityPolicy(
                POLICY_VERSION,
                List.of(
                        new CalendarAuthorityAssignment(key, CalendarAuthorityRole.PRIMARY),
                        new CalendarAuthorityAssignment(key, CalendarAuthorityRole.CONTROL))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting");
    }

    @Test
    void emptyConfigurationIsClosedByDefaultAndKeepsItsPolicyVersion() {
        CalendarAuthorityPolicy policy = new ConfiguredCalendarAuthorityPolicy(POLICY_VERSION, List.of());

        assertThat(policy.resolve(key("synthetic-provider", "competition-a", "2026", "REGULAR")))
                .isEqualTo(new CalendarAuthorityResolution(CalendarAuthorityRole.UNASSIGNED, POLICY_VERSION));
    }

    private static CalendarAuthorityKey key(
            String provider,
            String providerCompetitionId,
            String season,
            String phase) {
        return new CalendarAuthorityKey(
                provider,
                providerCompetitionId,
                season,
                phase,
                CalendarAuthorityDataType.CALENDAR);
    }

    private static CalendarAuthorityPolicy policyWithPrimaryAssignment(CalendarAuthorityKey key) {
        return new ConfiguredCalendarAuthorityPolicy(
                POLICY_VERSION,
                List.of(new CalendarAuthorityAssignment(key, CalendarAuthorityRole.PRIMARY)));
    }
}
