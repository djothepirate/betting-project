package com.bettingproject.enrichment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrichmentDispatchWindowTest {
    private static final Instant KICKOFF = Instant.parse("2026-10-04T18:00:00Z");
    private static final Instant FINAL = Instant.parse("2026-10-04T20:00:00Z");

    @Test
    void lineupCheckpointsHaveDisjointHalfOpenWindows() {
        var t30 = window(EnrichmentPlanStepCode.LINEUP_T_MINUS_30, KICKOFF, null);
        var t15 = window(EnrichmentPlanStepCode.LINEUP_T_MINUS_15, KICKOFF, null);

        assertThat(t30.opensAt()).isEqualTo(KICKOFF.minusSeconds(1800));
        assertThat(t30.closesAt()).isEqualTo(t15.opensAt());
        assertThat(t30.decide(t30.opensAt())).isEqualTo(EnrichmentDispatchWindow.Decision.ELIGIBLE);
        assertThat(t30.decide(t30.closesAt())).isEqualTo(EnrichmentDispatchWindow.Decision.MISSED_WINDOW);
        assertThat(t15.closesAt()).isEqualTo(KICKOFF);
        assertThat(t15.decide(KICKOFF)).isEqualTo(EnrichmentDispatchWindow.Decision.MISSED_WINDOW);
    }

    @Test
    void detailWindowsUseTheAcceptedFiveMinuteTolerance() {
        var kickoff = window(EnrichmentPlanStepCode.DETAIL_AT_KICKOFF, KICKOFF, null);
        var plus45 = window(EnrichmentPlanStepCode.DETAIL_PLUS_45, KICKOFF, null);

        assertThat(kickoff.opensAt()).isEqualTo(KICKOFF.minusSeconds(300));
        assertThat(kickoff.closesAt()).isEqualTo(KICKOFF.plusSeconds(300));
        assertThat(plus45.opensAt()).isEqualTo(KICKOFF.plusSeconds(2400));
        assertThat(plus45.closesAt()).isEqualTo(KICKOFF.plusSeconds(3000));
        assertThat(kickoff.decide(kickoff.opensAt().minusNanos(1)))
                .isEqualTo(EnrichmentDispatchWindow.Decision.BEFORE_WINDOW);
    }

    @Test
    void postmatchWindowsRequireExplicitFinalEvidence() {
        var missingEvidence = new EnrichmentPlanStep(UUID.randomUUID(),
                EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL, "POSTMATCH", null, "EXPLICIT_FINAL_STATUS");
        assertThatThrownBy(() -> EnrichmentDispatchWindow.forStep(missingEvidence, KICKOFF))
                .isInstanceOf(NullPointerException.class);

        var postmatch = window(EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL, KICKOFF, FINAL);
        assertThat(postmatch.opensAt()).isEqualTo(FINAL);
        assertThat(postmatch.closesAt()).isEqualTo(Instant.MAX);
        assertThat(postmatch.decide(FINAL.plusSeconds(86400)))
                .isEqualTo(EnrichmentDispatchWindow.Decision.ELIGIBLE);

        var recheck = window(EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60, KICKOFF, FINAL);
        assertThat(recheck.opensAt()).isEqualTo(FINAL.plusSeconds(3600));
        assertThat(recheck.closesAt()).isEqualTo(FINAL.plusSeconds(3900));
        assertThat(recheck.decide(recheck.closesAt())).isEqualTo(EnrichmentDispatchWindow.Decision.MISSED_WINDOW);
    }

    @Test
    void rejectsEmptyOrInvertedWindows() {
        assertThatThrownBy(() -> new EnrichmentDispatchWindow(KICKOFF, KICKOFF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrichmentDispatchWindow(KICKOFF.plusSeconds(1), KICKOFF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EnrichmentDispatchWindow window(EnrichmentPlanStepCode code, Instant kickoff, Instant finalAt) {
        boolean finalDriven = code == EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL
                || code == EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60;
        EnrichmentPlanStep step = new EnrichmentPlanStep(UUID.randomUUID(), code,
                code.name().startsWith("POSTMATCH_RECHECK") ? "POSTMATCH_RECHECK"
                        : finalDriven ? "POSTMATCH" : code.name().startsWith("LINEUP") ? "LINEUP" : "DETAIL",
                finalAt == null ? scheduled(code, kickoff) : finalAt.plusSeconds(
                        code == EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL ? 0 : 3600),
                finalDriven ? "EXPLICIT_FINAL_STATUS" : code.name().startsWith("LINEUP")
                        ? "LINEUP_NOT_COMPLETE" : "SCHEDULED_KICKOFF_WINDOW",
                finalAt, finalAt == null ? null : UUID.randomUUID());
        return EnrichmentDispatchWindow.forStep(step, kickoff);
    }

    private static Instant scheduled(EnrichmentPlanStepCode code, Instant kickoff) {
        return switch (code) {
            case LINEUP_T_MINUS_30 -> kickoff.minusSeconds(1800);
            case LINEUP_T_MINUS_15 -> kickoff.minusSeconds(900);
            case DETAIL_AT_KICKOFF -> kickoff;
            case DETAIL_PLUS_45 -> kickoff.plusSeconds(2700);
            case POSTMATCH_AFTER_FINAL, POSTMATCH_RECHECK_FINAL_PLUS_60 -> null;
        };
    }
}
