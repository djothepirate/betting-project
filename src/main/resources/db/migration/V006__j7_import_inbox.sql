CREATE TABLE j7_import_receipt (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    export_id UUID NOT NULL,
    canonical_event_id UUID NOT NULL,
    provider_event_id BIGINT NOT NULL,
    protocol_version VARCHAR(16) NOT NULL,
    schema_id VARCHAR(160) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    generator_version VARCHAR(64) NOT NULL,
    selection_mode VARCHAR(32) NOT NULL,
    source_set_sha256 VARCHAR(64) NOT NULL,
    validation_status VARCHAR(32) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    data_sha256 VARCHAR(64) NOT NULL,
    client_certificate_sha256 VARCHAR(64) NOT NULL,
    payload_size_bytes BIGINT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload_expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_j7_import_receipt_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_j7_import_receipt_export UNIQUE (export_id),
    CONSTRAINT uq_j7_import_receipt_payload_reference UNIQUE (
        id,
        file_sha256,
        payload_size_bytes
    ),
    CONSTRAINT ck_j7_import_receipt_protocol CHECK (protocol_version = '1.0'),
    CONSTRAINT ck_j7_import_receipt_schema CHECK (
        schema_id = 'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1'
        AND schema_version = '1.0.0'
    ),
    CONSTRAINT ck_j7_import_receipt_generator CHECK (
        generator_version ~ '^[A-Za-z0-9._+-]{1,64}$'
    ),
    CONSTRAINT ck_j7_import_receipt_selection CHECK (selection_mode = 'LATEST_AVAILABLE'),
    CONSTRAINT ck_j7_import_receipt_validation CHECK (
        validation_status = 'HUMAN_VALIDATED'
    ),
    CONSTRAINT ck_j7_import_receipt_sha256 CHECK (
        source_set_sha256 ~ '^[0-9a-f]{64}$'
        AND file_sha256 ~ '^[0-9a-f]{64}$'
        AND data_sha256 ~ '^[0-9a-f]{64}$'
        AND client_certificate_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_j7_import_receipt_idempotency CHECK (
        idempotency_key = 'j7:' || export_id::text || ':sha256:' || file_sha256
    ),
    CONSTRAINT ck_j7_import_receipt_payload_size CHECK (
        payload_size_bytes BETWEEN 1 AND 5242880
    ),
    CONSTRAINT ck_j7_import_receipt_provider_event CHECK (provider_event_id > 0),
    CONSTRAINT ck_j7_import_receipt_dates CHECK (
        generated_at <= decided_at
        AND decided_at <= received_at
        AND payload_expires_at > received_at
        AND payload_expires_at <= received_at + INTERVAL '3650 days'
    )
);

CREATE INDEX ix_j7_import_receipt_received
    ON j7_import_receipt (received_at DESC, id DESC);

CREATE INDEX ix_j7_import_receipt_payload_expiry
    ON j7_import_receipt (payload_expires_at, id);

CREATE TABLE j7_import_payload (
    import_id UUID PRIMARY KEY,
    file_sha256 VARCHAR(64) NOT NULL,
    payload_size_bytes BIGINT NOT NULL,
    payload BYTEA NOT NULL,
    CONSTRAINT fk_j7_import_payload_receipt
        FOREIGN KEY (import_id, file_sha256, payload_size_bytes)
        REFERENCES j7_import_receipt (id, file_sha256, payload_size_bytes),
    CONSTRAINT ck_j7_import_payload_sha256 CHECK (
        file_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_j7_import_payload_size CHECK (
        payload_size_bytes BETWEEN 1 AND 5242880
        AND octet_length(payload) = payload_size_bytes
    )
);

CREATE TABLE j7_import_audit (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES j7_import_receipt(id),
    event_type VARCHAR(32) NOT NULL,
    request_idempotency_key VARCHAR(128) NOT NULL,
    observed_file_sha256 VARCHAR(64) NOT NULL,
    observed_data_sha256 VARCHAR(64) NOT NULL,
    client_certificate_sha256 VARCHAR(64) NOT NULL,
    reason_code VARCHAR(48) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_j7_import_audit_event CHECK (
        event_type IN (
            'IMPORTED',
            'DUPLICATE',
            'DIVERGENCE_REJECTED',
            'PAYLOAD_PURGED'
        )
    ),
    CONSTRAINT ck_j7_import_audit_idempotency CHECK (
        request_idempotency_key ~ '^[!-~]{1,128}$'
    ),
    CONSTRAINT ck_j7_import_audit_sha256 CHECK (
        observed_file_sha256 ~ '^[0-9a-f]{64}$'
        AND observed_data_sha256 ~ '^[0-9a-f]{64}$'
        AND client_certificate_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_j7_import_audit_reason CHECK (
        (event_type = 'IMPORTED' AND reason_code = 'ACCEPTED')
        OR (event_type = 'DUPLICATE' AND reason_code = 'BYTE_IDENTICAL')
        OR (
            event_type = 'DIVERGENCE_REJECTED'
            AND reason_code IN (
                'IDEMPOTENCY_KEY_DIVERGENCE',
                'EXPORT_ID_DIVERGENCE',
                'FILE_HASH_DIVERGENCE',
                'DATA_HASH_DIVERGENCE',
                'CERTIFICATE_DIVERGENCE',
                'METADATA_DIVERGENCE'
            )
        )
        OR (event_type = 'PAYLOAD_PURGED' AND reason_code = 'RETENTION_EXPIRED')
    )
);

CREATE TABLE j7_import_payload_tombstone (
    import_id UUID PRIMARY KEY REFERENCES j7_import_receipt(id),
    file_sha256 VARCHAR(64) NOT NULL,
    data_sha256 VARCHAR(64) NOT NULL,
    payload_size_bytes BIGINT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    purged_at TIMESTAMPTZ NOT NULL,
    reason_code VARCHAR(48) NOT NULL,
    CONSTRAINT ck_j7_import_payload_tombstone_sha256 CHECK (
        file_sha256 ~ '^[0-9a-f]{64}$'
        AND data_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_j7_import_payload_tombstone_size CHECK (
        payload_size_bytes BETWEEN 1 AND 5242880
    ),
    CONSTRAINT ck_j7_import_payload_tombstone_dates CHECK (
        purged_at >= received_at
    ),
    CONSTRAINT ck_j7_import_payload_tombstone_reason CHECK (
        reason_code = 'RETENTION_EXPIRED'
    )
);

CREATE INDEX ix_j7_import_audit_history
    ON j7_import_audit (import_id, occurred_at DESC, id DESC);

CREATE INDEX ix_j7_import_audit_chronology
    ON j7_import_audit (occurred_at DESC, id DESC);

CREATE FUNCTION reject_j7_import_immutable_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '55000',
        MESSAGE = format('%I is append-only or immutable', TG_TABLE_NAME);
END;
$$;

CREATE FUNCTION guard_j7_import_payload_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_setting('bettingproject.j7_payload_purge', TRUE) IS DISTINCT FROM 'v1' THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'j7_import_payload can only be deleted by the bounded purge function';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_j7_import_receipt_immutable
    BEFORE UPDATE OR DELETE ON j7_import_receipt
    FOR EACH ROW
    EXECUTE FUNCTION reject_j7_import_immutable_mutation();

CREATE TRIGGER trg_j7_import_payload_immutable
    BEFORE UPDATE ON j7_import_payload
    FOR EACH ROW
    EXECUTE FUNCTION reject_j7_import_immutable_mutation();

CREATE TRIGGER trg_j7_import_payload_bounded_delete
    BEFORE DELETE ON j7_import_payload
    FOR EACH ROW
    EXECUTE FUNCTION guard_j7_import_payload_delete();

CREATE TRIGGER trg_j7_import_audit_append_only
    BEFORE UPDATE OR DELETE ON j7_import_audit
    FOR EACH ROW
    EXECUTE FUNCTION reject_j7_import_immutable_mutation();

CREATE TRIGGER trg_j7_import_payload_tombstone_immutable
    BEFORE UPDATE OR DELETE ON j7_import_payload_tombstone
    FOR EACH ROW
    EXECUTE FUNCTION reject_j7_import_immutable_mutation();

CREATE FUNCTION purge_j7_import_payloads(
    p_expired_before TIMESTAMPTZ,
    p_purged_at TIMESTAMPTZ,
    p_maximum_rows INTEGER
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    purged_count INTEGER;
BEGIN
    IF p_expired_before IS NULL OR p_purged_at IS NULL THEN
        RAISE EXCEPTION USING
            ERRCODE = '22004',
            MESSAGE = 'purge timestamps are required';
    END IF;
    IF p_purged_at < p_expired_before THEN
        RAISE EXCEPTION USING
            ERRCODE = '22007',
            MESSAGE = 'purged_at cannot precede expired_before';
    END IF;
    IF p_maximum_rows IS NULL OR p_maximum_rows < 1 OR p_maximum_rows > 1000 THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'maximum_rows must be between 1 and 1000';
    END IF;

    PERFORM set_config('bettingproject.j7_payload_purge', 'v1', TRUE);

    WITH candidates AS (
        SELECT payload.import_id
        FROM j7_import_payload payload
        JOIN j7_import_receipt receipt ON receipt.id = payload.import_id
        WHERE receipt.payload_expires_at <= p_expired_before
          AND EXISTS (
              SELECT 1
              FROM outbox_message outbox
              WHERE outbox.aggregate_type = 'j7_import_receipt'
                AND outbox.aggregate_id = receipt.id
                AND outbox.destination = 'J7_IMPORT_ACCEPTED'
                AND outbox.idempotency_key = 'j7-import-accepted:' || receipt.id::text
                AND outbox.status = 'DELIVERED'
          )
        ORDER BY receipt.payload_expires_at, payload.import_id
        LIMIT p_maximum_rows
        FOR UPDATE OF payload SKIP LOCKED
    ),
    deleted AS (
        DELETE FROM j7_import_payload payload
        USING candidates
        WHERE payload.import_id = candidates.import_id
        RETURNING payload.import_id
    ),
    tombstoned AS (
        INSERT INTO j7_import_payload_tombstone (
            import_id, file_sha256, data_sha256, payload_size_bytes,
            received_at, purged_at, reason_code
        )
        SELECT receipt.id, receipt.file_sha256, receipt.data_sha256,
               receipt.payload_size_bytes, receipt.received_at, p_purged_at,
               'RETENTION_EXPIRED'
        FROM deleted
        JOIN j7_import_receipt receipt ON receipt.id = deleted.import_id
        RETURNING import_id
    ),
    audited AS (
        INSERT INTO j7_import_audit (
            id, import_id, event_type, request_idempotency_key,
            observed_file_sha256, observed_data_sha256,
            client_certificate_sha256, reason_code, occurred_at
        )
        SELECT gen_random_uuid(), receipt.id, 'PAYLOAD_PURGED',
               receipt.idempotency_key, receipt.file_sha256,
               receipt.data_sha256, receipt.client_certificate_sha256,
               'RETENTION_EXPIRED', p_purged_at
        FROM tombstoned
        JOIN j7_import_receipt receipt ON receipt.id = tombstoned.import_id
        RETURNING id
    )
    SELECT count(*) INTO purged_count FROM audited;

    PERFORM set_config('bettingproject.j7_payload_purge', '', TRUE);
    RETURN purged_count;
END;
$$;
