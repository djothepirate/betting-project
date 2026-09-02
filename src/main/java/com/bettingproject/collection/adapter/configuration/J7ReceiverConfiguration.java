package com.bettingproject.collection.adapter.configuration;

import java.time.Duration;

import com.bettingproject.collection.adapter.web.j7.StrictJ7ImportParser;
import com.bettingproject.collection.application.imports.J7ImportRetentionPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("control-api")
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class J7ReceiverConfiguration {

    @Bean
    StrictJ7ImportParser strictJ7ImportParser() {
        return new StrictJ7ImportParser();
    }

    @Bean
    J7ImportRetentionPolicy j7ImportRetentionPolicy(J7ReceiverProperties properties) {
        return new J7ImportRetentionPolicy(Duration.ofDays(properties.getRetentionDays()));
    }
}
