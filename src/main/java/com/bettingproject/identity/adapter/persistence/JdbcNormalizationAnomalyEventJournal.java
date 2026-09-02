package com.bettingproject.identity.adapter.persistence;

import java.time.ZoneOffset;

import com.bettingproject.identity.application.NormalizationAnomalyEventJournal;
import com.bettingproject.identity.domain.NormalizationAnomalyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcNormalizationAnomalyEventJournal implements NormalizationAnomalyEventJournal {

    private final JdbcClient jdbcClient;

    public JdbcNormalizationAnomalyEventJournal(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void append(NormalizationAnomalyEvent event) {
        jdbcClient.sql("""
                INSERT INTO normalization_anomaly_event (
                    id, normalization_anomaly_id, event_type, previous_status,
                    resulting_status, fixture_application_log_id, details, created_at
                ) VALUES (
                    :id, :normalizationAnomalyId, :eventType, :previousStatus,
                    :resultingStatus, :fixtureApplicationLogId, :details, :createdAt
                )
                """)
                .param("id", event.id())
                .param("normalizationAnomalyId", event.anomalyId())
                .param("eventType", event.eventType().name())
                .param("previousStatus", event.previousStatus() == null
                        ? null
                        : event.previousStatus().name())
                .param("resultingStatus", event.resultingStatus().name())
                .param("fixtureApplicationLogId", event.fixtureApplicationLogId())
                .param("details", event.details())
                .param("createdAt", event.createdAt().atOffset(ZoneOffset.UTC))
                .update();
    }
}
