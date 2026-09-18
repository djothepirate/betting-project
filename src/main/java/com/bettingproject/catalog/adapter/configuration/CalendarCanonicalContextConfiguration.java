package com.bettingproject.catalog.adapter.configuration;

import com.bettingproject.catalog.application.CalendarCanonicalContextPolicy;
import com.bettingproject.catalog.application.RegistryCalendarCanonicalContextPolicy;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"control-api", "batch-worker"})
public class CalendarCanonicalContextConfiguration {
    @Bean
    CalendarCanonicalContextPolicy calendarCanonicalContextPolicy(ProviderCapabilityRegistry registry) {
        return new RegistryCalendarCanonicalContextPolicy(registry);
    }
}
