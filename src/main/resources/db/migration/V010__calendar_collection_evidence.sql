-- MVP-001 lot 3: durable calendar evidence, no active supplier or invented history.
CREATE TABLE calendar_collection (
    id UUID PRIMARY KEY,
    window_id UUID NOT NULL REFERENCES provider_budget_window(id),
    provider VARCHAR(64) NOT NULL,
    provider_competition_id VARCHAR(200) NOT NULL,
    source_season VARCHAR(64) NOT NULL,
    source_phase VARCHAR(64) NOT NULL,
    data_type VARCHAR(24) NOT NULL,
    collection_date DATE NOT NULL,
    season_start_year INTEGER NOT NULL,
    command_sha256 CHAR(64) NOT NULL,
    registry_sha256 CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_calendar_collection_window UNIQUE (id, window_id),
    CONSTRAINT ck_calendar_collection_key CHECK (
        provider <> '' AND provider = btrim(provider) AND provider !~ '[[:cntrl:]]'
        AND provider_competition_id <> '' AND provider_competition_id = btrim(provider_competition_id)
        AND provider_competition_id !~ '[[:cntrl:]]'
        AND source_season <> '' AND source_season = btrim(source_season) AND source_season !~ '[[:cntrl:]]'
        AND source_phase <> '' AND source_phase = btrim(source_phase) AND source_phase !~ '[[:cntrl:]]'
        AND data_type = 'CALENDAR'
    ),
    CONSTRAINT ck_calendar_collection_year CHECK (season_start_year BETWEEN 1000 AND 9999),
    CONSTRAINT ck_calendar_collection_hashes CHECK (
        command_sha256 ~ '^[0-9a-f]{64}$' AND registry_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_calendar_collection_status CHECK (status IN ('RUNNING', 'COMPLETED', 'INCOMPLETE')),
    CONSTRAINT ck_calendar_collection_reason CHECK (
        (status IN ('RUNNING', 'COMPLETED') AND reason_code IS NULL)
        OR (status = 'INCOMPLETE' AND reason_code IS NOT NULL AND reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
    ),
    CONSTRAINT ck_calendar_collection_dates CHECK (created_at <= updated_at)
);

CREATE INDEX ix_calendar_collection_window ON calendar_collection (window_id, created_at DESC, id);

CREATE TABLE calendar_collection_page (
    id UUID PRIMARY KEY,
    collection_id UUID NOT NULL,
    window_id UUID NOT NULL,
    intent_id UUID NOT NULL UNIQUE,
    audit_id UUID NOT NULL UNIQUE,
    page_number INTEGER NOT NULL,
    page_offset INTEGER NOT NULL,
    page_limit INTEGER NOT NULL,
    connector_version VARCHAR(64) NOT NULL,
    raw_snapshot_id UUID REFERENCES raw_snapshot(id),
    raw_sha256 CHAR(64),
    requested_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ,
    http_status INTEGER,
    quota_remaining BIGINT,
    response_code VARCHAR(64) NOT NULL,
    CONSTRAINT uq_calendar_collection_page_number UNIQUE (collection_id, page_number),
    CONSTRAINT uq_calendar_page_raw_provenance UNIQUE (id, raw_sha256),
    CONSTRAINT fk_calendar_page_collection FOREIGN KEY (collection_id, window_id)
        REFERENCES calendar_collection(id, window_id),
    CONSTRAINT fk_calendar_page_intent FOREIGN KEY (intent_id, window_id)
        REFERENCES provider_call_intent(id, window_id),
    CONSTRAINT fk_calendar_page_audit FOREIGN KEY (audit_id)
        REFERENCES provider_call_audit(id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_calendar_page_pagination CHECK (
        page_number BETWEEN 1 AND 100 AND page_offset >= 0 AND page_limit BETWEEN 1 AND 100
    ),
    CONSTRAINT ck_calendar_page_connector CHECK (
        connector_version <> '' AND connector_version = btrim(connector_version)
        AND connector_version !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_calendar_page_raw CHECK (
        (raw_snapshot_id IS NULL AND raw_sha256 IS NULL)
        OR (raw_snapshot_id IS NOT NULL AND raw_sha256 IS NOT NULL AND raw_sha256 ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_calendar_page_response CHECK (
        response_code IN ('PENDING', 'RECEIVED', 'HTTP_ERROR', 'UNCERTAIN_RESPONSE',
            'RESPONSE_TOO_LARGE', 'SECRET_ECHO', 'TIMEOUT', 'TRANSPORT_ERROR', 'RESPONSE_INTERRUPTED')
        AND ((response_code = 'PENDING') = (received_at IS NULL))
        AND (response_code <> 'PENDING' OR (raw_snapshot_id IS NULL AND http_status IS NULL AND quota_remaining IS NULL))
        AND (response_code <> 'SECRET_ECHO' OR raw_snapshot_id IS NULL)
        AND (response_code NOT IN ('RECEIVED', 'HTTP_ERROR')
            OR (raw_snapshot_id IS NOT NULL AND http_status IS NOT NULL))
        AND (response_code <> 'RECEIVED' OR http_status BETWEEN 200 AND 299)
        AND (response_code <> 'HTTP_ERROR' OR http_status NOT BETWEEN 200 AND 299)
    ),
    CONSTRAINT ck_calendar_page_http CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_calendar_page_quota CHECK (quota_remaining IS NULL OR quota_remaining >= 0),
    CONSTRAINT ck_calendar_page_dates CHECK (received_at IS NULL OR received_at >= requested_at)
);

CREATE INDEX ix_calendar_page_collection ON calendar_collection_page (collection_id, page_number);

CREATE TABLE calendar_collection_derivation (
    id UUID PRIMARY KEY,
    page_id UUID NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    derived_snapshot_id UUID REFERENCES raw_snapshot(id),
    raw_sha256 CHAR(64) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_calendar_derivation_raw_provenance FOREIGN KEY (page_id, raw_sha256)
        REFERENCES calendar_collection_page(id, raw_sha256),
    CONSTRAINT ck_calendar_derivation_parser CHECK (
        parser_version <> '' AND parser_version = btrim(parser_version) AND parser_version !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_calendar_derivation_outcome CHECK (
        outcome IN ('PARSED', 'APPLIED', 'INCOMPATIBLE', 'INCOMPLETE_PAGINATION',
            'UNSUPPORTED_STATUS', 'INTEGRITY_ERROR')
    ),
    CONSTRAINT ck_calendar_derivation_hash CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_calendar_derivation_snapshot CHECK (
        outcome NOT IN ('PARSED', 'APPLIED') OR derived_snapshot_id IS NOT NULL
    )
);

CREATE INDEX ix_calendar_derivation_page
    ON calendar_collection_derivation (page_id, evaluated_at DESC, id DESC);
