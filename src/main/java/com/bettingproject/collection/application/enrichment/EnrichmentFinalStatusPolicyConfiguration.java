package com.bettingproject.collection.application.enrichment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"control-api", "batch-worker"})
public class EnrichmentFinalStatusPolicyConfiguration {
    @Bean
    EnrichmentFinalStatusPolicy enrichmentFinalStatusPolicy() {
        return new EnrichmentFinalStatusPolicy();
    }
}
