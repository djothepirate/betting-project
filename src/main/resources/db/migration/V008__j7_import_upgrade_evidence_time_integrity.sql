DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM j7_import_payload_tombstone
        WHERE purged_at > clock_timestamp()
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'V006/V007 J7 tombstone evidence is future-dated';
    END IF;
END;
$$;

COMMENT ON TABLE j7_import_payload_tombstone
    IS 'Immutable evidence of an expired J7 payload removed by the bounded purge; V008 rejects future-dated legacy evidence during upgrade.';
