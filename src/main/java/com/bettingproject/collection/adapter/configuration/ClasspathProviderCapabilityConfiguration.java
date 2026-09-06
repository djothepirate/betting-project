package com.bettingproject.collection.adapter.configuration;

import java.io.IOException;

import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.application.capability.ProviderRoutingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@Profile({"control-api", "batch-worker"})
public class ClasspathProviderCapabilityConfiguration {
    public static final String RESOURCE = "collection/provider-capability-registry-v1.json";

    @Bean
    ProviderCapabilityRegistry providerCapabilityRegistry() {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            return new ProviderCapabilityDocumentParser().parse(input.readAllBytes());
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("failed to load classpath provider capability registry");
        }
    }

    @Bean
    ProviderRoutingService providerRoutingService(ProviderCapabilityRegistry registry) {
        return new ProviderRoutingService(registry);
    }
}
