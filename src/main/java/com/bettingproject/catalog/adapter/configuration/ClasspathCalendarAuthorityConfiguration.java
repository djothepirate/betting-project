package com.bettingproject.catalog.adapter.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.bettingproject.catalog.application.CalendarAuthorityAssignment;
import com.bettingproject.catalog.application.CalendarAuthorityDataType;
import com.bettingproject.catalog.application.CalendarAuthorityKey;
import com.bettingproject.catalog.application.CalendarAuthorityPolicy;
import com.bettingproject.catalog.application.ConfiguredCalendarAuthorityPolicy;
import com.bettingproject.catalog.application.RegistryCalendarAuthorityPolicy;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@Profile({"control-api", "batch-worker"})
public class ClasspathCalendarAuthorityConfiguration {

    static final String POLICY_SCHEMA_VERSION = "calendar-authority-policy-v1";
    static final String POLICY_RESOURCE = "catalog/calendar-authority-policy-v1.json";

    @Bean
    CalendarAuthorityPolicy calendarAuthorityPolicy(ProviderCapabilityRegistry registry) {
        return new RegistryCalendarAuthorityPolicy(registry);
    }

    static CalendarAuthorityPolicy load(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            PolicyDocument document = JsonMapper.builder().build().readValue(input, PolicyDocument.class);
            if (document == null) {
                throw new IllegalArgumentException("calendar authority policy document must not be null");
            }
            if (!POLICY_SCHEMA_VERSION.equals(document.schemaVersion())) {
                throw new IllegalArgumentException(
                        "unsupported calendar authority policy schemaVersion: " + document.schemaVersion());
            }
            if (document.entries() == null) {
                throw new IllegalArgumentException("calendar authority policy entries must not be null");
            }

            List<CalendarAuthorityAssignment> assignments = document.entries().stream()
                    .map(PolicyEntry::toAssignment)
                    .toList();
            return new ConfiguredCalendarAuthorityPolicy(document.policyVersion(), assignments);
        }
        catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "failed to load calendar authority policy from " + resource.getDescription(),
                    exception);
        }
    }

    private record PolicyDocument(
            String schemaVersion,
            String policyVersion,
            List<PolicyEntry> entries) {
    }

    private record PolicyEntry(
            String provider,
            String providerCompetitionId,
            String season,
            String phase,
            CalendarAuthorityDataType dataType,
            CalendarAuthorityRole role) {

        CalendarAuthorityAssignment toAssignment() {
            return new CalendarAuthorityAssignment(
                    new CalendarAuthorityKey(
                            provider,
                            providerCompetitionId,
                            season,
                            phase,
                            dataType),
                    role);
        }
    }
}
