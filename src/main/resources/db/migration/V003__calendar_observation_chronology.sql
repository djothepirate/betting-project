ALTER TABLE canonical_fixture
    ADD COLUMN neutral_venue BOOLEAN,
    ADD COLUMN participants_unordered BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_authority_observation_id UUID,
    ADD COLUMN last_authority_observed_at TIMESTAMPTZ,
    ADD COLUMN last_authority_provider VARCHAR(64),
    ADD COLUMN last_authority_policy_version VARCHAR(64);

ALTER TABLE canonical_fixture
    ALTER COLUMN participants_unordered DROP DEFAULT,
    ADD CONSTRAINT fk_canonical_fixture_last_authority_observation
        FOREIGN KEY (last_authority_observation_id) REFERENCES fixture_observation(id),
    ADD CONSTRAINT ck_canonical_fixture_authority_stamp CHECK (
        (
            last_authority_observation_id IS NULL
            AND last_authority_observed_at IS NULL
            AND last_authority_provider IS NULL
            AND last_authority_policy_version IS NULL
        )
        OR (
            last_authority_observation_id IS NOT NULL
            AND last_authority_observed_at IS NOT NULL
            AND last_authority_provider IS NOT NULL
            AND last_authority_policy_version IS NOT NULL
        )
    );

ALTER TABLE fixture_observation
    ADD COLUMN source_schema_version VARCHAR(64),
    ADD COLUMN source_season VARCHAR(64),
    ADD COLUMN source_neutral_venue BOOLEAN,
    ADD COLUMN source_participants_unordered BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE fixture_observation
SET source_schema_version = 'cal01-fixture-v2';

UPDATE fixture_observation AS observation
SET source_season = season.season_label
FROM canonical_fixture AS fixture
JOIN canonical_season AS season ON season.id = fixture.season_id
WHERE observation.canonical_fixture_id = fixture.id;

ALTER TABLE fixture_observation
    ALTER COLUMN source_schema_version SET NOT NULL,
    ALTER COLUMN source_participants_unordered DROP DEFAULT,
    ADD CONSTRAINT ck_fixture_observation_schema_version CHECK (
        source_schema_version IN ('cal01-fixture-v2', 'cal01-fixture-v3')
    );

CREATE INDEX ix_fixture_observation_provider_time
    ON fixture_observation (provider, provider_fixture_id, observed_at DESC, id DESC);

CREATE TABLE fixture_application_log (
    id UUID PRIMARY KEY,
    fixture_observation_id UUID NOT NULL REFERENCES fixture_observation(id),
    canonical_fixture_id UUID REFERENCES canonical_fixture(id),
    previous_authority_observation_id UUID REFERENCES fixture_observation(id),
    outcome VARCHAR(48) NOT NULL,
    reason_code VARCHAR(64),
    authority_role VARCHAR(16),
    policy_version VARCHAR(64),
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_fixture_application_outcome CHECK (
        outcome IN (
            'CREATED',
            'UPDATED',
            'UNCHANGED',
            'BLOCKED',
            'REJECTED',
            'STALE',
            'EQUAL_AUTHORITY_TIME_CONFLICT',
            'INVALID_TRANSITION',
            'CONTROL_DIVERGENCE',
            'UNASSIGNED'
        )
    ),
    CONSTRAINT ck_fixture_application_reason CHECK (
        (
            outcome IN ('CREATED', 'UPDATED', 'UNCHANGED')
            AND reason_code IS NULL
        )
        OR (
            outcome IN (
                'BLOCKED',
                'REJECTED',
                'STALE',
                'EQUAL_AUTHORITY_TIME_CONFLICT',
                'INVALID_TRANSITION',
                'CONTROL_DIVERGENCE',
                'UNASSIGNED'
            )
            AND reason_code IS NOT NULL
        )
    ),
    CONSTRAINT ck_fixture_application_authority_role CHECK (
        authority_role IS NULL OR authority_role IN ('PRIMARY', 'CONTROL', 'UNASSIGNED')
    ),
    CONSTRAINT ck_fixture_application_policy_context CHECK (
        (authority_role IS NULL AND policy_version IS NULL)
        OR (authority_role IS NOT NULL AND policy_version IS NOT NULL)
    ),
    CONSTRAINT ck_fixture_application_canonical_fixture CHECK (
        outcome NOT IN ('CREATED', 'UPDATED', 'UNCHANGED')
        OR canonical_fixture_id IS NOT NULL
    )
);

CREATE INDEX ix_fixture_application_observation
    ON fixture_application_log (fixture_observation_id, evaluated_at DESC, id DESC);

CREATE INDEX ix_fixture_application_fixture
    ON fixture_application_log (canonical_fixture_id, evaluated_at DESC, id DESC)
    WHERE canonical_fixture_id IS NOT NULL;
