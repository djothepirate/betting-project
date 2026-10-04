package com.bettingproject.collection.adapter.persistence;

import static com.bettingproject.shared.adapter.persistence.ReadSql.time;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.application.enrichment.EnrichmentQualityQueryPort;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Static SQL projections for the bounded daily quality view; never reads raw_snapshot.payload. */
@Repository
@Profile("control-api")
public class JdbcEnrichmentQualityQueryAdapter implements EnrichmentQualityQueryPort {
    private static final String DAY_ADMISSIONS = """
            SELECT admission.id
            FROM enrichment_daily_plan plan
            JOIN enrichment_daily_admission admission ON admission.plan_id = plan.id
            WHERE plan.competition_date = :date
            """;
    private static final String PLAN_STEPS = """
            SELECT step.id, step.admission_id, step.step_code, step.family_scope, step.condition_code,
                step.status, step.result_code, step.scheduled_at, step.trigger_observed_at,
                step.trigger_evidence_observation_id, step.trigger_policy_version
            FROM enrichment_plan_step step
            WHERE step.admission_id IN (
            """ + DAY_ADMISSIONS + """
            )
            ORDER BY (SELECT admission_order FROM enrichment_daily_admission WHERE id = step.admission_id),
                CASE step.step_code
                    WHEN 'LINEUP_T_MINUS_30' THEN 1 WHEN 'LINEUP_T_MINUS_15' THEN 2
                    WHEN 'DETAIL_AT_KICKOFF' THEN 3 WHEN 'DETAIL_PLUS_45' THEN 4
                    WHEN 'POSTMATCH_AFTER_FINAL' THEN 5 ELSE 6 END,
                step.id
            LIMIT 43
            """;

    private final JdbcClient jdbc;

    public JdbcEnrichmentQualityQueryAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<DailyPlan> dailyPlan(LocalDate date) {
        return jdbc.sql("""
                SELECT id, competition_date, budget_window_id, btrim(registry_sha256) AS registry_sha256,
                    selected_fixture_count, evaluated_at, created_at
                FROM enrichment_daily_plan WHERE competition_date = :date
                """)
                .param("date", date)
                .query((rs, row) -> new DailyPlan(uuid(rs, "id"), rs.getObject("competition_date", LocalDate.class),
                        uuid(rs, "budget_window_id"), rs.getString("registry_sha256"), rs.getInt("selected_fixture_count"),
                        time(rs, "evaluated_at"), time(rs, "created_at")))
                .optional();
    }

    @Override
    public List<AdmittedFixture> admittedFixtures(LocalDate date) {
        return jdbc.sql("""
                SELECT admission.id AS admission_id, fixture.id AS fixture_id, admission.admission_order,
                    admission.priority, admission.kickoff_at, competition.canonical_name AS competition_name,
                    season.season_label, fixture.phase, fixture.status,
                    home.canonical_name AS home_team, away.canonical_name AS away_team
                FROM enrichment_daily_plan plan
                JOIN enrichment_daily_admission admission ON admission.plan_id = plan.id
                JOIN canonical_fixture fixture ON fixture.id = admission.canonical_fixture_id
                JOIN canonical_competition competition ON competition.id = fixture.competition_id
                JOIN canonical_season season ON season.id = fixture.season_id
                JOIN canonical_team home ON home.id = fixture.home_team_id
                JOIN canonical_team away ON away.id = fixture.away_team_id
                WHERE plan.competition_date = :date
                ORDER BY admission.admission_order, admission.id
                LIMIT 8
                """)
                .param("date", date)
                .query((rs, row) -> new AdmittedFixture(uuid(rs, "admission_id"), uuid(rs, "fixture_id"),
                        rs.getInt("admission_order"), rs.getBoolean("priority"), time(rs, "kickoff_at"),
                        rs.getString("competition_name"), rs.getString("season_label"), rs.getString("phase"),
                        rs.getString("status"), rs.getString("home_team"), rs.getString("away_team")))
                .list();
    }

    @Override
    public List<PlannedStep> planSteps(LocalDate date) {
        return jdbc.sql(PLAN_STEPS)
                .param("date", date)
                .query((rs, row) -> new PlannedStep(uuid(rs, "id"), uuid(rs, "admission_id"),
                        rs.getString("step_code"), rs.getString("family_scope"), rs.getString("condition_code"),
                        rs.getString("status"), rs.getString("result_code"), time(rs, "scheduled_at"),
                        time(rs, "trigger_observed_at"), uuid(rs, "trigger_evidence_observation_id"),
                        rs.getString("trigger_policy_version")))
                .list();
    }

