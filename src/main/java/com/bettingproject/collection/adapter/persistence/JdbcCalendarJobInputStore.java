package com.bettingproject.collection.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import com.bettingproject.collection.application.calendar.*;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.operations.domain.JobModel.Type;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcCalendarJobInputStore implements CalendarJobInputStore {
    private final JdbcClient jdbc;
    public JdbcCalendarJobInputStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public void insertIfAbsent(CalendarJobInput input) {
        var command = input.discovery();
        var capability = command == null ? null : command.capability();
        jdbc.sql("""
                INSERT INTO calendar_job_input (job_id, job_type, window_id, provider, provider_competition_id,
                    source_season, source_phase, collection_date, season_start_year, registry_sha256,
                    replay_page_id, parser_version)
                VALUES (:job, :type, :window, :provider, :competition, :season, :phase, :date, :year, :registry, :page, :parser)
                ON CONFLICT (job_id) DO NOTHING
                """).param("job", input.jobId()).param("type", input.type().name())
                .param("window", command == null ? null : command.windowId())
                .param("provider", capability == null ? null : capability.provider())
                .param("competition", capability == null ? null : capability.providerCompetitionId())
                .param("season", capability == null ? null : capability.sourceSeason())
                .param("phase", capability == null ? null : capability.sourcePhase())
                .param("date", command == null ? null : command.date())
                .param("year", command == null ? null : command.seasonStartYear())
                .param("registry", input.registrySha256()).param("page", input.replayPageId())
                .param("parser", input.parserVersion()).update();
        if (!find(input.jobId()).orElseThrow().equals(input)) {
            throw new IllegalStateException("Calendar job input conflict");
        }
    }

    @Override public Optional<CalendarJobInput> find(UUID jobId) {
        return jdbc.sql("SELECT * FROM calendar_job_input WHERE job_id = :id").param("id", jobId)
                .query((rs, n) -> {
                    Type type = Type.valueOf(rs.getString("job_type"));
                    CalendarCollectionCommand command = type != Type.CALENDAR_DISCOVERY ? null :
                            new CalendarCollectionCommand(jobId, rs.getObject("window_id", UUID.class),
                                new ProviderCapabilityKey(rs.getString("provider"), rs.getString("provider_competition_id"),
                                    rs.getString("source_season"), rs.getString("source_phase"), CapabilityDataType.CALENDAR),
                                rs.getDate("collection_date").toLocalDate(), rs.getInt("season_start_year"));
                    return new CalendarJobInput(jobId, type, command, rs.getObject("replay_page_id", UUID.class),
                            rs.getString("parser_version"), rs.getString("registry_sha256"));
                }).optional();
    }
}
