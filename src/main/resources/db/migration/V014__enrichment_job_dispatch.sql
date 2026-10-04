-- ENR-002 lot 4: bounded executable enrichment milestones, exact routes and schedule outcomes.
-- No provider capability, account, budget window, or real collection is activated here.

ALTER TABLE persistent_job DROP CONSTRAINT ck_collection_job_execution;
ALTER TABLE persistent_job ADD CONSTRAINT ck_collection_job_execution CHECK (
    execution_version >= 1 AND (NOT managed_collection OR (
        job_type IN ('CALENDAR_DISCOVERY', 'PREMATCH_ENRICHMENT', 'POSTMATCH_ENRICHMENT',
            'POSTMATCH_RECHECK', 'REPLAY_NORMALIZATION')
        AND command_sha256 IS NOT NULL AND command_sha256 ~ '^[0-9a-f]{64}$'
        AND scheduled_at IS NOT NULL AND max_attempts BETWEEN 1 AND 10 AND max_attempts IS NOT NULL
        AND attempts <= max_attempts
        AND ((status = 'RUNNING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
          OR (status <> 'RUNNING' AND lease_token IS NULL AND lease_until IS NULL))
    ))
);

ALTER TABLE enrichment_plan_step ADD COLUMN result_code VARCHAR(64);
ALTER TABLE enrichment_plan_step ADD COLUMN trigger_policy_version VARCHAR(64);
ALTER TABLE enrichment_plan_step DROP CONSTRAINT ck_enrichment_plan_step_status;
ALTER TABLE enrichment_plan_step ADD CONSTRAINT ck_enrichment_plan_step_status CHECK (
    status IN ('PLANNED', 'QUEUED', 'MISSED_WINDOW', 'SKIPPED', 'COMPLETED')
);
ALTER TABLE enrichment_plan_step ADD CONSTRAINT ck_enrichment_plan_step_result CHECK (
    result_code IS NULL OR result_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
);
ALTER TABLE enrichment_plan_step ADD CONSTRAINT ck_enrichment_plan_step_final_policy CHECK (
    (trigger_observed_at IS NULL) = (trigger_policy_version IS NULL)
    AND (trigger_policy_version IS NULL OR
        (trigger_policy_version = btrim(trigger_policy_version) AND trigger_policy_version <> ''
            AND trigger_policy_version !~ '[[:cntrl:]]'))
);

ALTER TABLE enrichment_collection_attempt DROP CONSTRAINT ck_enrichment_attempt_state;
ALTER TABLE enrichment_collection_attempt ADD CONSTRAINT ck_enrichment_attempt_state CHECK (state IN (
    'RESERVED', 'COMMITTED_FOR_SEND', 'RECEIVED', 'HTTP_ERROR', 'UNCERTAIN',
    'RESPONSE_TOO_LARGE', 'SECRET_ECHO', 'RESPONSE_INTERRUPTED', 'RELEASED'
));
ALTER TABLE enrichment_collection_attempt DROP CONSTRAINT ck_enrichment_attempt_response;
ALTER TABLE enrichment_collection_attempt ADD CONSTRAINT ck_enrichment_attempt_response CHECK (
    (raw_snapshot_id IS NULL) = (payload_sha256 IS NULL)
    AND (payload_sha256 IS NULL OR payload_sha256 ~ '^[0-9a-f]{64}$')
    AND (http_status IS NULL OR http_status BETWEEN 100 AND 599)
    AND (quota_remaining IS NULL OR quota_remaining >= 0)
    AND ((state IN ('RECEIVED', 'HTTP_ERROR', 'RESPONSE_TOO_LARGE', 'SECRET_ECHO'))
        = (received_at IS NOT NULL AND result_recorded_at IS NOT NULL))
    AND (state NOT IN ('RESERVED', 'COMMITTED_FOR_SEND', 'RELEASED') OR
        (received_at IS NULL AND result_recorded_at IS NULL AND raw_snapshot_id IS NULL
            AND http_status IS NULL AND quota_remaining IS NULL))
    AND (state <> 'RELEASED' OR reason_code IN ('MISSED_WINDOW', 'LINEUP_ALREADY_COMPLETE'))
    AND (state NOT IN ('RESERVED', 'COMMITTED_FOR_SEND') OR reason_code IS NULL)
    AND (state <> 'RECEIVED' OR (http_status BETWEEN 200 AND 299 AND raw_snapshot_id IS NOT NULL))
    AND (state <> 'HTTP_ERROR' OR (http_status NOT BETWEEN 200 AND 299 AND raw_snapshot_id IS NOT NULL))
    AND (state <> 'RESPONSE_TOO_LARGE' OR (raw_snapshot_id IS NULL AND reason_code = 'RESPONSE_TOO_LARGE'))
    AND (state NOT IN ('SECRET_ECHO', 'TRANSPORT_ERROR', 'UNCERTAIN') OR
        (raw_snapshot_id IS NULL AND http_status IS NULL AND quota_remaining IS NULL AND reason_code IS NOT NULL))
    AND ((state = 'UNCERTAIN') = (reason_code = 'UNCERTAIN_SEND'))
);

CREATE TABLE enrichment_job_input (
    job_id UUID PRIMARY KEY,
    job_type VARCHAR(32) NOT NULL,
    admission_id UUID NOT NULL,
    step_id UUID NOT NULL UNIQUE,
    budget_window_id UUID NOT NULL REFERENCES provider_budget_window(id),
    registry_sha256 CHAR(64) NOT NULL,
    route_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_enrichment_job_type FOREIGN KEY (job_id, job_type)
        REFERENCES persistent_job(id, job_type),
    CONSTRAINT fk_enrichment_job_step FOREIGN KEY (step_id, admission_id)
        REFERENCES enrichment_plan_step(id, admission_id),
    CONSTRAINT ck_enrichment_job_type CHECK (
        job_type IN ('PREMATCH_ENRICHMENT', 'POSTMATCH_ENRICHMENT', 'POSTMATCH_RECHECK')
    ),
    CONSTRAINT ck_enrichment_job_hashes CHECK (
        registry_sha256 ~ '^[0-9a-f]{64}$' AND route_sha256 ~ '^[0-9a-f]{64}$'
    )
);

CREATE TABLE enrichment_job_input_route (
    job_id UUID NOT NULL REFERENCES enrichment_job_input(job_id),
    family VARCHAR(24) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_competition_id VARCHAR(200) NOT NULL,
    source_season VARCHAR(64) NOT NULL,
    source_phase VARCHAR(64) NOT NULL,
    data_type VARCHAR(24) NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    PRIMARY KEY (job_id, family),
    CONSTRAINT ck_enrichment_job_route_family CHECK (
        family IN ('MATCH_DETAIL', 'LINEUP', 'TEAM_STATS', 'EVENTS', 'PLAYER_STATS')
        AND data_type = family
    ),
    CONSTRAINT ck_enrichment_job_route_provider CHECK (
        provider IN ('highlightly', 'football-data.org')
        AND provider = btrim(provider) AND provider_competition_id = btrim(provider_competition_id)
        AND provider_competition_id <> '' AND source_season = btrim(source_season) AND source_season <> ''
        AND source_phase = btrim(source_phase) AND parser_version = btrim(parser_version)
        AND parser_version <> '' AND provider !~ '[[:cntrl:]]'
        AND provider_competition_id !~ '[[:cntrl:]]' AND source_season !~ '[[:cntrl:]]'
        AND source_phase !~ '[[:cntrl:]]' AND parser_version !~ '[[:cntrl:]]'
    )
);

CREATE INDEX ix_enrichment_job_input_admission ON enrichment_job_input(admission_id, created_at, job_id);
