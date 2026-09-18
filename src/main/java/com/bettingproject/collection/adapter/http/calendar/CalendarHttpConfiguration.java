package com.bettingproject.collection.adapter.http.calendar;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile({"control-api", "batch-worker"})
public class CalendarHttpConfiguration {
    @Bean
    HighlightlyCalendarPageClient highlightlyCalendarPageClient(Environment environment, Clock clock) {
        String credential = credential(environment, "highlightly", "HIGHLIGHTLY_API_KEY");
        return new HighlightlyCalendarPageClient(credential == null ? null
                : BoundedCalendarHttpClient.productionTransport(), credential, clock);
    }

    @Bean
    FootballDataCalendarPageClient footballDataCalendarPageClient(Environment environment, Clock clock) {
        String credential = credential(environment, "football-data", "FOOTBALL_DATA_API_KEY");
        return new FootballDataCalendarPageClient(credential == null ? null
                : BoundedCalendarHttpClient.productionTransport(), credential, clock);
    }

    private static String credential(Environment environment, String provider, String environmentName) {
        String prefix = "betting.providers." + provider;
        if (!"true".equals(environment.getProperty(prefix + ".enabled"))) {
            return null;
        }
        // Do not enable a transport whose JDK might retry GET internally or log credentials.
        // Changing these JVM-global properties here would be too late for cached JDK options.
        if (!CalendarHttpRuntimeSafety.allowed()) {
            return null;
        }
        String value = environment.getProperty(prefix + ".api-key");
        if (value == null) {
            value = environment.getProperty(environmentName);
        }
        return BoundedCalendarHttpClient.usableSecret(value) ? value : null;
    }
}
