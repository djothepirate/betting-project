package com.bettingproject.bootstrap;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

@TestConfiguration(proxyBeanMethods = false)
public class NoDatabaseTestConfiguration {

    private static final String LOOPBACK_JDBC_URL =
            "jdbc:postgresql://127.0.0.1:5433/betting";

    @Bean
    DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(LOOPBACK_JDBC_URL);
        dataSource.setUsername("test");
        dataSource.setPassword("test");
        return dataSource;
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails() {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return "test";
            }

            @Override
            public String getPassword() {
                return "test";
            }

            @Override
            public String getJdbcUrl() {
                return LOOPBACK_JDBC_URL;
            }
        };
    }

    @Bean
    JdbcClient jdbcClient() {
        return sql -> {
            throw new UnsupportedOperationException("Database access is disabled in profile bootstrap tests");
        };
    }
}
