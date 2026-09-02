package com.bettingproject.catalog.adapter.configuration;

import java.nio.charset.StandardCharsets;

import com.bettingproject.catalog.application.CalendarAuthorityDataType;
import com.bettingproject.catalog.application.CalendarAuthorityKey;
import com.bettingproject.catalog.application.CalendarAuthorityPolicy;
import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathCalendarAuthorityConfigurationTest {

    @Test
    void versionedClasspathBaselineLoadsClosedWithoutRealProviderReferences() {
        CalendarAuthorityPolicy policy = ClasspathCalendarAuthorityConfiguration.load(
                new ClassPathResource(ClasspathCalendarAuthorityConfiguration.POLICY_RESOURCE));

        assertThat(policy.resolve(key("synthetic-provider", "competition-a")).role())
                .isEqualTo(CalendarAuthorityRole.UNASSIGNED);
    }

    @Test
    void typedConfigurationLoadsExactSyntheticAssignments() {
        CalendarAuthorityPolicy policy = load("""
                {
                  "schemaVersion": "calendar-authority-policy-v1",
                  "policyVersion": "synthetic-config-v7",
                  "entries": [
                    {
                      "provider": "synthetic-primary",
                      "providerCompetitionId": "competition-a",
                      "season": "2026",
                      "phase": "REGULAR",
                      "dataType": "CALENDAR",
                      "role": "PRIMARY"
                    },
                    {
                      "provider": "synthetic-control",
                      "providerCompetitionId": "competition-a",
                      "season": "2026",
                      "phase": "REGULAR",
                      "dataType": "CALENDAR",
                      "role": "CONTROL"
                    }
                  ]
                }
                """);

        assertThat(policy.resolve(key("synthetic-primary", "competition-a")).role())
                .isEqualTo(CalendarAuthorityRole.PRIMARY);
        assertThat(policy.resolve(key("synthetic-control", "competition-a")).role())
                .isEqualTo(CalendarAuthorityRole.CONTROL);
        assertThat(policy.resolve(key("synthetic-primary", "competition-b")).role())
                .isEqualTo(CalendarAuthorityRole.UNASSIGNED);
    }

    @Test
    void inconsistentConfigurationFailsAtLoadTime() {
        assertThatThrownBy(() -> load("""
                {
                  "schemaVersion": "calendar-authority-policy-v1",
                  "policyVersion": "synthetic-config-v1",
                  "entries": [
                    {
                      "provider": "synthetic-provider",
                      "providerCompetitionId": "competition-a",
                      "season": "2026",
                      "phase": "REGULAR",
                      "dataType": "CALENDAR",
                      "role": "PRIMARY"
                    },
                    {
                      "provider": "synthetic-provider",
                      "providerCompetitionId": "competition-a",
                      "season": "2026",
                      "phase": "REGULAR",
                      "dataType": "CALENDAR",
                      "role": "CONTROL"
                    }
                  ]
                }
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("conflicting calendar authority roles for key "
                        + "CalendarAuthorityKey[provider=synthetic-provider, "
                        + "providerCompetitionId=competition-a, season=2026, phase=REGULAR, dataType=CALENDAR]");
    }

    @Test
    void wildcardAssignmentsFailAtLoadTimeForEverySupportedMarker() {
        assertWildcardAssignmentRejected(
                "synthetic-*", "competition-a", "2026", "REGULAR", "provider");
        assertWildcardAssignmentRejected(
                "synthetic-provider", "competition-?", "2026", "REGULAR", "providerCompetitionId");
        assertWildcardAssignmentRejected(
                "synthetic-provider", "competition-a", "20%", "REGULAR", "season");
        assertWildcardAssignmentRejected(
                "synthetic-provider", "competition-a", "2026", "REGULAR*", "phase");
    }

    @Test
    void unknownSchemaOrDataTypeFailsAtLoadTime() {
        assertThatThrownBy(() -> load("""
                {
                  "schemaVersion": "calendar-authority-policy-v2",
                  "policyVersion": "synthetic-config-v1",
                  "entries": []
                }
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("unsupported calendar authority policy schemaVersion: "
                        + "calendar-authority-policy-v2");

        assertThatThrownBy(() -> load("""
                {
                  "schemaVersion": "calendar-authority-policy-v1",
                  "policyVersion": "synthetic-config-v1",
                  "entries": [
                    {
                      "provider": "synthetic-provider",
                      "providerCompetitionId": "competition-a",
                      "season": "2026",
                      "phase": "REGULAR",
                      "dataType": "MATCH_DETAIL",
                      "role": "PRIMARY"
                    }
                  ]
                }
                """))
                .isInstanceOf(IllegalStateException.class);
    }

    private static CalendarAuthorityPolicy load(String json) {
        return ClasspathCalendarAuthorityConfiguration.load(
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8), "synthetic-policy.json"));
    }

    private static void assertWildcardAssignmentRejected(
            String provider,
            String providerCompetitionId,
            String season,
            String phase,
            String rejectedField) {
        assertThatThrownBy(() -> load("""
                {
                  "schemaVersion": "calendar-authority-policy-v1",
                  "policyVersion": "synthetic-config-v1",
                  "entries": [
                    {
                      "provider": "%s",
                      "providerCompetitionId": "%s",
                      "season": "%s",
                      "phase": "%s",
                      "dataType": "CALENDAR",
                      "role": "PRIMARY"
                    }
                  ]
                }
                """.formatted(provider, providerCompetitionId, season, phase)))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("wildcards")
                        .hasMessageContaining(rejectedField));
    }

    private static CalendarAuthorityKey key(String provider, String providerCompetitionId) {
        return new CalendarAuthorityKey(
                provider,
                providerCompetitionId,
                "2026",
                "REGULAR",
                CalendarAuthorityDataType.CALENDAR);
    }
}
