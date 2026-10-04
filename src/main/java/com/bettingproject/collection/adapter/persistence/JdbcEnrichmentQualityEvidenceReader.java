package com.bettingproject.collection.adapter.persistence;

import static com.bettingproject.shared.adapter.persistence.ReadSql.time;

import com.bettingproject.collection.application.enrichment.EnrichmentQualityEvidenceReader;
import com.bettingproject.collection.application.enrichment.StoredEnrichmentQualityEvidence;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Static, bounded quality evidence lookups over parsed JSON only; raw payloads stay inaccessible. */
@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcEnrichmentQualityEvidenceReader implements EnrichmentQualityEvidenceReader {
    private static final String SELECT_EVIDENCE = """
            SELECT observation.id, observation.provider, observation.provider_fixture_id,
                observation.logical_competition, observation.logical_season, observation.logical_phase,
                observation.family, observation.observation_state, observation.parser_version,
                observation.received_at, observation.representation_json::text AS representation_json
            FROM provider_enrichment_observation observation
            """;

    private final JdbcClient jdbc;

    public JdbcEnrichmentQualityEvidenceReader(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<StoredEnrichmentQualityEvidence> latestForProviderFixture(UUID fixtureId,
            String competition, String season, String phase, String provider, String providerFixtureId,
            EnrichmentFamily family, Instant receivedAtOrBefore) {
        return jdbc.sql(SELECT_EVIDENCE + """
                WHERE observation.canonical_fixture_id = :fixture AND observation.logical_competition = :competition
                  AND observation.logical_season = :season AND observation.logical_phase = :phase
                  AND observation.provider = :provider AND observation.provider_fixture_id = :providerFixture
                  AND observation.family = :family AND observation.received_at <= :received
                  AND observation.observation_state IN ('AVAILABLE', 'PARTIAL')
                ORDER BY observation.received_at DESC, observation.id DESC
                LIMIT 1
                """)
                .param("fixture", fixtureId).param("competition", competition).param("season", season)
                .param("phase", phase).param("provider", provider).param("providerFixture", providerFixtureId)
                .param("family", family.name()).param("received", utc(receivedAtOrBefore))
                .query(this::map).optional();
    }

    @Override
    public List<StoredEnrichmentQualityEvidence> latestPerOtherProvider(UUID fixtureId,
            String competition, String season, String phase, String excludedProvider,
            EnrichmentFamily family, Instant receivedAtOrBefore) {
        return jdbc.sql("""
                WITH ranked AS (
                    SELECT observation.id, observation.provider, observation.provider_fixture_id,
                        observation.logical_competition, observation.logical_season, observation.logical_phase,
                        observation.family, observation.observation_state, observation.parser_version,
                        observation.received_at, observation.representation_json::text AS representation_json,
                        row_number() OVER (PARTITION BY observation.provider
                            ORDER BY observation.received_at DESC, observation.id DESC) AS provider_rank
                    FROM provider_enrichment_observation observation
                    WHERE observation.canonical_fixture_id = :fixture
                      AND observation.logical_competition = :competition
                      AND observation.logical_season = :season AND observation.logical_phase = :phase
                      AND observation.provider <> :excludedProvider AND observation.family = :family
                      AND observation.received_at <= :received
                      AND observation.observation_state IN ('AVAILABLE', 'PARTIAL')
                )
                SELECT id, provider, provider_fixture_id, logical_competition, logical_season, logical_phase,
                    family, observation_state, parser_version, received_at, representation_json
                FROM ranked WHERE provider_rank = 1 ORDER BY provider, id LIMIT 4
                """)
                .param("fixture", fixtureId).param("competition", competition).param("season", season)
                .param("phase", phase).param("excludedProvider", excludedProvider)
                .param("family", family.name()).param("received", utc(receivedAtOrBefore))
                .query(this::map).list();
    }

    private StoredEnrichmentQualityEvidence map(ResultSet rs, int row) throws SQLException {
        return new StoredEnrichmentQualityEvidence(rs.getObject("id", UUID.class), rs.getString("provider"),
                rs.getString("provider_fixture_id"), rs.getString("logical_competition"),
                rs.getString("logical_season"), rs.getString("logical_phase"),
                EnrichmentFamily.valueOf(rs.getString("family")),
                EnrichmentObservationState.valueOf(rs.getString("observation_state")),
                rs.getString("parser_version"), time(rs, "received_at"), rs.getString("representation_json"));
    }

    private static OffsetDateTime utc(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
