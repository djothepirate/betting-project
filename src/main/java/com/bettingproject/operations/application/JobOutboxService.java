package com.bettingproject.operations.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"control-api", "batch-worker"})
public class JobOutboxService {

    private final JobOutboxRepository repository;
    private final Clock clock;

    public JobOutboxService(JobOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean schedulePublication(
            String jobKey,
            String jobType,
            String destination,
            String payloadJson) {
        UUID jobId = UUID.randomUUID();
        Instant scheduledAt = clock.instant();
        if (!repository.insertJobIfAbsent(new PendingJob(jobId, jobKey, jobType, scheduledAt))) {
            return false;
        }

        repository.insertOutboxMessage(new PendingOutboxMessage(
                UUID.randomUUID(),
                "job:" + jobKey + ":" + destination,
                jobId,
                destination,
                payloadJson,
                scheduledAt));
        return true;
    }
}
