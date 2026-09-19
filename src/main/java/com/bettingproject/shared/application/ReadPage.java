package com.bettingproject.shared.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded read contract; cursor encoding belongs exclusively to web adapters. */
public record ReadPage<T>(List<T> items, boolean hasMore) {
    public ReadPage { items = List.copyOf(items); }

    public record Anchor(Instant time, UUID id) {
        public Anchor {
            Objects.requireNonNull(time); Objects.requireNonNull(id);
            if (time.getNano() % 1000 != 0 || time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                    || time.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) {
                throw new IllegalArgumentException("Invalid read anchor");
            }
        }
    }
    public record Request(int limit, Anchor anchor) {
        public Request { if (limit < 1 || limit > 100) { throw new IllegalArgumentException("Invalid read limit"); } }
        public int fetchLimit() { return limit + 1; }
    }
    public interface Timed { UUID id(); Instant sortTime(); }
    public static <T> ReadPage<T> of(List<T> rows, Request request) {
        if (rows.size() > request.fetchLimit()) { throw new IllegalStateException("Unbounded read response"); }
        return new ReadPage<>(rows.subList(0, Math.min(rows.size(), request.limit())), rows.size() > request.limit());
    }
}
