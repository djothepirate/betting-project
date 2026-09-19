package com.bettingproject.operations.application.jobs;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.operations.domain.JobModel.*;

public interface JobRepository {
    Enqueued enqueue(Submission submission);
    Optional<Job> find(UUID id);
    Optional<Claim> claimNext();
    int recoverExpired(int limit);
    void lockAndCheck(Claim claim);
    void finish(Claim claim, Outcome outcome);
    Optional<String> effectResult(UUID jobId, String effectKey);
    void appendEffect(UUID jobId, String effectKey, String result);
    Instant databaseNow();
}
