ALTER TABLE provider_mapping
    ALTER COLUMN season TYPE VARCHAR(64),
    ADD COLUMN version BIGINT;

UPDATE provider_mapping
SET version = 1;

ALTER TABLE provider_mapping
    ALTER COLUMN version SET NOT NULL,
    ADD CONSTRAINT ck_provider_mapping_version CHECK (version >= 1);

CREATE INDEX ix_provider_mapping_page
    ON provider_mapping (updated_at DESC, id DESC);

CREATE INDEX ix_provider_mapping_status_provider_page
    ON provider_mapping (mapping_status, provider, updated_at DESC, id DESC);

CREATE INDEX ix_provider_mapping_canonical_page
    ON provider_mapping (canonical_entity_id, updated_at DESC, id DESC)
    WHERE canonical_entity_id IS NOT NULL;

ALTER TABLE normalization_anomaly
    ADD COLUMN season VARCHAR(64),
    ADD COLUMN phase VARCHAR(64),
    ADD COLUMN version BIGINT,
    ADD COLUMN last_seen_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN occurrence_count BIGINT;

UPDATE normalization_anomaly
SET version = 1,
    last_seen_at = created_at,
    updated_at = created_at,
    occurrence_count = 1;

ALTER TABLE normalization_anomaly
    ALTER COLUMN version SET NOT NULL,
    ALTER COLUMN last_seen_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN occurrence_count SET NOT NULL,
    ADD CONSTRAINT ck_normalization_anomaly_version CHECK (version >= 1),
    ADD CONSTRAINT ck_normalization_anomaly_occurrences CHECK (occurrence_count >= 1);

ALTER TABLE normalization_anomaly
    DROP CONSTRAINT uq_normalization_anomaly;

ALTER TABLE normalization_anomaly
    ADD CONSTRAINT uq_normalization_anomaly_context UNIQUE NULLS NOT DISTINCT (
        raw_snapshot_id,
        provider,
        entity_type,
        provider_entity_id,
        season,
        phase,
        anomaly_code
    );

CREATE INDEX ix_normalization_anomaly_page
    ON normalization_anomaly (status, created_at DESC, id DESC);

CREATE INDEX ix_normalization_anomaly_mapping_context
    ON normalization_anomaly (
        status,
        provider,
        entity_type,
        provider_entity_id,
        season,
        phase,
        created_at DESC,
        id DESC
    );

CREATE TABLE control_command_receipt (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(40) NOT NULL,
    command_sha256 CHAR(64) NOT NULL,
    result_resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_control_command_receipt_key UNIQUE (idempotency_key),
    CONSTRAINT ck_control_command_receipt_key CHECK (
        idempotency_key ~ '^[!-~]{1,128}$'
    ),
    CONSTRAINT ck_control_command_receipt_type CHECK (
        command_type ~ '^[A-Z][A-Z0-9_]{0,39}$'
    ),
    CONSTRAINT ck_control_command_receipt_sha256 CHECK (
        command_sha256 ~ '^[0-9a-f]{64}$'
    )
);