    @Override
    public List<LatestObservation> latestObservations(LocalDate date) {
        return jdbc.sql("""
                SELECT DISTINCT ON (observation.admission_id, observation.family)
                    observation.id, observation.admission_id, observation.canonical_fixture_id,
                    observation.family, observation.observation_state, observation.provider,
                    observation.provider_fixture_id, observation.logical_competition,
                    observation.logical_season, observation.logical_phase,
                    observation.source_season_reference, observation.source_phase_reference,
                    btrim(observation.payload_sha256) AS payload_sha256,
                    btrim(observation.representation_sha256) AS representation_sha256,
                    observation.parser_version, observation.raw_snapshot_id,
                    observation.requested_at, observation.received_at, observation.source_observed_at,
                    CASE WHEN observation.family = 'LINEUP'
                              AND observation.representation_json #>> '{assessment,status}' IN
                                  ('ABSENT','INCOMPLETE','COMPLETE','COMPLETE_LATE','UNKNOWN')
                         THEN observation.representation_json #>> '{assessment,status}' END AS lineup_assessment_status
                FROM provider_enrichment_observation observation
                JOIN enrichment_daily_admission admission ON admission.id = observation.admission_id
                JOIN enrichment_daily_plan plan ON plan.id = admission.plan_id
                WHERE plan.competition_date = :date
                ORDER BY observation.admission_id, observation.family,
                    observation.received_at DESC, observation.id DESC
                LIMIT 36
                """)
                .param("date", date)
                .query((rs, row) -> new LatestObservation(uuid(rs, "id"), uuid(rs, "admission_id"),
                        uuid(rs, "canonical_fixture_id"), EnrichmentFamily.valueOf(rs.getString("family")),
                        rs.getString("observation_state"), rs.getString("provider"), rs.getString("provider_fixture_id"),
                        rs.getString("logical_competition"), rs.getString("logical_season"), rs.getString("logical_phase"),
                        rs.getString("source_season_reference"), rs.getString("source_phase_reference"),
                        rs.getString("payload_sha256"), rs.getString("representation_sha256"), rs.getString("parser_version"),
                        uuid(rs, "raw_snapshot_id"), time(rs, "requested_at"), time(rs, "received_at"),
                        time(rs, "source_observed_at"), rs.getString("lineup_assessment_status")))
                .list();
    }

    @Override
    public List<FindingCount> findingCounts(LocalDate date) {
        return jdbc.sql("""
                SELECT observation.admission_id, finding.issue_code, finding.entity_scope,
                    count(*) AS finding_count, max(finding.detected_at) AS last_detected_at
                FROM enrichment_quality_finding finding
                JOIN provider_enrichment_observation observation ON observation.id = finding.enrichment_observation_id
                JOIN enrichment_daily_admission admission ON admission.id = observation.admission_id
                JOIN enrichment_daily_plan plan ON plan.id = admission.plan_id
                WHERE plan.competition_date = :date
                GROUP BY observation.admission_id, finding.issue_code, finding.entity_scope
                ORDER BY observation.admission_id, finding.issue_code, finding.entity_scope
                LIMIT 246
                """)
                .param("date", date)
                .query((rs, row) -> new FindingCount(uuid(rs, "admission_id"), rs.getString("issue_code"),
                        rs.getString("entity_scope"), rs.getLong("finding_count"), time(rs, "last_detected_at")))
                .list();
    }

    @Override
    public List<LatestAttempt> latestAttempts(LocalDate date) {
        return jdbc.sql("""
                WITH ranked AS (
                    SELECT attempt.*, row_number() OVER (
                        PARTITION BY attempt.admission_id, attempt.step_code, attempt.family
                        ORDER BY attempt.created_at DESC, attempt.id DESC) AS rank
                    FROM enrichment_collection_attempt attempt
                    JOIN enrichment_daily_admission admission ON admission.id = attempt.admission_id
                    JOIN enrichment_daily_plan plan ON plan.id = admission.plan_id
                    WHERE plan.competition_date = :date
                )
                SELECT id, admission_id, step_code, family, provider, logical_endpoint, state,
                    CASE WHEN reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$' THEN reason_code END AS safe_reason_code,
                    http_status, connector_version, parser_version, raw_snapshot_id,
                    btrim(payload_sha256) AS payload_sha256, quota_remaining,
                    requested_at, received_at, result_recorded_at
                FROM ranked WHERE rank = 1
                ORDER BY admission_id, step_code, family
                LIMIT 211
                """)
                .param("date", date)
                .query((rs, row) -> new LatestAttempt(uuid(rs, "id"), uuid(rs, "admission_id"),
                        rs.getString("step_code"), EnrichmentFamily.valueOf(rs.getString("family")),
                        rs.getString("provider"), rs.getString("logical_endpoint"), rs.getString("state"),
                        rs.getString("safe_reason_code"), rs.getObject("http_status", Integer.class),
                        rs.getString("connector_version"), rs.getString("parser_version"),
                        uuid(rs, "raw_snapshot_id"), rs.getString("payload_sha256"),
                        rs.getObject("quota_remaining", Long.class), time(rs, "requested_at"),
                        time(rs, "received_at"), time(rs, "result_recorded_at")))
                .list();
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
