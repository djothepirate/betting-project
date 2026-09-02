package com.bettingproject.catalog.adapter.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.bettingproject.catalog.application.FixtureApplicationJournal;
import com.bettingproject.catalog.domain.FixtureApplicationLog;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcFixtureApplicationJournal implements FixtureApplicationJournal {

    private final JdbcClient jdbcClient;

    public JdbcFixtureApplicationJournal(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void append(FixtureApplicationLog applicationLog) {
        jdbcClient.sql("""
                INSERT INTO fixture_application_log (
                    id, fixture_observation_id, canonical_fixture_id,
                    previous_authority_observation_id, outcome, reason_code,
                    authority_role, policy_version, evaluated_at
                ) VALUES (
                    :id, :fixtureObservationId, :canonicalFixtureId,
                    :previousAuthorityObservationId, :outcome, :reasonCode,
                    :authorityRole, :policyVersion, :evaluatedAt
                )
                """)
                .param("id", applicationLog.id())
                .param("fixtureObservationId", applicationLog.fixtureObservationId())
                .param("canonicalFixtureId", applicationLog.canonicalFixtureId())
                .param("previousAuthorityObservationId", applicationLog.previousAuthorityObservationId())
                .param("outcome", applicationLog.outcome().name())
                .param("reasonCode", applicationLog.reasonCode())
                .param("authorityRole", applicationLog.authorityRole() == null
                        ? null
                        : applicationLog.authorityRole().name())
                .param("policyVersion", applicationLog.policyVersion())
                .param("evaluatedAt", utc(applicationLog.evaluatedAt()))
                .update();
    }

    private OffsetDateTime utc(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
