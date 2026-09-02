package com.bettingproject.catalog.adapter.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcControlCommandLockTest {

    @Test
    void usesTheVersionedGlobalControlCommandNamespace() {
        assertThat(JdbcControlCommandLock.LOCK_PREFIX)
                .isEqualTo("betting-project:catalog:control-command:idempotency:v1:");
    }
}