CREATE TABLE provider_mapping_decision (
    id UUID PRIMARY KEY,
    provider_mapping_id UUID NOT NULL REFERENCES provider_mapping(id),
    control_command_receipt_id UUID NOT NULL REFERENCES control_command_receipt(id),
    decision_type VARCHAR(16) NOT NULL,
    expected_version BIGINT NOT NULL,
    resulting_version BIGINT NOT NULL,
    previous_mapping_status VARCHAR(24),
    previous_canonical_entity_id UUID,
    previous_confidence NUMERIC(5, 4),
    resulting_mapping_status VARCHAR(24) NOT NULL,
    resulting_canonical_entity_id UUID,
    resulting_confidence NUMERIC(5, 4),
    operator_id VARCHAR(100) NOT NULL,
    justification VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_provider_mapping_decision_receipt UNIQUE (control_command_receipt_id),
    CONSTRAINT uq_provider_mapping_decision_version UNIQUE (
        provider_mapping_id,
        resulting_version
    ),
    CONSTRAINT ck_provider_mapping_decision_type CHECK (
        decision_type IN ('CONFIRM', 'REJECT')
    ),
    CONSTRAINT ck_provider_mapping_decision_versions CHECK (
        expected_version >= 0
        AND resulting_version = expected_version + 1
    ),
    CONSTRAINT ck_provider_mapping_decision_previous CHECK (
        (
            expected_version = 0
            AND previous_mapping_status IS NULL
            AND previous_canonical_entity_id IS NULL
            AND previous_confidence IS NULL
        )
        OR (
            expected_version >= 1
            AND previous_mapping_status IS NOT NULL
            AND previous_mapping_status IN ('CONFIRMED', 'AMBIGUOUS', 'REJECTED')
            AND (
                (
                    previous_mapping_status = 'CONFIRMED'
                    AND previous_canonical_entity_id IS NOT NULL
                )
                OR (
                    previous_mapping_status <> 'CONFIRMED'
                    AND previous_canonical_entity_id IS NULL
                )
            )
        )
    ),
    CONSTRAINT ck_provider_mapping_decision_result CHECK (
        (
            decision_type = 'CONFIRM'
            AND resulting_mapping_status = 'CONFIRMED'
            AND resulting_canonical_entity_id IS NOT NULL
            AND resulting_confidence IS NOT NULL
            AND resulting_confidence = 1.0000
        )
        OR (
            decision_type = 'REJECT'
            AND resulting_mapping_status = 'REJECTED'
            AND resulting_canonical_entity_id IS NULL
            AND resulting_confidence IS NULL
        )
    ),
    CONSTRAINT ck_provider_mapping_decision_previous_confidence CHECK (
        previous_confidence IS NULL
        OR (previous_confidence >= 0 AND previous_confidence <= 1)
    ),
    CONSTRAINT ck_provider_mapping_decision_operator CHECK (
        operator_id = btrim(operator_id)
        AND char_length(operator_id) BETWEEN 1 AND 100
        AND operator_id !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_provider_mapping_decision_justification CHECK (
        justification = btrim(justification)
        AND char_length(justification) BETWEEN 1 AND 1000
        AND justification !~ '[[:cntrl:]]'
    )
);

CREATE INDEX ix_provider_mapping_decision_history
    ON provider_mapping_decision (provider_mapping_id, created_at DESC, id DESC);

CREATE INDEX ix_provider_mapping_decision_page
    ON provider_mapping_decision (created_at DESC, id DESC);

CREATE TABLE normalization_anomaly_event (
    id UUID PRIMARY KEY,
    normalization_anomaly_id UUID NOT NULL REFERENCES normalization_anomaly(id),
    event_type VARCHAR(24) NOT NULL,
    previous_status VARCHAR(24),
    resulting_status VARCHAR(24) NOT NULL,
    fixture_application_log_id UUID REFERENCES fixture_application_log(id),
    details VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_normalization_anomaly_event_type CHECK (
        event_type IN ('OPENED', 'OBSERVED', 'RESOLVED', 'REOPENED')
    ),
    CONSTRAINT ck_normalization_anomaly_event_statuses CHECK (
        (
            event_type = 'OPENED'
            AND previous_status IS NULL
            AND resulting_status = 'OPEN'
        )
        OR (
            event_type = 'OBSERVED'
            AND previous_status IS NOT NULL
            AND previous_status IN ('OPEN', 'IGNORED')
            AND resulting_status = previous_status
        )
        OR (
            event_type = 'RESOLVED'
            AND previous_status IS NOT NULL
            AND previous_status = 'OPEN'
            AND resulting_status = 'RESOLVED'
        )
        OR (
            event_type = 'REOPENED'
            AND previous_status IS NOT NULL
            AND previous_status = 'RESOLVED'
            AND resulting_status = 'OPEN'
        )
    ),
    CONSTRAINT ck_normalization_anomaly_event_details CHECK (
        details = btrim(details)
        AND char_length(details) BETWEEN 1 AND 2000
    )
);

CREATE INDEX ix_normalization_anomaly_event_page
    ON normalization_anomaly_event (created_at DESC, id DESC);

CREATE INDEX ix_normalization_anomaly_event_history
    ON normalization_anomaly_event (
        normalization_anomaly_id,
        created_at DESC,
        id DESC
    );

CREATE TABLE provider_mapping_decision_anomaly (
    provider_mapping_decision_id UUID NOT NULL REFERENCES provider_mapping_decision(id),
    normalization_anomaly_id UUID NOT NULL REFERENCES normalization_anomaly(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (provider_mapping_decision_id, normalization_anomaly_id)
);

CREATE INDEX ix_provider_mapping_decision_anomaly_snapshot
    ON provider_mapping_decision_anomaly (normalization_anomaly_id, created_at DESC);
