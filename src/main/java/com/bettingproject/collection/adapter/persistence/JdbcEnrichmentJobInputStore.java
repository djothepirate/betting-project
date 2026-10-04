package com.bettingproject.collection.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.collection.application.enrichment.EnrichmentJobInput;
import com.bettingproject.collection.application.enrichment.EnrichmentJobInputStore;
import com.bettingproject.collection.application.enrichment.EnrichmentJobRoute;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import com.bettingproject.operations.domain.JobModel.Type;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcEnrichmentJobInputStore implements EnrichmentJobInputStore {
    private final JdbcClient jdbc;

    public JdbcEnrichmentJobInputStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public void insertIfAbsentAndResolve(EnrichmentJobInput input) {
        int inserted = jdbc.sql("""
                INSERT INTO enrichment_job_input (job_id, job_type, admission_id, step_id, budget_window_id,
                    registry_sha256, route_sha256, created_at)
                VALUES (:job, :type, :admission, :step, :window, :registry, :route, :created)
                ON CONFLICT (step_id) DO NOTHING
                """)
                .param("job", input.jobId()).param("type", input.jobType().name())
                .param("admission", input.admissionId()).param("step", input.stepId())
                .param("window", input.budgetWindowId()).param("registry", input.registrySha256())
                .param("route", input.routeSha256()).param("created", utc(input.createdAt())).update();
        if (inserted == 1) {
            for (EnrichmentJobRoute route : input.routes()) {
                jdbc.sql("""
                        INSERT INTO enrichment_job_input_route (job_id, family, provider, provider_competition_id,
                            source_season, source_phase, data_type, parser_version)
                        VALUES (:job, :family, :provider, :competition, :season, :phase, :type, :parser)
                        """)
                        .param("job", input.jobId()).param("family", route.family().name())
                        .param("provider", route.capability().provider())
                        .param("competition", route.capability().providerCompetitionId())
                        .param("season", route.capability().sourceSeason()).param("phase", route.capability().sourcePhase())
                        .param("type", route.capability().dataType().name()).param("parser", route.parserVersion()).update();
            }
        }
        EnrichmentJobInput resolved = find(input.jobId()).orElseThrow();
        if (!resolved.equals(input)) { throw new IllegalStateException("Enrichment job input conflict"); }
    }

    @Override public Optional<EnrichmentJobInput> find(UUID jobId) {
        return jdbc.sql("""
                SELECT input.*, step.step_code FROM enrichment_job_input input
                JOIN enrichment_plan_step step ON step.id = input.step_id
                WHERE input.job_id = :job
                """)
                .param("job", jobId).query(this::header).optional().map(this::hydrate);
    }

    private EnrichmentJobInput hydrate(Header header) {
        List<EnrichmentJobRoute> routes = jdbc.sql("""
                SELECT family, provider, provider_competition_id, source_season, source_phase, data_type, parser_version
                FROM enrichment_job_input_route WHERE job_id = :job ORDER BY family
                """)
                .param("job", header.jobId()).query((rs, row) -> new EnrichmentJobRoute(
                        EnrichmentFamily.valueOf(rs.getString("family")),
                        new ProviderCapabilityKey(rs.getString("provider"), rs.getString("provider_competition_id"),
                                rs.getString("source_season"), rs.getString("source_phase"),
                                CapabilityDataType.valueOf(rs.getString("data_type"))),
                        rs.getString("parser_version"))).list();
        return new EnrichmentJobInput(header.jobId(), header.admissionId(), header.stepId(), header.stepCode(),
                header.jobType(), header.windowId(), header.registrySha256(), header.routeSha256(), routes, header.createdAt());
    }

    private Header header(ResultSet rs, int row) throws SQLException {
        return new Header(rs.getObject("job_id", UUID.class), rs.getObject("admission_id", UUID.class),
                rs.getObject("step_id", UUID.class), EnrichmentPlanStepCode.valueOf(rs.getString("step_code")),
                Type.valueOf(rs.getString("job_type")), rs.getObject("budget_window_id", UUID.class),
                rs.getString("registry_sha256"), rs.getString("route_sha256"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime utc(java.time.Instant value) { return value.atOffset(ZoneOffset.UTC); }

    private record Header(UUID jobId, UUID admissionId, UUID stepId, EnrichmentPlanStepCode stepCode,
            Type jobType, UUID windowId, String registrySha256, String routeSha256, java.time.Instant createdAt) { }
}
