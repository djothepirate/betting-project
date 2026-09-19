package com.bettingproject.operations.application.jobs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.operations.domain.JobModel;
import com.bettingproject.shared.application.ReadPage;

public interface JobQueryPort {
    record JobView(UUID id, JobModel.Type type, JobModel.Status status, int attempts, int maxAttempts,
            long version, Instant scheduledAt, Instant nextRunAt, Instant leaseUntil, String lastErrorCode,
            String outboxStatus, Instant createdAt, Instant updatedAt) implements ReadPage.Timed {
        public Instant sortTime() { return createdAt; }
    }
    record EventView(UUID id, UUID jobId, int attemptNumber, long version,
            String eventType, String reasonCode, Instant createdAt) implements ReadPage.Timed {
        public Instant sortTime() { return createdAt; }
    }
    List<JobView> jobs(JobModel.Type type, JobModel.Status status, ReadPage.Request page);
    Optional<JobView> job(UUID id);
    List<EventView> events(UUID jobId, ReadPage.Request page);
}
