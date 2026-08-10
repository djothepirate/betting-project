CREATE TABLE canonical_competition (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(200) NOT NULL,
    country_code VARCHAR(3),
    competition_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_canonical_competition UNIQUE (canonical_name, country_code)
);

CREATE TABLE canonical_team (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(200) NOT NULL,
    country_code VARCHAR(3),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_canonical_team UNIQUE (canonical_name, country_code)
);

CREATE TABLE canonical_fixture (
    id UUID PRIMARY KEY,
    competition_id UUID NOT NULL REFERENCES canonical_competition(id),
    home_team_id UUID NOT NULL REFERENCES canonical_team(id),
    away_team_id UUID NOT NULL REFERENCES canonical_team(id),
    kickoff_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_fixture_distinct_teams CHECK (home_team_id <> away_team_id),
    CONSTRAINT uq_canonical_fixture UNIQUE (competition_id, home_team_id, away_team_id, kickoff_at)
);

CREATE TABLE provider_mapping (
    id UUID PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    provider_entity_id VARCHAR(200) NOT NULL,
    canonical_entity_id UUID,
    season VARCHAR(32) NOT NULL DEFAULT '',
    phase VARCHAR(64) NOT NULL DEFAULT '',
    confidence NUMERIC(5, 4),
    mapping_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_provider_mapping_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT ck_provider_mapping_status CHECK (mapping_status IN ('CONFIRMED', 'AMBIGUOUS', 'REJECTED')),
    CONSTRAINT uq_provider_mapping UNIQUE (provider, entity_type, provider_entity_id, season, phase)
);

CREATE TABLE raw_snapshot (
    id UUID PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    endpoint VARCHAR(200) NOT NULL,
    requested_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    source_observed_at TIMESTAMPTZ,
    http_status INTEGER,
    latency_ms BIGINT,
    quota_remaining BIGINT,
    payload_sha256 CHAR(64) NOT NULL,
    payload_compression VARCHAR(16) NOT NULL,
    payload BYTEA NOT NULL,
    connector_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_snapshot_http_status CHECK (http_status IS NULL OR (http_status >= 100 AND http_status <= 599)),
    CONSTRAINT ck_snapshot_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT uq_raw_snapshot UNIQUE (provider, endpoint, payload_sha256)
);

CREATE TABLE provider_call_audit (
    id UUID PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    logical_endpoint VARCHAR(200) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ,
    http_status INTEGER,
    latency_ms BIGINT,
    quota_remaining BIGINT,
    payload_sha256 CHAR(64),
    connector_version VARCHAR(64) NOT NULL,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_provider_call_latency CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

CREATE TABLE persistent_job (
    id UUID PRIMARY KEY,
    job_key VARCHAR(240) NOT NULL,
    job_type VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_run_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    input_ref VARCHAR(500),
    output_ref VARCHAR(500),
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_persistent_job_key UNIQUE (job_key),
    CONSTRAINT ck_persistent_job_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_persistent_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'RETRY', 'FAILED', 'CANCELLED'))
);

CREATE INDEX ix_persistent_job_poll
    ON persistent_job (status, next_run_at, created_at);

CREATE TABLE outbox_message (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(300) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    destination VARCHAR(100) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_outbox_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'DELIVERED', 'RETRY', 'FAILED'))
);

CREATE INDEX ix_outbox_poll
    ON outbox_message (status, next_attempt_at, created_at);
