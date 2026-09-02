package com.bettingproject.catalog.adapter.persistence;

import com.bettingproject.catalog.application.CalendarNormalizationLock;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcCalendarNormalizationLock implements CalendarNormalizationLock {

    private static final String LOCK_NAME = "betting-project:catalog:calendar-normalization:v1";

    private final JdbcClient jdbcClient;

    public JdbcCalendarNormalizationLock(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void acquire() {
        jdbcClient.sql("""
                SELECT pg_advisory_xact_lock(hashtextextended(:lockName, 0))
                """)
                .param("lockName", LOCK_NAME)
                .query((resultSet, rowNumber) -> Boolean.TRUE)
                .single();
    }
}
