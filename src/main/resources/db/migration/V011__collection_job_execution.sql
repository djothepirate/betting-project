-- MVP-001 lot 4. Old publication/import jobs and outboxes are not enrolled implicitly.
ALTER TABLE persistent_job ADD COLUMN managed_collection BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE persistent_job ADD COLUMN command_sha256 CHAR(64);
ALTER TABLE persistent_job ADD COLUMN scheduled_at TIMESTAMPTZ;
ALTER TABLE persistent_job ADD COLUMN max_attempts INTEGER;
ALTER TABLE persistent_job ADD COLUMN execution_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE persistent_job ADD COLUMN lease_token UUID;
ALTER TABLE persistent_job ADD COLUMN lease_until TIMESTAMPTZ;
ALTER TABLE persistent_job ADD CONSTRAINT ck_collection_job_execution CHECK (
    execution_version >= 1 AND (NOT managed_collection OR (
        job_type IN ('CALENDAR_DISCOVERY', 'REPLAY_NORMALIZATION')
        AND command_sha256 IS NOT NULL AND command_sha256 ~ '^[0-9a-f]{64}$'
        AND scheduled_at IS NOT NULL AND max_attempts BETWEEN 1 AND 10 AND max_attempts IS NOT NULL
        AND attempts <= max_attempts
        AND ((status = 'RUNNING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
          OR (status <> 'RUNNING' AND lease_token IS NULL AND lease_until IS NULL))
    ))
);
CREATE INDEX ix_collection_job_due ON persistent_job(next_run_at, created_at, id)
    WHERE managed_collection AND status IN ('PENDING', 'RETRY');
CREATE INDEX ix_collection_job_expired ON persistent_job(lease_until, id)
    WHERE managed_collection AND status = 'RUNNING';

CREATE TABLE collection_job_event (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES persistent_job(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 0 AND 10),
    execution_version BIGINT NOT NULL CHECK (execution_version >= 1),
    lease_token UUID,
    event_type VARCHAR(24) NOT NULL CHECK (event_type IN ('ENQUEUED', 'CLAIMED', 'SUCCEEDED', 'RETRY', 'FAILED', 'LEASE_EXPIRED')),
    reason_code VARCHAR(64) CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_collection_job_event UNIQUE(job_id, execution_version, event_type)
);
CREATE INDEX ix_collection_job_history ON collection_job_event(job_id, created_at, id);

CREATE TABLE collection_job_effect (
    job_id UUID NOT NULL REFERENCES persistent_job(id),
    effect_key VARCHAR(200) NOT NULL CHECK (effect_key <> '' AND effect_key = btrim(effect_key) AND effect_key !~ '[[:cntrl:]]'),
    result_code VARCHAR(64) NOT NULL CHECK (result_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(job_id, effect_key)
);

CREATE TABLE calendar_job_input (
    job_id UUID PRIMARY KEY REFERENCES persistent_job(id),
    job_type VARCHAR(32) NOT NULL,
    window_id UUID REFERENCES provider_budget_window(id),
    provider VARCHAR(64),
    provider_competition_id VARCHAR(200),
    source_season VARCHAR(64),
    source_phase VARCHAR(64),
    collection_date DATE,
    season_start_year INTEGER,
    registry_sha256 CHAR(64) NOT NULL CHECK (registry_sha256 ~ '^[0-9a-f]{64}$'),
    replay_page_id UUID REFERENCES calendar_collection_page(id),
    parser_version VARCHAR(64) NOT NULL CHECK (parser_version <> '' AND parser_version = btrim(parser_version) AND parser_version !~ '[[:cntrl:]]'),
    CONSTRAINT ck_calendar_job_input CHECK (
        (job_type = 'CALENDAR_DISCOVERY' AND window_id IS NOT NULL
         AND provider IS NOT NULL AND provider_competition_id IS NOT NULL
         AND source_season IS NOT NULL AND source_phase IS NOT NULL
         AND provider <> '' AND provider_competition_id <> '' AND source_season <> '' AND source_phase <> ''
         AND collection_date IS NOT NULL AND season_start_year IS NOT NULL AND season_start_year BETWEEN 1000 AND 9999
         AND replay_page_id IS NULL)
        OR (job_type = 'REPLAY_NORMALIZATION' AND replay_page_id IS NOT NULL
         AND parser_version IS NOT NULL AND parser_version <> ''
         AND window_id IS NULL AND provider IS NULL AND provider_competition_id IS NULL
         AND source_season IS NULL AND source_phase IS NULL AND collection_date IS NULL AND season_start_year IS NULL)
    )
);

ALTER TABLE persistent_job ADD CONSTRAINT uq_job_execution_type UNIQUE(id, job_type);
ALTER TABLE calendar_job_input ADD CONSTRAINT fk_calendar_job_type
    FOREIGN KEY(job_id, job_type) REFERENCES persistent_job(id, job_type);

-- A lost send has no receive time and no invented empty response snapshot.
ALTER TABLE calendar_collection_page DROP CONSTRAINT ck_calendar_page_response;
ALTER TABLE calendar_collection_page ADD CONSTRAINT ck_calendar_page_response CHECK (
    response_code IN ('PENDING', 'RECEIVED', 'HTTP_ERROR', 'UNCERTAIN_RESPONSE', 'SEND_UNCERTAIN',
        'RESPONSE_TOO_LARGE', 'SECRET_ECHO', 'TIMEOUT', 'TRANSPORT_ERROR', 'RESPONSE_INTERRUPTED')
    AND ((response_code IN ('PENDING', 'SEND_UNCERTAIN')) = (received_at IS NULL))
    AND (response_code NOT IN ('PENDING', 'SEND_UNCERTAIN')
        OR (raw_snapshot_id IS NULL AND http_status IS NULL AND quota_remaining IS NULL))
    AND (response_code <> 'SECRET_ECHO' OR raw_snapshot_id IS NULL)
    AND (response_code NOT IN ('RECEIVED', 'HTTP_ERROR')
        OR (raw_snapshot_id IS NOT NULL AND http_status IS NOT NULL))
    AND (response_code <> 'RECEIVED' OR http_status BETWEEN 200 AND 299)
    AND (response_code <> 'HTTP_ERROR' OR http_status NOT BETWEEN 200 AND 299)
);
