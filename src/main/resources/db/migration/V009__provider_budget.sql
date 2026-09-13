-- MVP-001 lot 2: no supplier/window activation and no invented call history.
CREATE TABLE provider_budget_scope (
    id UUID PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    account_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_provider_budget_scope UNIQUE (provider, account_ref),
    CONSTRAINT ck_provider_budget_scope_text CHECK (
        provider = btrim(provider) AND provider <> '' AND provider !~ '[[:cntrl:]]'
        AND account_ref = btrim(account_ref) AND account_ref <> '' AND account_ref !~ '[[:cntrl:]]'
    )
);

CREATE TABLE provider_budget_window (
    id UUID PRIMARY KEY,
    scope_id UUID NOT NULL REFERENCES provider_budget_scope(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    capacity BIGINT NULL,
    project_limit BIGINT NOT NULL,
    reserve BIGINT NOT NULL,
    shared_initial BIGINT NOT NULL,
    project_initial BIGINT NOT NULL,
    cadence_limit INTEGER NULL,
    cadence_period_ms BIGINT NULL,
    state VARCHAR(16) NOT NULL,
    current_observation_id UUID NULL,
    quota_inconsistent BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    proof_logical_id VARCHAR(200) NOT NULL,
    proof_sha256 CHAR(64) NOT NULL,
    CONSTRAINT uq_provider_budget_window_scope UNIQUE (id, scope_id),
    CONSTRAINT ck_provider_budget_window_dates CHECK (starts_at < ends_at AND created_at <= updated_at),
    CONSTRAINT ck_provider_budget_window_state CHECK (state IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_provider_budget_window_version CHECK (version >= 1),
    CONSTRAINT ck_provider_budget_window_units CHECK (
        project_limit BETWEEN 1 AND 1000000000
        AND reserve BETWEEN 0 AND 1000000000
        AND shared_initial BETWEEN 0 AND 1000000000
        AND project_initial BETWEEN 0 AND 1000000000
        AND (capacity IS NULL OR (
            capacity BETWEEN 1 AND 1000000000 AND reserve <= capacity
            AND shared_initial <= capacity AND project_initial <= shared_initial
        ))
    ),
    CONSTRAINT ck_provider_budget_window_no_periodic_quota CHECK (
        capacity IS NOT NULL OR (
            reserve = 0 AND shared_initial = 0 AND cadence_limit IS NOT NULL
            AND current_observation_id IS NULL AND NOT quota_inconsistent
        )
    ),
    CONSTRAINT ck_provider_budget_window_cadence CHECK (
        (cadence_limit IS NULL AND cadence_period_ms IS NULL)
        OR (cadence_limit IS NOT NULL AND cadence_period_ms IS NOT NULL
            AND cadence_limit BETWEEN 1 AND 1000000000
            AND cadence_period_ms BETWEEN 1000 AND 31536000000)
    ),
    CONSTRAINT ck_provider_budget_window_proof CHECK (
        proof_logical_id = btrim(proof_logical_id) AND proof_logical_id <> ''
        AND proof_logical_id !~ '[[:cntrl:]]' AND proof_sha256 ~ '^[0-9a-f]{64}$'
    )
);

CREATE UNIQUE INDEX uq_provider_budget_window_open
    ON provider_budget_window (scope_id) WHERE state IN ('ACTIVE', 'SUSPENDED');
CREATE INDEX ix_provider_budget_window_scope ON provider_budget_window (scope_id, starts_at, id);

CREATE TABLE provider_call_intent (
    id UUID PRIMARY KEY,
    window_id UUID NOT NULL REFERENCES provider_budget_window(id),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    logical_endpoint VARCHAR(200) NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    state VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    committed_at TIMESTAMPTZ NULL,
    result_at TIMESTAMPTZ NULL,
    http_status INTEGER NULL,
    result_fingerprint CHAR(64) NULL,
    CONSTRAINT uq_provider_call_intent_window UNIQUE (id, window_id),
    CONSTRAINT ck_provider_call_intent_text CHECK (
        idempotency_key COLLATE "C" ~ '^[!-~]{1,128}$'
        AND logical_endpoint = btrim(logical_endpoint) AND logical_endpoint <> ''
        AND logical_endpoint !~ '[[:cntrl:]]' AND logical_endpoint !~ '[:?#&=]'
        AND left(logical_endpoint, 1) <> '/' AND position(chr(92) in logical_endpoint) = 0
        AND request_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_provider_call_intent_version CHECK (version >= 1),
    CONSTRAINT ck_provider_call_intent_state CHECK (
        state IN ('RESERVED', 'COMMITTED_FOR_SEND', 'RESULT_RECORDED', 'UNCERTAIN', 'RECONCILED', 'RELEASED')
    ),
    CONSTRAINT ck_provider_call_intent_times CHECK (
        created_at <= updated_at
        AND ((state IN ('RESERVED', 'RELEASED')) = (committed_at IS NULL))
        AND ((state IN ('RESULT_RECORDED', 'RECONCILED')) = (result_at IS NOT NULL))
        AND (committed_at IS NULL OR (committed_at >= created_at AND committed_at <= updated_at))
        AND (result_at IS NULL OR (result_at >= committed_at AND result_at <= updated_at))
    ),
    CONSTRAINT ck_provider_call_intent_result CHECK (
        (state = 'RESULT_RECORDED' AND http_status IS NOT NULL AND http_status BETWEEN 100 AND 599
            AND result_fingerprint IS NOT NULL AND result_fingerprint ~ '^[0-9a-f]{64}$')
        OR (state = 'RECONCILED' AND http_status IS NULL
            AND (result_fingerprint IS NULL OR result_fingerprint ~ '^[0-9a-f]{64}$'))
        OR (state NOT IN ('RESULT_RECORDED', 'RECONCILED')
            AND http_status IS NULL AND result_fingerprint IS NULL)
    )
);

CREATE INDEX ix_provider_call_intent_window_state ON provider_call_intent (window_id, state, id);
CREATE INDEX ix_provider_call_intent_scope_cadence
    ON provider_call_intent (window_id, committed_at, result_at, id)
    WHERE state NOT IN ('RESERVED', 'RELEASED');

CREATE TABLE provider_quota_observation (
    id UUID PRIMARY KEY,
    window_id UUID NOT NULL REFERENCES provider_budget_window(id),
    remaining BIGINT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    proof_logical_id VARCHAR(200) NOT NULL,
    proof_sha256 CHAR(64) NOT NULL,
    disposition VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_provider_quota_observation_window UNIQUE (id, window_id),
    CONSTRAINT ck_provider_quota_observation_remaining CHECK (remaining BETWEEN 0 AND 1000000000),
    CONSTRAINT ck_provider_quota_observation_dates CHECK (observed_at < valid_until AND observed_at <= created_at),
    CONSTRAINT ck_provider_quota_observation_disposition CHECK (
        disposition IN ('ACCEPTED', 'STALE', 'CONTRADICTORY')
    ),
    CONSTRAINT ck_provider_quota_observation_proof CHECK (
        proof_logical_id = btrim(proof_logical_id) AND proof_logical_id <> ''
        AND proof_logical_id !~ '[[:cntrl:]]' AND proof_sha256 ~ '^[0-9a-f]{64}$'
    )
);

ALTER TABLE provider_budget_window ADD CONSTRAINT fk_provider_budget_window_observation
    FOREIGN KEY (current_observation_id, id) REFERENCES provider_quota_observation (id, window_id);

CREATE INDEX ix_provider_quota_observation_time ON provider_quota_observation (window_id, observed_at DESC, id);

CREATE TABLE provider_quota_observation_intent (
    observation_id UUID NOT NULL,
    window_id UUID NOT NULL,
    intent_id UUID NOT NULL,
    PRIMARY KEY (observation_id, intent_id),
    CONSTRAINT fk_provider_quota_coverage_observation
        FOREIGN KEY (observation_id, window_id) REFERENCES provider_quota_observation (id, window_id),
    CONSTRAINT fk_provider_quota_coverage_intent
        FOREIGN KEY (intent_id, window_id) REFERENCES provider_call_intent (id, window_id)
);

CREATE TABLE provider_budget_incident (
    id UUID PRIMARY KEY,
    window_id UUID NOT NULL REFERENCES provider_budget_window(id),
    intent_id UUID NULL,
    code VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_provider_budget_incident_intent
        FOREIGN KEY (intent_id, window_id) REFERENCES provider_call_intent (id, window_id),
    CONSTRAINT ck_provider_budget_incident_code CHECK (code ~ '^[A-Z][A-Z0-9_]{0,63}$')
);

CREATE INDEX ix_provider_budget_incident_window ON provider_budget_incident (window_id, created_at DESC, id);

CREATE TABLE provider_budget_event (
    id UUID PRIMARY KEY,
    scope_id UUID NOT NULL REFERENCES provider_budget_scope(id),
    window_id UUID NULL,
    intent_id UUID NULL,
    type VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NULL,
    operator_id VARCHAR(100) NULL,
    justification VARCHAR(1000) NULL,
    proof_logical_id VARCHAR(200) NULL,
    proof_sha256 CHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_provider_budget_event_window
        FOREIGN KEY (window_id, scope_id) REFERENCES provider_budget_window (id, scope_id),
    CONSTRAINT fk_provider_budget_event_intent
        FOREIGN KEY (intent_id, window_id) REFERENCES provider_call_intent (id, window_id),
    CONSTRAINT ck_provider_budget_event_intent CHECK (intent_id IS NULL OR window_id IS NOT NULL),
    CONSTRAINT ck_provider_budget_event_codes CHECK (
        type ~ '^[A-Z][A-Z0-9_]{0,63}$'
        AND (reason_code IS NULL OR reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
    ),
    CONSTRAINT ck_provider_budget_event_text CHECK (
        (operator_id IS NULL OR (operator_id = btrim(operator_id) AND operator_id <> ''
            AND operator_id !~ '[[:cntrl:]]'))
        AND (justification IS NULL OR (justification = btrim(justification) AND justification <> ''
            AND justification !~ '[[:cntrl:]]'))
    ),
    CONSTRAINT ck_provider_budget_event_proof CHECK (
        (proof_logical_id IS NULL AND proof_sha256 IS NULL)
        OR (proof_logical_id IS NOT NULL AND proof_sha256 IS NOT NULL
            AND proof_logical_id = btrim(proof_logical_id) AND proof_logical_id <> ''
            AND proof_logical_id !~ '[[:cntrl:]]' AND proof_sha256 ~ '^[0-9a-f]{64}$')
    )
);

CREATE INDEX ix_provider_budget_event_window ON provider_budget_event (window_id, created_at DESC, id);
