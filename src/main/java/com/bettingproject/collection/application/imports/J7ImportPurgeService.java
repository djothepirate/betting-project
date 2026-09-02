package com.bettingproject.collection.application.imports;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit bounded use case only. INT-001 deliberately exposes no scheduler or HTTP purge route.
 */
@Service
@Profile("control-api")
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class J7ImportPurgeService {

    private final J7ImportStore store;
    private final Clock clock;

    public J7ImportPurgeService(J7ImportStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public int purgeExpiredPayloads(int maximumRows) {
        Instant now = clock.instant();
        return store.purgeExpiredPayloads(now, now, maximumRows);
    }
}
