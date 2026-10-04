package com.bettingproject.enrichment.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrichmentPlanStepTest {
    private static final UUID ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID EVIDENCE = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final Instant FINAL_OBSERVED_AT = Instant.parse("2030-08-10T18:42:13Z");

    @Test void finalDrivenStepsRequireScheduleToFollowTheirExplicitObservationEvidence() {
        new EnrichmentPlanStep(ID, EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL, "POSTMATCH",
                FINAL_OBSERVED_AT, "EXPLICIT_FINAL_STATUS", FINAL_OBSERVED_AT, EVIDENCE);
        new EnrichmentPlanStep(ID, EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60, "POSTMATCH_RECHECK",
                FINAL_OBSERVED_AT.plusSeconds(3600), "PRIORITY_AND_FINAL_STATUS", FINAL_OBSERVED_AT, EVIDENCE);

        assertThatThrownBy(() -> new EnrichmentPlanStep(ID, EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL,
                "POSTMATCH", FINAL_OBSERVED_AT.plusSeconds(1), "EXPLICIT_FINAL_STATUS", FINAL_OBSERVED_AT, EVIDENCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrichmentPlanStep(ID, EnrichmentPlanStepCode.POSTMATCH_RECHECK_FINAL_PLUS_60,
                "POSTMATCH_RECHECK", FINAL_OBSERVED_AT.plusSeconds(3599), "PRIORITY_AND_FINAL_STATUS", FINAL_OBSERVED_AT, EVIDENCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void scheduledFinalDrivenStepWithoutObservationEvidenceIsRejected() {
        assertThatThrownBy(() -> new EnrichmentPlanStep(ID, EnrichmentPlanStepCode.POSTMATCH_AFTER_FINAL,
                "POSTMATCH", FINAL_OBSERVED_AT, "EXPLICIT_FINAL_STATUS"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
