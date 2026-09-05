package com.bettingproject.collection.adapter.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses unsafe static receiver settings while the web-server factory is still being
 * customized. This phase runs before Boot asks its SSL bundle to resolve key/trust-store
 * resources, so a remote resource cannot be dereferenced before the regular bean guard runs.
 */
@Component
@Profile("control-api")
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public final class J7ReceiverEarlyWebServerGuard
        implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private final J7ReceiverProperties properties;
    private final Environment environment;

    public J7ReceiverEarlyWebServerGuard(
            J7ReceiverProperties properties,
            Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        J7ReceiverActivationGuard.validateStaticConfiguration(properties, environment);
    }
}
