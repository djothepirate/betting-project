package com.bettingproject.shared.infrastructure;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
