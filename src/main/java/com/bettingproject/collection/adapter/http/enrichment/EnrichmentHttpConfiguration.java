package com.bettingproject.collection.adapter.http.enrichment;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile({"control-api", "batch-worker"})
public class EnrichmentHttpConfiguration {
    @Bean
    HighlightlyEnrichmentClient highlightlyEnrichmentClient(Environment environment, Clock clock) {
        String credential = credential(environment, "highlightly", "HIGHLIGHTLY_API_KEY");
        return new HighlightlyEnrichmentClient(credential == null ? null
                : BoundedEnrichmentHttpClient.productionTransport(), credential, clock);
    }

    @Bean
    FootballDataEnrichmentClient footballDataEnrichmentClient(Environment environment, Clock clock) {
        String credential = credential(environment, "football-data", "FOOTBALL_DATA_API_KEY");
        return new FootballDataEnrichmentClient(credential == null ? null
                : BoundedEnrichmentHttpClient.productionTransport(), credential, clock);
    }

    private static String credential(Environment environment, String provider, String environmentVariable) {
        String prefix = "betting.providers." + provider;
        if (!"true".equals(environment.getProperty(prefix + ".enrichment-enabled"))
                || !EnrichmentHttpRuntimeSafety.allowed()) { return null; }
        String configured = environment.getProperty(prefix + ".api-key");
        if (configured == null) { configured = environment.getProperty(environmentVariable); }
        return BoundedEnrichmentHttpClient.usableCredential(configured) ? configured : null;
    }
}
