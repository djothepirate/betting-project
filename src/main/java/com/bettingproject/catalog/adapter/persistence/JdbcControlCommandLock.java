package com.bettingproject.catalog.adapter.persistence;

import com.bettingproject.catalog.application.ControlCommandLock;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcControlCommandLock implements ControlCommandLock {

    static final String LOCK_PREFIX =
            "betting-project:catalog:control-command:idempotency:v1:";

    private final JdbcClient jdbcClient;

    public JdbcControlCommandLock(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void acquire(String idempotencyKey) {
        jdbcClient.sql("""
                SELECT pg_advisory_xact_lock(hashtextextended(:lockName, 0))
                """)
                .param("lockName", LOCK_PREFIX + idempotencyKey)
                .query((resultSet, rowNumber) -> Boolean.TRUE)
                .single();
    }
}
