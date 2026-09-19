package com.bettingproject.operations.adapter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.bettingproject.operations.application.jobs.JobQueryPort;
import com.bettingproject.operations.domain.JobModel;
import com.bettingproject.shared.adapter.persistence.ReadSql;
import com.bettingproject.shared.application.ReadPage;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import static com.bettingproject.shared.adapter.persistence.ReadSql.time;

@Repository
@Profile("control-api")
public class JdbcJobQueryAdapter implements JobQueryPort {
    private final JdbcClient jdbc;
    public JdbcJobQueryAdapter(JdbcClient jdbc) { this.jdbc=jdbc; }
    private static final String JOBS = """
            SELECT j.id, j.job_type, j.status, j.attempts, j.max_attempts, j.execution_version,
                j.scheduled_at, j.next_run_at, j.lease_until, j.last_error_code, j.created_at,
                j.updated_at, o.status AS outbox_status
            FROM persistent_job j LEFT JOIN outbox_message o
                ON o.idempotency_key='collection-job:' || j.id::text AND o.destination='COLLECTION_JOB'
            """;
    public List<JobView> jobs(JobModel.Type type, JobModel.Status state, ReadPage.Request page) {
        return new ReadSql(JOBS).condition("j.managed_collection").filter("j.job_type",type).filter("j.status",state)
                .page("j.created_at","j.id",page).statement(jdbc).query(this::mapJob).list();
    }
    public Optional<JobView> job(UUID id) {
        return new ReadSql(JOBS).condition("j.managed_collection").filter("j.id",id).statement(jdbc).query(this::mapJob).optional();
    }
    public List<EventView> events(UUID jobId, ReadPage.Request page) {
        return new ReadSql("""
                SELECT e.id, e.job_id, e.attempt_number, e.execution_version, e.event_type, e.reason_code, e.created_at
                FROM collection_job_event e JOIN persistent_job j ON j.id=e.job_id
                """).condition("j.managed_collection").filter("e.job_id",jobId).page("e.created_at","e.id",page)
                .statement(jdbc).query((rs,n) -> new EventView(rs.getObject("id",UUID.class),rs.getObject("job_id",UUID.class),
                    rs.getInt("attempt_number"),rs.getLong("execution_version"),rs.getString("event_type"),
                    rs.getString("reason_code"),time(rs,"created_at"))).list();
    }
    private JobView mapJob(ResultSet rs,int n) throws SQLException {
        return new JobView(rs.getObject("id",UUID.class),JobModel.Type.valueOf(rs.getString("job_type")),
            JobModel.Status.valueOf(rs.getString("status")),rs.getInt("attempts"),rs.getInt("max_attempts"),
            rs.getLong("execution_version"),time(rs,"scheduled_at"),time(rs,"next_run_at"),time(rs,"lease_until"),
            rs.getString("last_error_code"),rs.getString("outbox_status"),time(rs,"created_at"),time(rs,"updated_at"));
    }
}
