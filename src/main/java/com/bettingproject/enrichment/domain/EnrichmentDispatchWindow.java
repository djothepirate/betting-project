package com.bettingproject.enrichment.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** UTC execution windows for one admitted enrichment milestone. */
public record EnrichmentDispatchWindow(Instant opensAt, Instant closesAt) {
    public enum Decision { BEFORE_WINDOW, ELIGIBLE, MISSED_WINDOW }

    public EnrichmentDispatchWindow {
        Objects.requireNonNull(opensAt, "opensAt");
        Objects.requireNonNull(closesAt, "closesAt");
        if (!opensAt.isBefore(closesAt)) {
            throw new IllegalArgumentException("dispatch window must be non-empty");
        }
    }

    public static EnrichmentDispatchWindow forStep(EnrichmentPlanStep step, Instant kickoffAt) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(kickoffAt, "kickoffAt");
        return switch (step.code()) {
            case LINEUP_T_MINUS_30 -> scheduled(step, kickoffAt.minus(Duration.ofMinutes(30)),
                    new EnrichmentDispatchWindow(kickoffAt.minus(Duration.ofMinutes(30)),
                            kickoffAt.minus(Duration.ofMinutes(15))));
            case LINEUP_T_MINUS_15 -> scheduled(step, kickoffAt.minus(Duration.ofMinutes(15)),
                    new EnrichmentDispatchWindow(kickoffAt.minus(Duration.ofMinutes(15)), kickoffAt));
            case DETAIL_AT_KICKOFF -> scheduled(step, kickoffAt,
                    new EnrichmentDispatchWindow(kickoffAt.minus(Duration.ofMinutes(5)),
                            kickoffAt.plus(Duration.ofMinutes(5))));
            case DETAIL_PLUS_45 -> scheduled(step, kickoffAt.plus(Duration.ofMinutes(45)),
                    new EnrichmentDispatchWindow(kickoffAt.plus(Duration.ofMinutes(40)),
                            kickoffAt.plus(Duration.ofMinutes(50))));
            case POSTMATCH_AFTER_FINAL -> postmatch(step, Duration.ZERO, null);
            case POSTMATCH_RECHECK_FINAL_PLUS_60 -> postmatch(step, Duration.ofMinutes(60), Duration.ofMinutes(65));
        };
    }

    public Decision decide(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(opensAt)) { return Decision.BEFORE_WINDOW; }
        return now.isBefore(closesAt) ? Decision.ELIGIBLE : Decision.MISSED_WINDOW;
    }

    private static EnrichmentDispatchWindow postmatch(EnrichmentPlanStep step,
            Duration openOffset, Duration closeOffset) {
        Instant finalAt = Objects.requireNonNull(step.triggerObservedAt(), "final observation time");
        return closeOffset == null
                ? new EnrichmentDispatchWindow(finalAt, Instant.MAX)
                : new EnrichmentDispatchWindow(finalAt.plus(openOffset), finalAt.plus(closeOffset));
    }

    private static EnrichmentDispatchWindow scheduled(EnrichmentPlanStep step, Instant expected,
            EnrichmentDispatchWindow window) {
        if (!expected.equals(step.scheduledAt()) || step.triggerObservedAt() != null
                || step.triggerObservationId() != null) {
            throw new IllegalArgumentException("scheduled enrichment step does not match its kickoff window");
        }
        return window;
    }
}
