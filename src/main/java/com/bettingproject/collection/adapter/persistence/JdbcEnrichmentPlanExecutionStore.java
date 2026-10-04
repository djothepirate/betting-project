package com.bettingproject.collection.adapter.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.collection.application.enrichment.EnrichmentPlanExecutionStore;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcEnrichmentPlanExecutionStore implements EnrichmentPlanExecutionStore {
    private final JdbcClient jdbc;

    public JdbcEnrichmentPlanExecutionStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public void lockAdmission(UUID admissionId) {
        jdbc.sql("SELECT id FROM enrichment_daily_admission WHERE id = :id FOR UPDATE")
                .param("id", admissionId).query(UUID.class).optional()
                .orElseThrow(() -> new IllegalStateException("Enrichment admission unavailable"));
    }

    @Override public boolean hasPrematchCompleteLineup(UUID admissionId, Instant kickoffAt) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM provider_enrichment_observation
                    WHERE admission_id = :admission AND family = 'LINEUP'
                      AND observation_state = 'AVAILABLE' AND received_at < :kickoff
                      AND representation_json -> 'assessment' ->> 'status' = 'COMPLETE'
                )
                """)
                .param("admission", admissionId).param("kickoff", utc(kickoffAt))
                .query(Boolean.class).single();
    }

    @Override public Optional<EnrichmentPlanStepStatus> stepStatus(UUID admissionId, EnrichmentPlanStepCode code) {
        return jdbc.sql("SELECT status FROM enrichment_plan_step WHERE admission_id = :admission AND step_code = :code")
                .param("admission", admissionId).param("code", code.name())
                .query(String.class).optional().map(EnrichmentPlanStepStatus::valueOf);
    }

    @Override public boolean markStep(UUID admissionId, EnrichmentPlanStepCode code,
            EnrichmentPlanStepStatus status, String resultCode) {
        if (resultCode != null && !resultCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("invalid step result code");
        }
        int changed = jdbc.sql("""
                UPDATE enrichment_plan_step SET status = :status, result_code = :result
                WHERE admission_id = :admission AND step_code = :code AND status IN ('PLANNED', 'QUEUED')
                """)
                .param("admission", admissionId).param("code", code.name())
                .param("status", status.name()).param("result", resultCode).update();
        return changed == 1;
    }

    @Override public List<EnrichmentPlanStepCode> armPostmatch(UUID admissionId, UUID observationId, Instant finalObservedAt,
            String policyVersion) {
        if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 64
                || policyVersion.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid final-status policy version");
        }
        List<String> armed = jdbc.sql("""
                UPDATE enrichment_plan_step
                SET trigger_observed_at = :observed, trigger_evidence_observation_id = :observation,
                    trigger_policy_version = :policy,
                    scheduled_at = CASE WHEN step_code = 'POSTMATCH_RECHECK_FINAL_PLUS_60'
                        THEN :observed + INTERVAL '60 minutes' ELSE :observed END,
                    result_code = NULL
                WHERE admission_id = :admission
                  AND (step_code = 'POSTMATCH_AFTER_FINAL'
                    OR (step_code = 'POSTMATCH_RECHECK_FINAL_PLUS_60' AND EXISTS (
                        SELECT 1 FROM enrichment_daily_admission admission
                        WHERE admission.id = enrichment_plan_step.admission_id AND admission.priority
                    )))
                  AND status = 'PLANNED' AND trigger_observed_at IS NULL
                RETURNING step_code
                """)
                .param("admission", admissionId).param("observed", utc(finalObservedAt))
                .param("observation", observationId).param("policy", policyVersion)
                .query(String.class).list();
        return armed.stream().map(EnrichmentPlanStepCode::valueOf).toList();
    }

    private static OffsetDateTime utc(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
