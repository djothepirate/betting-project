ALTER TABLE j7_import_receipt
    ADD CONSTRAINT uq_j7_import_receipt_tombstone_reference UNIQUE (
        id,
        file_sha256,
        data_sha256,
        payload_size_bytes,
        received_at,
        payload_expires_at
    );

ALTER TABLE j7_import_payload_tombstone
    ADD COLUMN payload_expires_at TIMESTAMPTZ;

DROP TRIGGER trg_j7_import_payload_tombstone_immutable
    ON j7_import_payload_tombstone;

UPDATE j7_import_payload_tombstone tombstone
SET payload_expires_at = receipt.payload_expires_at
FROM j7_import_receipt receipt
WHERE receipt.id = tombstone.import_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM j7_import_payload_tombstone tombstone
        JOIN j7_import_receipt receipt ON receipt.id = tombstone.import_id
        WHERE tombstone.purged_at < receipt.payload_expires_at
           OR EXISTS (
                SELECT 1
                FROM j7_import_payload payload
                WHERE payload.import_id = tombstone.import_id
           )
           OR NOT EXISTS (
                SELECT 1
                FROM j7_import_audit audit
                WHERE audit.import_id = tombstone.import_id
                  AND audit.event_type = 'PAYLOAD_PURGED'
                  AND audit.reason_code = 'RETENTION_EXPIRED'
                  AND audit.occurred_at = tombstone.purged_at
                  AND audit.request_idempotency_key = receipt.idempotency_key
                  AND audit.observed_file_sha256 = receipt.file_sha256
                  AND audit.observed_data_sha256 = receipt.data_sha256
                  AND audit.client_certificate_sha256 = receipt.client_certificate_sha256
           )
           OR NOT EXISTS (
                SELECT 1
                FROM outbox_message outbox
                WHERE outbox.aggregate_type = 'j7_import_receipt'
                  AND outbox.aggregate_id = receipt.id
                  AND outbox.destination = 'J7_IMPORT_ACCEPTED'
                  AND outbox.idempotency_key = 'j7-import-accepted:' || receipt.id::text
                  AND outbox.status = 'DELIVERED'
           )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'V006 J7 tombstone evidence is incomplete or inconsistent';
    END IF;
END;
$$;

ALTER TABLE j7_import_payload_tombstone
    ALTER COLUMN payload_expires_at SET NOT NULL,
    DROP CONSTRAINT ck_j7_import_payload_tombstone_dates,
    ADD CONSTRAINT fk_j7_import_payload_tombstone_receipt
        FOREIGN KEY (
            import_id,
            file_sha256,
            data_sha256,
            payload_size_bytes,
            received_at,
            payload_expires_at
        ) REFERENCES j7_import_receipt (
            id,
            file_sha256,
            data_sha256,
            payload_size_bytes,
            received_at,
            payload_expires_at
        ),
    ADD CONSTRAINT ck_j7_import_payload_tombstone_dates CHECK (
        payload_expires_at > received_at
        AND purged_at >= payload_expires_at
    );

CREATE TRIGGER trg_j7_import_payload_tombstone_immutable
    BEFORE UPDATE OR DELETE ON j7_import_payload_tombstone
    FOR EACH ROW
    EXECUTE FUNCTION reject_j7_import_immutable_mutation();

CREATE OR REPLACE FUNCTION guard_j7_import_payload_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM j7_import_payload_tombstone tombstone
        JOIN j7_import_receipt receipt
          ON receipt.id = tombstone.import_id
        JOIN j7_import_audit audit
          ON audit.import_id = tombstone.import_id
         AND audit.event_type = 'PAYLOAD_PURGED'
         AND audit.reason_code = 'RETENTION_EXPIRED'
         AND audit.occurred_at = tombstone.purged_at
         AND audit.request_idempotency_key = receipt.idempotency_key
         AND audit.observed_file_sha256 = receipt.file_sha256
         AND audit.observed_data_sha256 = receipt.data_sha256
         AND audit.client_certificate_sha256 = receipt.client_certificate_sha256
        WHERE tombstone.import_id = OLD.import_id
          AND tombstone.file_sha256 = OLD.file_sha256
          AND tombstone.payload_size_bytes = OLD.payload_size_bytes
          AND tombstone.purged_at >= receipt.payload_expires_at
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'j7_import_payload deletion requires its durable tombstone and purge audit';
    END IF;
    RETURN OLD;
END;
$$;

CREATE OR REPLACE FUNCTION purge_j7_import_payloads(
    p_expired_before TIMESTAMPTZ,
    p_purged_at TIMESTAMPTZ,
    p_maximum_rows INTEGER
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    purged_count INTEGER := 0;
    candidate RECORD;
    database_now TIMESTAMPTZ := clock_timestamp();
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
    IF p_expired_before > database_now OR p_purged_at > database_now THEN
        RAISE EXCEPTION USING
            ERRCODE = '22007',
            MESSAGE = 'purge timestamps cannot be in the future';
    END IF;
    IF p_maximum_rows IS NULL OR p_maximum_rows < 1 OR p_maximum_rows > 1000 THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'maximum_rows must be between 1 and 1000';
    END IF;

    FOR candidate IN
        SELECT payload.import_id,
               receipt.idempotency_key,
               receipt.file_sha256,
               receipt.data_sha256,
               receipt.client_certificate_sha256,
               receipt.payload_size_bytes,
               receipt.received_at,
               receipt.payload_expires_at
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
    LOOP
        INSERT INTO j7_import_payload_tombstone (
            import_id,
            file_sha256,
            data_sha256,
            payload_size_bytes,
            received_at,
            payload_expires_at,
            purged_at,
            reason_code
        ) VALUES (
            candidate.import_id,
            candidate.file_sha256,
            candidate.data_sha256,
            candidate.payload_size_bytes,
            candidate.received_at,
            candidate.payload_expires_at,
            p_purged_at,
            'RETENTION_EXPIRED'
        );

        INSERT INTO j7_import_audit (
            id,
            import_id,
            event_type,
            request_idempotency_key,
            observed_file_sha256,
            observed_data_sha256,
            client_certificate_sha256,
            reason_code,
            occurred_at
        ) VALUES (
            gen_random_uuid(),
            candidate.import_id,
            'PAYLOAD_PURGED',
            candidate.idempotency_key,
            candidate.file_sha256,
            candidate.data_sha256,
            candidate.client_certificate_sha256,
            'RETENTION_EXPIRED',
            p_purged_at
        );

        DELETE FROM j7_import_payload
        WHERE import_id = candidate.import_id;
        purged_count := purged_count + 1;
    END LOOP;

    RETURN purged_count;
END;
$$;

COMMENT ON FUNCTION purge_j7_import_payloads(TIMESTAMPTZ, TIMESTAMPTZ, INTEGER)
    IS 'Application-supported bounded J7 payload removal; rejects future cutoffs and requires expiry, delivered outbox, tombstone and audit. The database owner remains a trust boundary.';
