package com.bettingproject.operations.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobOutboxServiceTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-09-01T15:43:00Z");
    private static final Clock CLOCK = Clock.fixed(SCHEDULED_AT, ZoneOffset.UTC);

    @Test
    void schedulesTheJobAndOutboxMessageThroughThePersistencePort() {
        RecordingJobOutboxRepository repository = new RecordingJobOutboxRepository(true);
        JobOutboxService service = new JobOutboxService(repository, CLOCK);

        boolean scheduled = service.schedulePublication(
                "publication:fixture-42",
                "PUBLISH_FIXTURE",
                "telegram",
                "{\"fixtureId\":\"fixture-42\"}");

        assertThat(scheduled).isTrue();
        assertThat(repository.operations).containsExactly("job", "outbox");
        assertThat(repository.pendingJob).isNotNull();
        assertThat(repository.pendingJob.id()).isNotNull();
        assertThat(repository.pendingJob.jobKey()).isEqualTo("publication:fixture-42");
        assertThat(repository.pendingJob.jobType()).isEqualTo("PUBLISH_FIXTURE");
        assertThat(repository.pendingJob.scheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(repository.pendingOutboxMessage).isNotNull();
        assertThat(repository.pendingOutboxMessage.id()).isNotNull();
        assertThat(repository.pendingOutboxMessage.id()).isNotEqualTo(repository.pendingJob.id());
        assertThat(repository.pendingOutboxMessage.idempotencyKey())
                .isEqualTo("job:publication:fixture-42:telegram");
        assertThat(repository.pendingOutboxMessage.aggregateId()).isEqualTo(repository.pendingJob.id());
        assertThat(repository.pendingOutboxMessage.destination()).isEqualTo("telegram");
        assertThat(repository.pendingOutboxMessage.payloadJson())
                .isEqualTo("{\"fixtureId\":\"fixture-42\"}");
        assertThat(repository.pendingOutboxMessage.scheduledAt()).isEqualTo(SCHEDULED_AT);
    }

    @Test
    void duplicateJobReturnsFalseWithoutCreatingAnOutboxMessage() {
        RecordingJobOutboxRepository repository = new RecordingJobOutboxRepository(false);
        JobOutboxService service = new JobOutboxService(repository, CLOCK);

        boolean scheduled = service.schedulePublication(
                "publication:fixture-42",
                "PUBLISH_FIXTURE",
                "telegram",
                "{\"fixtureId\":\"fixture-42\"}");

        assertThat(scheduled).isFalse();
        assertThat(repository.operations).containsExactly("job");
        assertThat(repository.pendingJob).isNotNull();
        assertThat(repository.pendingOutboxMessage).isNull();
    }

    private static final class RecordingJobOutboxRepository implements JobOutboxRepository {

        private final boolean insertJobResult;
        private final List<String> operations = new ArrayList<>();
        private PendingJob pendingJob;
        private PendingOutboxMessage pendingOutboxMessage;

        private RecordingJobOutboxRepository(boolean insertJobResult) {
            this.insertJobResult = insertJobResult;
        }

        @Override
        public boolean insertJobIfAbsent(PendingJob job) {
            operations.add("job");
            pendingJob = job;
            return insertJobResult;
        }

        @Override
        public void insertOutboxMessage(PendingOutboxMessage message) {
            operations.add("outbox");
            pendingOutboxMessage = message;
        }
    }
}
