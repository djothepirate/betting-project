package com.bettingproject.operations.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.bettingproject.operations.domain.JobModel.*;
import static org.assertj.core.api.Assertions.*;

class JobModelTest {
    @Test void preservesFiveJobTypesButOnlyTwoAreExecutable() {
        assertThat(Type.values()).hasSize(5);
        assertThat(java.util.Arrays.stream(Type.values()).filter(Type::executable)).containsExactly(Type.CALENDAR_DISCOVERY, Type.REPLAY_NORMALIZATION);
        for (Type type : Type.values()) {
            if (!type.executable()) {
                assertThatThrownBy(() -> new Submission("test", type, "a".repeat(64), Instant.EPOCH, 3))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
    @ParameterizedTest @ValueSource(ints = {-1, 0, 11, 100})
    void rejectsUnboundedAttempts(int count) {
        assertThatThrownBy(() -> new Submission("test", Type.CALENDAR_DISCOVERY, "a".repeat(64), Instant.EPOCH, count))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backoff(count)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void backoffIsExponentialAndBoundedWithoutSleeping() {
        assertThat(backoff(1).toSeconds()).isEqualTo(5);
        assertThat(backoff(2).toSeconds()).isEqualTo(10);
        assertThat(backoff(10).toSeconds()).isEqualTo(300);
    }
    @Test void claimRequiresActualRunningOwner() {
        assertThatThrownBy(() -> new Claim(new Job(UUID.randomUUID(), "x", Type.CALENDAR_DISCOVERY, "a".repeat(64),
                Status.PENDING, 0, 3, 1, Instant.EPOCH, null, null))).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"", "has\ncontrol", " space", " "})
    void refusesUnsafeKeys(String key) {
        assertThatThrownBy(() -> new Submission(key, Type.CALENDAR_DISCOVERY, "a".repeat(64), Instant.EPOCH, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void durableErrorsCannotBeArbitraryExceptionMessages() {
        assertThatThrownBy(() -> Outcome.retry("SQL: sensitive details")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Outcome(true, true, "COMPLETED")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Outcome.success().successful()).isTrue();
    }
}
