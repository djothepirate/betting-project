package com.bettingproject.enrichment.domain;

/** Bounded autonomous collection milestones; none of these is itself an HTTP authorization. */
public enum EnrichmentPlanStepCode {
    LINEUP_T_MINUS_30,
    LINEUP_T_MINUS_15,
    DETAIL_AT_KICKOFF,
    DETAIL_PLUS_45,
    POSTMATCH_AFTER_FINAL,
    POSTMATCH_RECHECK_FINAL_PLUS_60
}
