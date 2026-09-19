package com.bettingproject.operations.application.jobs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.operations.domain.JobModel.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JobWorkerTest {
    private static final Claim CLAIM = new Claim(new Job(UUID.randomUUID(), "test", Type.CALENDAR_DISCOVERY,
            "a".repeat(64), Status.RUNNING, 1, 3, 2, Instant.EPOCH, Instant.EPOCH.plusSeconds(120), UUID.randomUUID()));

    @Test void emptyQueueReturnsWithoutHandlerOrActiveWait() {
        FakeTransactions tx = new FakeTransactions(); tx.empty = true;
        assertThat(new JobWorker(tx, List.of()).tick()).isFalse();
        assertThat(tx.recoveryBound).isEqualTo(10);
        assertThat(tx.outcome).isNull();
    }
    @Test void successfulHandlerAcknowledgesOnlyAfterItsReturn() {
        FakeTransactions tx = new FakeTransactions();
        assertThat(new JobWorker(tx, List.of(handler(() -> { assertThat(tx.outcome).isNull(); return Outcome.success(); }))).tick()).isTrue();
        assertThat(tx.outcome.successful()).isTrue();
    }
    @Test void failureUsesGenericBoundedRetryNotExceptionText() {
        FakeTransactions tx = new FakeTransactions();
        new JobWorker(tx, List.of(handler(() -> { throw new IllegalStateException("sensitive runtime data"); }))).tick();
        assertThat(tx.outcome).isEqualTo(Outcome.retry("EXECUTION_ERROR"));
    }
    @Test void staleHandlerCannotAcknowledge() {
        FakeTransactions tx = new FakeTransactions();
        new JobWorker(tx, List.of(handler(() -> { throw new JobLeaseLostException(); }))).tick();
        assertThat(tx.outcome).isNull();
    }
    @Test void absentHandlerIsExplicitTerminalFailure() {
        FakeTransactions tx = new FakeTransactions();
        new JobWorker(tx, List.of()).tick();
        assertThat(tx.outcome).isEqualTo(Outcome.failed("NO_HANDLER"));
    }
    @Test void duplicateHandlersFailClosed() {
        assertThatThrownBy(() -> new JobWorker(new FakeTransactions(), List.of(handler(Outcome::success), handler(Outcome::success))))
                .isInstanceOf(IllegalStateException.class);
    }
    private static JobHandler handler(java.util.function.Supplier<Outcome> action) {
        return new JobHandler() {
            public Type type() { return Type.CALENDAR_DISCOVERY; }
            public Outcome execute(Claim claim) { assertThat(claim).isEqualTo(CLAIM); return action.get(); }
        };
    }
    private static final class FakeTransactions extends JobTransactions {
        boolean empty; int recoveryBound; Outcome outcome;
        FakeTransactions() { super(null); }
        @Override public int recoverExpired(int limit) { recoveryBound = limit; return 0; }
        @Override public Optional<Claim> claimNext() { return empty ? Optional.empty() : Optional.of(CLAIM); }
        @Override public void finish(Claim claim, Outcome result) { outcome = result; }
    }
}
