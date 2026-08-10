package com.bettingproject.operations.adapter.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.bettingproject.operations.application.RuntimeMode;
import com.bettingproject.operations.application.RuntimeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/bootstrap")
@Profile("control-api")
public class BootstrapStatusController {

    private final RuntimeProperties runtimeProperties;
    private final Environment environment;
    private final Clock clock;

    @Autowired
    public BootstrapStatusController(RuntimeProperties runtimeProperties, Environment environment) {
        this(runtimeProperties, environment, Clock.systemUTC());
    }

    BootstrapStatusController(RuntimeProperties runtimeProperties, Environment environment, Clock clock) {
        this.runtimeProperties = runtimeProperties;
        this.environment = environment;
        this.clock = clock;
    }

    @GetMapping("/status")
    public BootstrapStatus status() {
        return new BootstrapStatus(
                "betting-project",
                runtimeProperties.mode(),
                List.of(environment.getActiveProfiles()),
                Instant.now(clock));
    }

    public record BootstrapStatus(
            String application,
            RuntimeMode mode,
            List<String> activeProfiles,
            Instant observedAt) {
    }
}
