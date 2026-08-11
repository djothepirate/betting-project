CREATE TABLE canonical_season (
    id UUID PRIMARY KEY,
    competition_id UUID NOT NULL REFERENCES canonical_competition(id),
    season_label VARCHAR(64) NOT NULL,
    starts_on DATE,
    ends_on DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_canonical_season_dates CHECK (
        starts_on IS NULL OR ends_on IS NULL OR starts_on <= ends_on
    ),
    CONSTRAINT uq_canonical_season UNIQUE (competition_id, season_label)
);

ALTER TABLE canonical_fixture
    ADD COLUMN season_id UUID REFERENCES canonical_season(id),
    ADD COLUMN phase VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE canonical_fixture
    DROP CONSTRAINT uq_canonical_fixture;

ALTER TABLE canonical_fixture
    ADD CONSTRAINT uq_canonical_fixture UNIQUE (
        competition_id, season_id, home_team_id, away_team_id, kickoff_at
    ),
    ADD CONSTRAINT ck_canonical_fixture_status CHECK (
        status IN ('SCHEDULED', 'POSTPONED', 'CANCELLED', 'FINISHED')
    );

ALTER TABLE provider_mapping
    ADD CONSTRAINT ck_provider_mapping_entity_type CHECK (
        entity_type IN ('COMPETITION', 'TEAM', 'FIXTURE')
    ),
    ADD CONSTRAINT ck_provider_mapping_canonical_entity CHECK (
        (mapping_status = 'CONFIRMED' AND canonical_entity_id IS NOT NULL)
        OR (mapping_status <> 'CONFIRMED' AND canonical_entity_id IS NULL)
    );

CREATE TABLE fixture_observation (
    id UUID PRIMARY KEY,
    raw_snapshot_id UUID NOT NULL REFERENCES raw_snapshot(id),
    canonical_fixture_id UUID REFERENCES canonical_fixture(id),
    provider VARCHAR(64) NOT NULL,
    provider_fixture_id VARCHAR(200) NOT NULL,
    provider_competition_id VARCHAR(200),
    provider_home_team_id VARCHAR(200),
    provider_away_team_id VARCHAR(200),
    source_kickoff_at TIMESTAMPTZ NOT NULL,
    source_status VARCHAR(32) NOT NULL,
    source_phase VARCHAR(64) NOT NULL DEFAULT '',
    normalization_status VARCHAR(24) NOT NULL,
    reason_code VARCHAR(64),
    observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_fixture_observation_status CHECK (
        normalization_status IN ('NORMALIZED', 'BLOCKED', 'REJECTED')
    ),
    CONSTRAINT uq_fixture_observation UNIQUE (raw_snapshot_id, provider_fixture_id)
);

CREATE INDEX ix_fixture_observation_fixture
    ON fixture_observation (canonical_fixture_id, observed_at);

CREATE TABLE normalization_anomaly (
    id UUID PRIMARY KEY,
    raw_snapshot_id UUID NOT NULL REFERENCES raw_snapshot(id),
    provider VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    provider_entity_id VARCHAR(200) NOT NULL,
    anomaly_code VARCHAR(64) NOT NULL,
    details VARCHAR(2000) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT ck_normalization_anomaly_entity_type CHECK (
        entity_type IN ('COMPETITION', 'TEAM', 'FIXTURE', 'SNAPSHOT')
    ),
    CONSTRAINT ck_normalization_anomaly_status CHECK (
        status IN ('OPEN', 'RESOLVED', 'IGNORED')
    ),
    CONSTRAINT uq_normalization_anomaly UNIQUE (
        raw_snapshot_id, entity_type, provider_entity_id, anomaly_code
    )
);

CREATE INDEX ix_normalization_anomaly_open
    ON normalization_anomaly (status, provider, anomaly_code, created_at);
