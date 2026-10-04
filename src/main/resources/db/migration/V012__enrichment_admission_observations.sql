-- ENR-002 lot 2: durable daily admission and immutable derived observations.
-- No admission, provider observation, quality finding or provider activation is seeded here.

CREATE TABLE enrichment_daily_plan (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    command_sha256 CHAR(64) NOT NULL,
    budget_window_id UUID NOT NULL REFERENCES provider_budget_window(id),
    competition_date DATE NOT NULL UNIQUE,
    registry_sha256 CHAR(64) NOT NULL,
    estimated_calls_per_fixture INTEGER NOT NULL,
    selected_fixture_count INTEGER NOT NULL,
    estimated_calls BIGINT NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_enrichment_daily_plan_key CHECK (idempotency_key COLLATE "C" ~ '^[!-~]{1,128}$'),
    CONSTRAINT ck_enrichment_daily_plan_hashes CHECK (
        command_sha256 ~ '^[0-9a-f]{64}$' AND registry_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_enrichment_daily_plan_counts CHECK (
        estimated_calls_per_fixture BETWEEN 1 AND 80
        AND selected_fixture_count BETWEEN 1 AND 7
        AND estimated_calls = selected_fixture_count * estimated_calls_per_fixture
    ),
    CONSTRAINT ck_enrichment_daily_plan_times CHECK (created_at >= evaluated_at)
);

CREATE INDEX ix_enrichment_daily_plan_window_date
    ON enrichment_daily_plan (budget_window_id, competition_date DESC, id DESC);

CREATE TABLE enrichment_daily_admission (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES enrichment_daily_plan(id),
    canonical_fixture_id UUID NOT NULL REFERENCES canonical_fixture(id),
    admission_order SMALLINT NOT NULL,
    priority BOOLEAN NOT NULL,
    kickoff_at TIMESTAMPTZ NOT NULL,
    estimated_calls INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_enrichment_daily_admission_fixture UNIQUE (plan_id, canonical_fixture_id),
    CONSTRAINT uq_enrichment_daily_admission_order UNIQUE (plan_id, admission_order),
    CONSTRAINT ck_enrichment_daily_admission_order CHECK (admission_order BETWEEN 1 AND 7),
    CONSTRAINT ck_enrichment_daily_admission_calls CHECK (estimated_calls BETWEEN 1 AND 80)
);
ALTER TABLE enrichment_daily_admission
    ADD CONSTRAINT uq_enrichment_daily_admission_identity UNIQUE (id, canonical_fixture_id);

CREATE INDEX ix_enrichment_admission_fixture ON enrichment_daily_admission (canonical_fixture_id, created_at DESC, id DESC);

CREATE TABLE enrichment_plan_step (
    id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES enrichment_daily_admission(id),
    step_code VARCHAR(40) NOT NULL,
    family_scope VARCHAR(24) NOT NULL,
    scheduled_at TIMESTAMPTZ,
    condition_code VARCHAR(48) NOT NULL,
    trigger_observed_at TIMESTAMPTZ,
    trigger_evidence_observation_id UUID,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_enrichment_plan_step UNIQUE (admission_id, step_code),
    CONSTRAINT ck_enrichment_plan_step_code CHECK (step_code IN (
        'LINEUP_T_MINUS_30', 'LINEUP_T_MINUS_15', 'DETAIL_AT_KICKOFF', 'DETAIL_PLUS_45',
        'POSTMATCH_AFTER_FINAL', 'POSTMATCH_RECHECK_FINAL_PLUS_60'
    )),
    CONSTRAINT ck_enrichment_plan_step_family CHECK (family_scope IN ('LINEUP', 'DETAIL', 'POSTMATCH', 'POSTMATCH_RECHECK')),
    CONSTRAINT ck_enrichment_plan_step_condition CHECK (condition_code IN (
        'LINEUP_NOT_COMPLETE', 'SCHEDULED_KICKOFF_WINDOW', 'EXPLICIT_FINAL_STATUS', 'PRIORITY_AND_FINAL_STATUS'
    )),
    CONSTRAINT ck_enrichment_plan_step_status CHECK (status IN ('PLANNED', 'MISSED_WINDOW', 'SKIPPED', 'COMPLETED')),
    CONSTRAINT ck_enrichment_plan_step_due CHECK (
        (step_code IN ('POSTMATCH_AFTER_FINAL', 'POSTMATCH_RECHECK_FINAL_PLUS_60')
            AND ((scheduled_at IS NULL AND trigger_observed_at IS NULL AND trigger_evidence_observation_id IS NULL)
              OR (scheduled_at IS NOT NULL AND trigger_observed_at IS NOT NULL AND trigger_evidence_observation_id IS NOT NULL
                AND ((step_code = 'POSTMATCH_AFTER_FINAL' AND scheduled_at = trigger_observed_at)
                  OR (step_code = 'POSTMATCH_RECHECK_FINAL_PLUS_60' AND scheduled_at = trigger_observed_at + INTERVAL '60 minutes')))))
        OR (step_code NOT IN ('POSTMATCH_AFTER_FINAL', 'POSTMATCH_RECHECK_FINAL_PLUS_60')
            AND scheduled_at IS NOT NULL AND trigger_observed_at IS NULL AND trigger_evidence_observation_id IS NULL)
    )
);

CREATE INDEX ix_enrichment_plan_step_due ON enrichment_plan_step (scheduled_at, id)
    WHERE status = 'PLANNED' AND scheduled_at IS NOT NULL;

CREATE TABLE provider_enrichment_observation (
    id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES enrichment_daily_admission(id),
    canonical_fixture_id UUID NOT NULL REFERENCES canonical_fixture(id),
    budget_intent_id UUID NOT NULL REFERENCES provider_call_intent(id),
    raw_snapshot_id UUID NOT NULL REFERENCES raw_snapshot(id),
    provider VARCHAR(64) NOT NULL,
    provider_fixture_id VARCHAR(200) NOT NULL,
    logical_competition VARCHAR(64) NOT NULL,
    logical_season VARCHAR(64) NOT NULL,
    logical_phase VARCHAR(64) NOT NULL DEFAULT '',
    source_season_reference VARCHAR(128),
    source_phase_reference VARCHAR(128),
    family VARCHAR(24) NOT NULL,
    observation_state VARCHAR(24) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    representation_sha256 CHAR(64) NOT NULL,
    representation_json JSONB NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    source_observed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_provider_enrichment_observation_replay UNIQUE (
        budget_intent_id, provider_fixture_id, family, parser_version
    ),
    CONSTRAINT uq_provider_enrichment_observation_admission UNIQUE (id, admission_id),
    CONSTRAINT fk_provider_enrichment_admission_fixture
        FOREIGN KEY (admission_id, canonical_fixture_id)
        REFERENCES enrichment_daily_admission(id, canonical_fixture_id),
    CONSTRAINT ck_provider_enrichment_observation_text CHECK (
        provider = btrim(provider) AND provider <> '' AND provider !~ '[[:cntrl:]]'
        AND provider_fixture_id = btrim(provider_fixture_id) AND provider_fixture_id <> '' AND provider_fixture_id !~ '[[:cntrl:]]'
        AND logical_competition = btrim(logical_competition) AND logical_competition <> ''
        AND logical_season = btrim(logical_season) AND logical_season <> ''
        AND logical_phase = btrim(logical_phase) AND logical_phase !~ '[[:cntrl:]]'
        AND (source_season_reference IS NULL OR (source_season_reference = btrim(source_season_reference) AND source_season_reference !~ '[[:cntrl:]]'))
        AND (source_phase_reference IS NULL OR (source_phase_reference = btrim(source_phase_reference) AND source_phase_reference !~ '[[:cntrl:]]'))
        AND parser_version = btrim(parser_version) AND parser_version <> '' AND parser_version !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_provider_enrichment_observation_values CHECK (
        family IN ('MATCH_DETAIL', 'LINEUP', 'TEAM_STATS', 'EVENTS', 'PLAYER_STATS')
        AND observation_state IN ('NOT_PRESENT', 'NULL_VALUE', 'EMPTY', 'AVAILABLE', 'PARTIAL', 'INCOMPATIBLE')
        AND payload_sha256 ~ '^[0-9a-f]{64}$' AND representation_sha256 ~ '^[0-9a-f]{64}$'
        AND jsonb_typeof(representation_json) IN ('object', 'array')
        AND requested_at <= received_at AND created_at >= received_at
    )
);

ALTER TABLE raw_snapshot ADD CONSTRAINT uq_raw_snapshot_enrichment_provenance
    UNIQUE (id, provider, payload_sha256);
ALTER TABLE provider_enrichment_observation ADD CONSTRAINT fk_provider_enrichment_raw_provenance
    FOREIGN KEY (raw_snapshot_id, provider, payload_sha256)
    REFERENCES raw_snapshot (id, provider, payload_sha256);
ALTER TABLE enrichment_plan_step ADD CONSTRAINT fk_enrichment_plan_step_final_evidence
    FOREIGN KEY (trigger_evidence_observation_id, admission_id)
    REFERENCES provider_enrichment_observation (id, admission_id);

CREATE INDEX ix_provider_enrichment_observation_fixture
    ON provider_enrichment_observation (canonical_fixture_id, family, received_at DESC, id DESC);
CREATE INDEX ix_provider_enrichment_observation_raw
    ON provider_enrichment_observation (raw_snapshot_id, id);
CREATE INDEX ix_provider_enrichment_observation_admission
    ON provider_enrichment_observation (admission_id, received_at DESC, id DESC);

CREATE TABLE enrichment_quality_finding (
    id UUID PRIMARY KEY,
    enrichment_observation_id UUID NOT NULL REFERENCES provider_enrichment_observation(id),
    issue_code VARCHAR(64) NOT NULL,
    entity_scope VARCHAR(24) NOT NULL,
    provider_entity_id VARCHAR(200),
    detected_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_enrichment_quality_finding_code CHECK (issue_code IN (
        'MISSING_PLAYER_FULL_NAME', 'ZERO_MINUTE_EXPECTED_METRICS', 'INVALID_SECOND_YELLOW_VALUE',
        'MISSING_PLAYER_YELLOW_CARD', 'CROSS_ENDPOINT_PLAYER_ID_MISMATCH',
        'EMPTY_RESPONSE_WITH_HISTORICAL_REGRESSION', 'FINAL_SCORE_CONFLICT'
    )),
    CONSTRAINT ck_enrichment_quality_finding_scope CHECK (entity_scope IN ('FIXTURE', 'TEAM', 'PLAYER', 'EVENT', 'METRIC')),
    CONSTRAINT ck_enrichment_quality_finding_entity CHECK (
        provider_entity_id IS NULL OR (provider_entity_id = btrim(provider_entity_id)
            AND provider_entity_id <> '' AND provider_entity_id !~ '[[:cntrl:]]')
    )
);

CREATE UNIQUE INDEX uq_enrichment_quality_finding_replay
    ON enrichment_quality_finding (enrichment_observation_id, issue_code, entity_scope, provider_entity_id) NULLS NOT DISTINCT;
CREATE INDEX ix_enrichment_quality_finding_observation
    ON enrichment_quality_finding (enrichment_observation_id, detected_at, id);
CREATE INDEX ix_enrichment_quality_finding_code
    ON enrichment_quality_finding (issue_code, detected_at DESC, id DESC);
