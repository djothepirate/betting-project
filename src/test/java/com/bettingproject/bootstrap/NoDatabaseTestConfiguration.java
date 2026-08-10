package com.bettingproject.bootstrap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

@TestConfiguration(proxyBeanMethods = false)
public class NoDatabaseTestConfiguration {

    @Bean
    JdbcClient jdbcClient() {
        return sql -> {
            throw new UnsupportedOperationException("Database access is disabled in profile bootstrap tests");
        };
    }
}
