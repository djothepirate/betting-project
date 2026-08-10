package com.bettingproject.operations.application;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("betting.runtime")
public record RuntimeProperties(RuntimeMode mode) {

    public RuntimeProperties {
        Objects.requireNonNull(mode, "betting.runtime.mode must be configured");
    }
}
