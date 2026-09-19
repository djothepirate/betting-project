package com.bettingproject.catalog.adapter.persistence;

import java.util.List;
import java.util.UUID;
import com.bettingproject.collection.application.control.DailySelectionCandidates;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.selection.DailySelectionPolicy.Candidate;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import static com.bettingproject.shared.adapter.persistence.ReadSql.time;

@Repository
@Profile("control-api")
public class JdbcDailySelectionCandidates implements DailySelectionCandidates {
    private final JdbcClient jdbc;
    public JdbcDailySelectionCandidates(JdbcClient jdbc){this.jdbc=jdbc;}
    public List<Candidate> find(UUID collectionId,ProviderCapability capability,int limit){
        if(limit<1 || limit>1001){throw new IllegalArgumentException("Invalid candidate bound");}
        return jdbc.sql("""
                SELECT DISTINCT f.id, f.kickoff_at, f.status
                FROM canonical_fixture f
                JOIN canonical_season s ON s.id=f.season_id
                JOIN fixture_observation o ON o.id=f.last_authority_observation_id
                JOIN calendar_collection_derivation d ON d.derived_snapshot_id=o.raw_snapshot_id AND d.outcome='APPLIED'
                JOIN calendar_collection_page p ON p.id=d.page_id
                WHERE p.collection_id=:collectionId AND o.provider=:provider
                    AND o.provider_competition_id=:competition AND o.source_season=:sourceSeason AND o.source_phase=:sourcePhase
                    AND s.season_label=:season AND f.phase=:phase
                ORDER BY f.kickoff_at, f.id LIMIT :limit
                """).param("collectionId",collectionId).param("provider",capability.key().provider())
                .param("competition",capability.key().providerCompetitionId()).param("sourceSeason",capability.key().sourceSeason())
                .param("sourcePhase",capability.key().sourcePhase()).param("season",capability.route().season())
                .param("phase",capability.route().phase()).param("limit",limit)
                .query((rs,n)->new Candidate(rs.getObject("id",UUID.class),capability.route().competitionCode(),
                    time(rs,"kickoff_at"),rs.getString("status"))).list();
    }
}
