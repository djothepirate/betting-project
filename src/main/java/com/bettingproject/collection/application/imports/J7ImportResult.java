package com.bettingproject.collection.application.imports;

import java.util.Objects;

public sealed interface J7ImportResult {

    record Imported(StoredJ7Import receipt) implements J7ImportResult {
        public Imported {
            receipt = Objects.requireNonNull(receipt, "receipt");
        }
    }

    record Duplicate(StoredJ7Import receipt) implements J7ImportResult {
        public Duplicate {
            receipt = Objects.requireNonNull(receipt, "receipt");
        }
    }

    record Conflict(J7ImportAuditReason reason) implements J7ImportResult {
        public Conflict {
            reason = Objects.requireNonNull(reason, "reason");
        }
    }
}
