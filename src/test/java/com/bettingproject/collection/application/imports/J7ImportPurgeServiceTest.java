package com.bettingproject.collection.application.imports;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class J7ImportPurgeServiceTest {

    @Test
    void delegatesOnlyAnExplicitBoundedCallAtTheCurrentInstant() {
        Instant now = Instant.parse("2026-10-02T10:00:00Z");
        CapturingStore store = new CapturingStore();
        J7ImportPurgeService service = new J7ImportPurgeService(
                store, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.purgeExpiredPayloads(25)).isEqualTo(7);
        assertThat(store.expiredBefore).isEqualTo(now);
        assertThat(store.purgedAt).isEqualTo(now);
        assertThat(store.maximumRows).isEqualTo(25);
    }

    private static final class CapturingStore implements J7ImportStore {

        private Instant expiredBefore;
        private Instant purgedAt;
        private int maximumRows;

        @Override
        public void acquireImportLock(UUID exportId, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredJ7Import> findByIdempotencyKey(String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredJ7Import> findByExportId(UUID exportId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertImport(StoredJ7Import storedImport, byte[] payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<byte[]> findPayload(UUID importId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendAudit(J7ImportAuditEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendAcceptedOutbox(J7AcceptedOutboxEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int purgeExpiredPayloads(
                Instant expiredBefore,
                Instant purgedAt,
                int maximumRows) {
            this.expiredBefore = expiredBefore;
            this.purgedAt = purgedAt;
            this.maximumRows = maximumRows;
            return 7;
        }
    }
}
