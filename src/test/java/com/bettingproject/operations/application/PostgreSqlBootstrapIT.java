package com.bettingproject.operations.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.bettingproject.collection.application.SnapshotStore;
import com.bettingproject.collection.domain.RawSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER"
        })
@ActiveProfiles("batch-worker")
class PostgreSqlBootstrapIT {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JobOutboxService jobOutboxService;

    @Autowired
    private SnapshotStore snapshotStore;

    @Test
    void flywayCreatesTheExpectedBootstrapSchema() {
        long migrationCount = jdbcClient.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success")
                .query(Long.class)
                .single();
        long requiredTableCount = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('raw_snapshot', 'persistent_job', 'outbox_message')
                """)
                .query(Long.class)
                .single();

        assertThat(migrationCount).isPositive();
        assertThat(requiredTableCount).isEqualTo(3);
    }

    @Test
    void duplicateJobCreatesOneJobAndOneOutboxMessage() {
        boolean first = jobOutboxService.schedulePublication(
                "publication:fixture-42",
                "PUBLISH_FIXTURE",
                "telegram",
                "{\"fixtureId\":\"fixture-42\"}");
        boolean second = jobOutboxService.schedulePublication(
                "publication:fixture-42",
                "PUBLISH_FIXTURE",
                "telegram",
                "{\"fixtureId\":\"fixture-42\"}");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(jobOutboxService.countJobs("publication:fixture-42")).isEqualTo(1);
        assertThat(jobOutboxService.countOutboxMessages("job:publication:fixture-42:telegram")).isEqualTo(1);
    }

    @Test
    void invalidOutboxPayloadRollsBackTheJob() {
        assertThatThrownBy(() -> jobOutboxService.schedulePublication(
                "publication:invalid-json",
                "PUBLISH_FIXTURE",
                "telegram",
                "not-json"))
                .isInstanceOf(DataAccessException.class);

        assertThat(jobOutboxService.countJobs("publication:invalid-json")).isZero();
    }

    @Test
    @Transactional
    void duplicateRawSnapshotIsIgnored() {
        RawSnapshot snapshot = RawSnapshot.capture(
                "offline-fixture",
                "calendar",
                Instant.parse("2026-08-10T10:00:00Z"),
                "{\"fixtures\":[]}".getBytes(StandardCharsets.UTF_8),
                "fixture-parser-1");

        assertThat(snapshotStore.store(snapshot)).isTrue();
        assertThat(snapshotStore.store(snapshot)).isFalse();
    }
}
