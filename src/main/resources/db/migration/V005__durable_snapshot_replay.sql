CREATE INDEX ix_raw_snapshot_payload_sha256
    ON raw_snapshot (payload_sha256, id);

CREATE TABLE normalization_replay_request (
    id UUID PRIMARY KEY,
    control_command_receipt_id UUID NOT NULL REFERENCES control_command_receipt(id),
    raw_snapshot_id UUID NOT NULL REFERENCES raw_snapshot(id),
    expected_payload_sha256 CHAR(64) NOT NULL,
    provider_mapping_decision_id UUID REFERENCES provider_mapping_decision(id),
    origin VARCHAR(24) NOT NULL,
    selector_type VARCHAR(24) NOT NULL,
    selector_value VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    attempt_count INTEGER NOT NULL,
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_normalization_replay_request_receipt UNIQUE (control_command_receipt_id),
    CONSTRAINT ck_normalization_replay_request_sha256 CHECK (
        expected_payload_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_normalization_replay_request_origin CHECK (
        origin IN ('MANUAL', 'MAPPING_DECISION')
    ),
    CONSTRAINT ck_normalization_replay_request_selector_type CHECK (
        selector_type IN ('SNAPSHOT_ID', 'PAYLOAD_SHA256')
    ),
    CONSTRAINT ck_normalization_replay_request_selector CHECK (
        (
            selector_type = 'SNAPSHOT_ID'
            AND selector_value ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        )
        OR (
            selector_type = 'PAYLOAD_SHA256'
            AND selector_value ~ '^[0-9a-f]{64}$'
        )
    ),
    CONSTRAINT ck_normalization_replay_request_origin_decision CHECK (
        (
            origin = 'MANUAL'
            AND provider_mapping_decision_id IS NULL
        )
        OR (
            origin = 'MAPPING_DECISION'
            AND provider_mapping_decision_id IS NOT NULL
            AND selector_type = 'SNAPSHOT_ID'
        )
    ),
    CONSTRAINT ck_normalization_replay_request_status CHECK (
        status IN (
            'PENDING',
            'RUNNING',
            'COMPLETED',
            'FAILED_RETRYABLE',
            'FAILED_TERMINAL'
        )
    ),
    CONSTRAINT ck_normalization_replay_request_version CHECK (version >= 1),
    CONSTRAINT ck_normalization_replay_request_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_normalization_replay_request_attempt_state CHECK (
        (status = 'PENDING' AND attempt_count = 0)
        OR (status <> 'PENDING' AND attempt_count >= 1)
    ),
    CONSTRAINT ck_normalization_replay_request_completion CHECK (
        (
            status IN ('PENDING', 'RUNNING', 'FAILED_RETRYABLE')
            AND completed_at IS NULL
        )
        OR (
            status IN ('COMPLETED', 'FAILED_TERMINAL')
            AND completed_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_normalization_replay_request_error CHECK (
        (
            status IN ('PENDING', 'RUNNING', 'COMPLETED')
            AND last_error_code IS NULL
            AND last_error_message IS NULL
        )
        OR (
            status IN ('FAILED_RETRYABLE', 'FAILED_TERMINAL')
            AND last_error_code IS NOT NULL
            AND last_error_message IS NOT NULL
            AND last_error_code = btrim(last_error_code)
            AND char_length(last_error_code) BETWEEN 1 AND 100
            AND last_error_message = btrim(last_error_message)
            AND char_length(last_error_message) BETWEEN 1 AND 2000
        )
    ),
    CONSTRAINT ck_normalization_replay_request_dates CHECK (
        updated_at >= created_at
        AND (completed_at IS NULL OR completed_at >= created_at)
    )
);

CREATE UNIQUE INDEX uq_normalization_replay_request_mapping_snapshot
    ON normalization_replay_request (provider_mapping_decision_id, raw_snapshot_id)
    WHERE provider_mapping_decision_id IS NOT NULL;

CREATE INDEX ix_normalization_replay_request_status
    ON normalization_replay_request (status, updated_at, id);

CREATE INDEX ix_normalization_replay_request_snapshot
    ON normalization_replay_request (raw_snapshot_id, created_at DESC, id DESC);

CREATE INDEX ix_normalization_replay_request_decision
    ON normalization_replay_request (provider_mapping_decision_id, created_at DESC, id DESC)
    WHERE provider_mapping_decision_id IS NOT NULL;

CREATE TABLE normalization_replay_attempt (
    id UUID PRIMARY KEY,
    normalization_replay_request_id UUID NOT NULL REFERENCES normalization_replay_request(id),
    attempt_number INTEGER NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    expected_payload_sha256 CHAR(64) NOT NULL,
    actual_payload_sha256 CHAR(64) NOT NULL,
    compatible BOOLEAN,
    fixtures_created INTEGER,
    fixtures_updated INTEGER,
    fixtures_unchanged INTEGER,
    fixtures_blocked INTEGER,
    anomalies INTEGER,
    error_code VARCHAR(100),
    error_message VARCHAR(2000),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_normalization_replay_attempt_number UNIQUE (
        normalization_replay_request_id,
        attempt_number
    ),
    CONSTRAINT ck_normalization_replay_attempt_number CHECK (attempt_number >= 1),
    CONSTRAINT ck_normalization_replay_attempt_outcome CHECK (
        outcome IN ('COMPLETED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL')
    ),
    CONSTRAINT ck_normalization_replay_attempt_hashes CHECK (
        expected_payload_sha256 ~ '^[0-9a-f]{64}$'
        AND actual_payload_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_normalization_replay_attempt_result CHECK (
        (
            outcome = 'COMPLETED'
            AND compatible IS NOT NULL
            AND fixtures_created IS NOT NULL
            AND fixtures_created >= 0
            AND fixtures_updated IS NOT NULL
            AND fixtures_updated >= 0
            AND fixtures_unchanged IS NOT NULL
            AND fixtures_unchanged >= 0
            AND fixtures_blocked IS NOT NULL
            AND fixtures_blocked >= 0
            AND anomalies IS NOT NULL
            AND anomalies >= 0
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR (
            outcome IN ('FAILED_RETRYABLE', 'FAILED_TERMINAL')
            AND compatible IS NULL
            AND fixtures_created IS NULL
            AND fixtures_updated IS NULL
            AND fixtures_unchanged IS NULL
            AND fixtures_blocked IS NULL
            AND anomalies IS NULL
            AND error_code IS NOT NULL
            AND error_message IS NOT NULL
            AND error_code = btrim(error_code)
            AND char_length(error_code) BETWEEN 1 AND 100
            AND error_message = btrim(error_message)
            AND char_length(error_message) BETWEEN 1 AND 2000
        )
    ),
    CONSTRAINT ck_normalization_replay_attempt_dates CHECK (finished_at >= started_at)
);

CREATE INDEX ix_normalization_replay_attempt_history
    ON normalization_replay_attempt (
        normalization_replay_request_id,
        attempt_number DESC,
        id DESC
    );

CREATE INDEX ix_normalization_replay_attempt_page
    ON normalization_replay_attempt (finished_at DESC, id DESC);

CREATE TABLE normalization_replay_attempt_application (
    normalization_replay_attempt_id UUID NOT NULL REFERENCES normalization_replay_attempt(id),
    fixture_application_log_id UUID NOT NULL REFERENCES fixture_application_log(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (normalization_replay_attempt_id, fixture_application_log_id),
    CONSTRAINT uq_normalization_replay_application UNIQUE (fixture_application_log_id)
);

CREATE INDEX ix_normalization_replay_attempt_application_log
    ON normalization_replay_attempt_application (fixture_application_log_id);

CREATE TABLE normalization_replay_attempt_anomaly_event (
    normalization_replay_attempt_id UUID NOT NULL REFERENCES normalization_replay_attempt(id),
    normalization_anomaly_event_id UUID NOT NULL REFERENCES normalization_anomaly_event(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (normalization_replay_attempt_id, normalization_anomaly_event_id),
    CONSTRAINT uq_normalization_replay_anomaly_event UNIQUE (normalization_anomaly_event_id)
);

CREATE INDEX ix_normalization_replay_attempt_anomaly_event_log
    ON normalization_replay_attempt_anomaly_event (normalization_anomaly_event_id);
