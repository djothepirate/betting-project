package com.bettingproject.operations.application.jobs;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.bettingproject.operations.domain.JobModel.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One bounded tick, no sleeping and no ambient transaction across a handler. */
@Service
@Profile("batch-worker")
@Transactional(propagation = Propagation.NEVER)
public class JobWorker {
    private final JobTransactions transactions;
    private final Map<Type, JobHandler> handlers = new EnumMap<>(Type.class);

    public JobWorker(JobTransactions transactions, List<JobHandler> handlers) {
        this.transactions = transactions;
        for (JobHandler handler : handlers) {
            if (!handler.type().executable() || this.handlers.put(handler.type(), handler) != null) {
                throw new IllegalStateException("Invalid job handler registry");
            }
        }
    }

    public boolean tick() {
        transactions.recoverExpired(10);
        var selected = transactions.claimNext();
        if (selected.isEmpty()) { return false; }
        Claim claim = selected.get();
        Outcome outcome;
        try {
            JobHandler handler = handlers.get(claim.job().type());
            outcome = handler == null ? Outcome.failed("NO_HANDLER") : handler.execute(claim);
        }
        catch (JobLeaseLostException lost) { return true; }
        catch (RuntimeException failure) {
            // No exception message, command, provider body or credentials in the durable error.
            outcome = Outcome.retry("EXECUTION_ERROR");
        }
        try { transactions.finish(claim, outcome); }
        catch (JobLeaseLostException lost) { /* The new owner/recovery is authoritative. */ }
        return true;
    }
}
