-- ENR-002 lot 3: one durable audit row per enrichment HTTP attempt and append-only derivations.
-- No provider capability, account, budget window, or call is activated by this migration.

ALTER TABLE enrichment_plan_step
    ADD CONSTRAINT uq_enrichment_plan_step_identity UNIQUE (id, admission_id);

CREATE TABLE enrichment_collection_attempt (
    id UUID PRIMARY KEY,
    admission_id UUID NOT NULL,
    step_id UUID NOT NULL,
    step_code VARCHAR(40) NOT NULL,
    canonical_fixture_id UUID NOT NULL,
    budget_window_id UUID NOT NULL,
    budget_intent_id UUID NOT NULL UNIQUE,
    audit_id UUID NOT NULL UNIQUE,
    provider VARCHAR(64) NOT NULL,
    provider_competition_id VARCHAR(200) NOT NULL,
    source_season VARCHAR(64) NOT NULL,
    source_phase VARCHAR(64) NOT NULL,
    data_type VARCHAR(24) NOT NULL,
    logical_competition VARCHAR(64) NOT NULL,
    logical_season VARCHAR(64) NOT NULL,
    logical_phase VARCHAR(64) NOT NULL,
    provider_fixture_id VARCHAR(200) NOT NULL,
    family VARCHAR(24) NOT NULL,
    logical_endpoint VARCHAR(200) NOT NULL,
    connector_version VARCHAR(64) NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    raw_snapshot_id UUID,
    payload_sha256 CHAR(64),
    kickoff_at TIMESTAMPTZ NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ,
    result_recorded_at TIMESTAMPTZ,
    http_status INTEGER,
    quota_remaining BIGINT,
    reason_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_enrichment_attempt_admission_fixture
        FOREIGN KEY (admission_id, canonical_fixture_id)
        REFERENCES enrichment_daily_admission(id, canonical_fixture_id),
    CONSTRAINT fk_enrichment_attempt_step
        FOREIGN KEY (step_id, admission_id)
        REFERENCES enrichment_plan_step(id, admission_id),
    CONSTRAINT fk_enrichment_attempt_budget
        FOREIGN KEY (budget_intent_id, budget_window_id)
        REFERENCES provider_call_intent(id, window_id),
    CONSTRAINT fk_enrichment_attempt_audit
        FOREIGN KEY (audit_id) REFERENCES provider_call_audit(id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_enrichment_attempt_raw
        FOREIGN KEY (raw_snapshot_id, provider, payload_sha256)
        REFERENCES raw_snapshot(id, provider, payload_sha256),
    CONSTRAINT ck_enrichment_attempt_text CHECK (
        provider = btrim(provider) AND provider <> '' AND provider !~ '[[:cntrl:]]'
        AND provider_competition_id = btrim(provider_competition_id) AND provider_competition_id <> ''
        AND provider_competition_id !~ '[[:cntrl:]]'
        AND source_season = btrim(source_season) AND source_season <> '' AND source_season !~ '[[:cntrl:]]'
        AND source_phase = btrim(source_phase) AND source_phase <> '' AND source_phase !~ '[[:cntrl:]]'
        AND logical_competition = btrim(logical_competition) AND logical_competition <> ''
        AND logical_season = btrim(logical_season) AND logical_season <> ''
        AND logical_phase = btrim(logical_phase) AND logical_phase !~ '[[:cntrl:]]'
        AND provider_fixture_id = btrim(provider_fixture_id) AND provider_fixture_id <> ''
        AND provider_fixture_id !~ '[[:cntrl:]]'
        AND logical_endpoint = btrim(logical_endpoint) AND logical_endpoint <> ''
        AND logical_endpoint !~ '[:?#&=]' AND left(logical_endpoint, 1) <> '/'
        AND position(chr(92) in logical_endpoint) = 0
        AND connector_version = btrim(connector_version) AND connector_version <> ''
        AND parser_version = btrim(parser_version) AND parser_version <> ''
        AND connector_version !~ '[[:cntrl:]]' AND parser_version !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_enrichment_attempt_route CHECK (
        data_type IN ('MATCH_DETAIL', 'LINEUP', 'TEAM_STATS', 'EVENTS', 'PLAYER_STATS')
        AND step_code IN ('LINEUP_T_MINUS_30', 'LINEUP_T_MINUS_15', 'DETAIL_AT_KICKOFF', 'DETAIL_PLUS_45',
            'POSTMATCH_AFTER_FINAL', 'POSTMATCH_RECHECK_FINAL_PLUS_60')
        AND family = data_type
        AND family IN ('MATCH_DETAIL', 'LINEUP', 'TEAM_STATS', 'EVENTS', 'PLAYER_STATS')
        AND request_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_enrichment_attempt_state CHECK (state IN (
        'RESERVED', 'COMMITTED_FOR_SEND', 'RECEIVED', 'HTTP_ERROR', 'UNCERTAIN',
        'RESPONSE_TOO_LARGE', 'SECRET_ECHO', 'RESPONSE_INTERRUPTED'
    )),
    CONSTRAINT ck_enrichment_attempt_response CHECK (
        (raw_snapshot_id IS NULL) = (payload_sha256 IS NULL)
        AND (payload_sha256 IS NULL OR payload_sha256 ~ '^[0-9a-f]{64}$')
        AND (http_status IS NULL OR http_status BETWEEN 100 AND 599)
        AND (quota_remaining IS NULL OR quota_remaining >= 0)
        AND ((state IN ('RECEIVED', 'HTTP_ERROR', 'RESPONSE_TOO_LARGE', 'SECRET_ECHO'))
            = (received_at IS NOT NULL AND result_recorded_at IS NOT NULL))
        AND (state NOT IN ('RESERVED', 'COMMITTED_FOR_SEND') OR
            (received_at IS NULL AND result_recorded_at IS NULL AND raw_snapshot_id IS NULL
                AND http_status IS NULL AND quota_remaining IS NULL AND reason_code IS NULL))
        AND (state <> 'RECEIVED' OR (http_status BETWEEN 200 AND 299 AND raw_snapshot_id IS NOT NULL))
        AND (state <> 'HTTP_ERROR' OR (http_status NOT BETWEEN 200 AND 299 AND raw_snapshot_id IS NOT NULL))
        AND (state <> 'RESPONSE_TOO_LARGE' OR (raw_snapshot_id IS NULL AND reason_code = 'RESPONSE_TOO_LARGE'))
        AND (state NOT IN ('SECRET_ECHO', 'TRANSPORT_ERROR', 'UNCERTAIN') OR
            (raw_snapshot_id IS NULL AND http_status IS NULL AND quota_remaining IS NULL AND reason_code IS NOT NULL))
        AND ((state = 'UNCERTAIN') = (reason_code = 'UNCERTAIN_SEND'))
    ),
    CONSTRAINT ck_enrichment_attempt_times CHECK (
        updated_at >= created_at AND updated_at >= requested_at
        AND (received_at IS NULL OR received_at >= requested_at)
        AND (result_recorded_at IS NULL OR result_recorded_at >= received_at)
    )
);

CREATE INDEX ix_enrichment_collection_attempt_admission
    ON enrichment_collection_attempt (admission_id, created_at DESC, id DESC);
CREATE INDEX ix_enrichment_collection_attempt_fixture
    ON enrichment_collection_attempt (canonical_fixture_id, family, requested_at DESC, id DESC);
CREATE INDEX ix_enrichment_collection_attempt_state
    ON enrichment_collection_attempt (state, updated_at, id);

CREATE TABLE enrichment_collection_derivation (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES enrichment_collection_attempt(id),
    parser_version VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    observation_id UUID NULL REFERENCES provider_enrichment_observation(id),
    raw_sha256 CHAR(64) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_enrichment_derivation_parser CHECK (
        parser_version = btrim(parser_version) AND parser_version <> '' AND parser_version !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_enrichment_derivation_outcome CHECK (
        outcome IN ('APPLIED', 'INCOMPATIBLE', 'UNSUPPORTED_PARSER', 'PROVENANCE_MISMATCH')
    ),
    CONSTRAINT ck_enrichment_derivation_hash CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_enrichment_derivation_observation CHECK (
        outcome <> 'APPLIED' OR observation_id IS NOT NULL
    )
);

CREATE INDEX ix_enrichment_collection_derivation_attempt
    ON enrichment_collection_derivation (attempt_id, evaluated_at DESC, id DESC);
