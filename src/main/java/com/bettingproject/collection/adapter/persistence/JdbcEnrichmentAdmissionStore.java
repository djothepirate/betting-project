package com.bettingproject.collection.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.collection.application.control.EnrichmentAdmissionStore;
import com.bettingproject.enrichment.domain.DailyEnrichmentAdmission;
import com.bettingproject.enrichment.domain.DailyEnrichmentPlan;
import com.bettingproject.enrichment.domain.EnrichmentPlanStep;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcEnrichmentAdmissionStore implements EnrichmentAdmissionStore {
    private final JdbcClient jdbc;

    public JdbcEnrichmentAdmissionStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public void lockIdempotencyKey(String key) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "betting-project:enrichment-daily-plan:idempotency:v1:" + key)
                .query((rs, row) -> Boolean.TRUE).single();
    }

    @Override public void lockDate(LocalDate utcDate) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "betting-project:enrichment-daily-plan:v1:" + utcDate)
                .query((rs, row) -> Boolean.TRUE).single();
    }

    @Override public Optional<DailyEnrichmentPlan> findByIdempotencyKey(String key) {
        return jdbc.sql("SELECT * FROM enrichment_daily_plan WHERE idempotency_key = :key")
                .param("key", key).query(this::header).optional().map(this::hydrate);
    }

    @Override public Optional<DailyEnrichmentPlan> findByDate(LocalDate date) {
        return jdbc.sql("SELECT * FROM enrichment_daily_plan WHERE competition_date = :date")
                .param("date", date).query(this::header).optional().map(this::hydrate);
    }

    @Override public boolean insert(DailyEnrichmentPlan plan) {
        int inserted = jdbc.sql("""
                INSERT INTO enrichment_daily_plan (id, idempotency_key, command_sha256, budget_window_id,
                    competition_date, registry_sha256, estimated_calls_per_fixture, selected_fixture_count,
                    estimated_calls, evaluated_at, created_at)
                VALUES (:id, :key, :commandHash, :window, :date, :registryHash, :callsPerFixture,
                    :fixtureCount, :calls, :evaluatedAt, :createdAt)
                ON CONFLICT (competition_date) DO NOTHING
                """)
                .param("id", plan.id()).param("key", plan.idempotencyKey()).param("commandHash", plan.commandSha256())
                .param("window", plan.budgetWindowId()).param("date", plan.competitionDate())
                .param("registryHash", plan.registrySha256()).param("callsPerFixture", plan.estimatedCallsPerFixture())
                .param("fixtureCount", plan.admissions().size()).param("calls", plan.estimatedCalls())
                .param("evaluatedAt", utc(plan.evaluatedAt())).param("createdAt", utc(plan.createdAt())).update();
        if (inserted == 0) { return false; }
        for (DailyEnrichmentAdmission admission : plan.admissions()) {
            jdbc.sql("""
                    INSERT INTO enrichment_daily_admission (id, plan_id, canonical_fixture_id, admission_order,
                        priority, kickoff_at, estimated_calls, created_at)
                    VALUES (:id, :plan, :fixture, :order, :priority, :kickoff, :calls, :created)
                    """)
                    .param("id", admission.id()).param("plan", plan.id()).param("fixture", admission.canonicalFixtureId())
                    .param("order", admission.order()).param("priority", admission.priority())
                    .param("kickoff", utc(admission.kickoffAt())).param("calls", admission.estimatedCalls())
                    .param("created", utc(plan.createdAt())).update();
            for (EnrichmentPlanStep step : admission.steps()) {
                jdbc.sql("""
                        INSERT INTO enrichment_plan_step (id, admission_id, step_code, family_scope,
                            scheduled_at, condition_code, trigger_observed_at, trigger_evidence_observation_id,
                            status, created_at)
                        VALUES (:id, :admission, :code, :family, :scheduled, :condition,
                            :triggerObservedAt, :triggerEvidence, 'PLANNED', :created)
                        """)
                        .param("id", step.id()).param("admission", admission.id()).param("code", step.code().name())
                        .param("family", step.familyScope()).param("scheduled", utc(step.scheduledAt()))
                        .param("condition", step.conditionCode()).param("triggerObservedAt", utc(step.triggerObservedAt()))
                        .param("triggerEvidence", step.triggerObservationId())
                        .param("created", utc(plan.createdAt())).update();
            }
        }
        return true;
    }

    private PlanHeader header(ResultSet rs, int row) throws SQLException {
        return new PlanHeader(rs.getObject("id", UUID.class), rs.getString("idempotency_key"),
                rs.getString("command_sha256"), rs.getObject("budget_window_id", UUID.class),
                rs.getObject("competition_date", LocalDate.class), rs.getString("registry_sha256"),
                rs.getInt("estimated_calls_per_fixture"), instant(rs, "evaluated_at"), instant(rs, "created_at"));
    }

    private DailyEnrichmentPlan hydrate(PlanHeader header) {
        List<AdmissionHeader> headers = jdbc.sql("""
                SELECT id, canonical_fixture_id, admission_order, priority, kickoff_at, estimated_calls
                FROM enrichment_daily_admission WHERE plan_id = :plan ORDER BY admission_order
                """).param("plan", header.id()).query((rs, row) -> new AdmissionHeader(
                        rs.getObject("id", UUID.class), rs.getObject("canonical_fixture_id", UUID.class),
                        rs.getInt("admission_order"), rs.getBoolean("priority"), instant(rs, "kickoff_at"),
                        rs.getInt("estimated_calls"))).list();
        List<DailyEnrichmentAdmission> admissions = headers.stream().map(this::hydrateAdmission).toList();
        return new DailyEnrichmentPlan(header.id(), header.key(), header.commandHash(), header.windowId(), header.date(),
                header.registryHash(), header.callsPerFixture(), header.evaluatedAt(), header.createdAt(), admissions);
    }

    private DailyEnrichmentAdmission hydrateAdmission(AdmissionHeader admission) {
        List<EnrichmentPlanStep> steps = jdbc.sql("""
                        SELECT id, step_code, family_scope, scheduled_at, condition_code,
                            trigger_observed_at, trigger_evidence_observation_id
                        FROM enrichment_plan_step WHERE admission_id = :admission ORDER BY step_code
                        """).param("admission", admission.id()).query((stepRs, stepRow) -> new EnrichmentPlanStep(
                        stepRs.getObject("id", UUID.class), EnrichmentPlanStepCode.valueOf(stepRs.getString("step_code")),
                        stepRs.getString("family_scope"), instant(stepRs, "scheduled_at"), stepRs.getString("condition_code"),
                        instant(stepRs, "trigger_observed_at"), stepRs.getObject("trigger_evidence_observation_id", UUID.class))).list();
        return new DailyEnrichmentAdmission(admission.id(), admission.fixtureId(), admission.order(), admission.priority(),
                admission.kickoffAt(), admission.estimatedCalls(), steps);
    }

    private static OffsetDateTime utc(Instant instant) { return instant == null ? null : instant.atOffset(ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
    private record PlanHeader(UUID id, String key, String commandHash, UUID windowId, LocalDate date,
            String registryHash, int callsPerFixture, Instant evaluatedAt, Instant createdAt) { }
    private record AdmissionHeader(UUID id, UUID fixtureId, int order, boolean priority, Instant kickoffAt, int estimatedCalls) { }
}
