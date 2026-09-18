package com.bettingproject.operations.application.jobs;

import java.util.Optional;
import java.util.function.Supplier;
import com.bettingproject.operations.domain.JobModel.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"control-api", "batch-worker"})
@Transactional
public class JobTransactions {
    private final JobRepository repository;
    public JobTransactions(JobRepository repository) { this.repository = repository; }

    public Enqueued enqueue(Submission submission) { return repository.enqueue(submission); }
    public Optional<Claim> claimNext() { return repository.claimNext(); }
    public int recoverExpired(int limit) {
        if (limit < 1 || limit > 100) { throw new IllegalArgumentException("Invalid recovery limit"); }
        return repository.recoverExpired(limit);
    }
    public void finish(Claim claim, Outcome outcome) {
        repository.lockAndCheck(claim);
        repository.finish(claim, outcome);
    }

    /** Job row first; then budget scope/window/intent. Never execute network code here. */
    public <T> T fenced(Claim claim, Supplier<T> work) {
        repository.lockAndCheck(claim);
        return work.get();
    }

    /** Result and effects commit together, including a replay's canonical mutations. */
    public String once(Claim claim, String effectKey, Supplier<String> work) {
        com.bettingproject.operations.domain.JobModel.text(effectKey, 200);
        repository.lockAndCheck(claim);
        Optional<String> prior = repository.effectResult(claim.job().id(), effectKey);
        if (prior.isPresent()) { return prior.get(); }
        String result = work.get();
        if (result == null || !result.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalStateException("Invalid effect result");
        }
        repository.appendEffect(claim.job().id(), effectKey, result);
        return result;
    }
}
