package com.bettingproject.collection.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.application.budget.ProviderBudgetRepository;
import com.bettingproject.collection.domain.budget.BudgetModel.Event;
import com.bettingproject.collection.domain.budget.BudgetModel.Incident;
import com.bettingproject.collection.domain.budget.BudgetModel.Intent;
import com.bettingproject.collection.domain.budget.BudgetModel.IntentState;
import com.bettingproject.collection.domain.budget.BudgetModel.ObservationDisposition;
import com.bettingproject.collection.domain.budget.BudgetModel.Proof;
import com.bettingproject.collection.domain.budget.BudgetModel.QuotaObservation;
import com.bettingproject.collection.domain.budget.BudgetModel.Scope;
import com.bettingproject.collection.domain.budget.BudgetModel.Window;
import com.bettingproject.collection.domain.budget.BudgetModel.WindowState;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** PostgreSQL persistence; all locks are held by the caller's budget transaction. */
@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcProviderBudgetRepository implements ProviderBudgetRepository {

    private final JdbcClient jdbc;

    public JdbcProviderBudgetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Scope createAndLockScope(String provider, String accountRef, Instant now) {
        jdbc.sql("""
                INSERT INTO provider_budget_scope (id, provider, account_ref, created_at)
                VALUES (:id, :provider, :account, :now)
                ON CONFLICT (provider, account_ref) DO NOTHING
                """)
                .param("id", UUID.randomUUID()).param("provider", provider)
                .param("account", accountRef).param("now", utc(now)).update();
        // A separate statement sees the winner after waiting for an insert conflict.
        return jdbc.sql("""
                SELECT id, provider, account_ref, created_at FROM provider_budget_scope
                WHERE provider = :provider AND account_ref = :account FOR UPDATE
                """).param("provider", provider).param("account", accountRef)
                .query((rs, row) -> new Scope(uuid(rs, "id"), rs.getString("provider"),
                        rs.getString("account_ref"), instant(rs, "created_at"))).single();
    }

    @Override
    public void lockScope(UUID scopeId) {
        jdbc.sql("SELECT id FROM provider_budget_scope WHERE id = :id FOR UPDATE")
                .param("id", scopeId).query(UUID.class).single();
    }

    @Override
    public void lockWindow(UUID windowId) {
        jdbc.sql("SELECT id FROM provider_budget_window WHERE id = :id FOR UPDATE")
                .param("id", windowId).query(UUID.class).single();
    }

    @Override
    public void lockIntent(UUID intentId) {
        jdbc.sql("SELECT id FROM provider_call_intent WHERE id = :id FOR UPDATE")
                .param("id", intentId).query(UUID.class).single();
    }

    @Override
    public Optional<Window> findWindow(UUID id) {
        return jdbc.sql("SELECT * FROM provider_budget_window WHERE id = :id")
                .param("id", id).query(this::window).optional();
    }

    @Override
    public List<Window> findWindows(UUID scopeId) {
        return jdbc.sql("SELECT * FROM provider_budget_window WHERE scope_id = :id ORDER BY starts_at, id")
                .param("id", scopeId).query(this::window).list();
    }

    @Override
    public void insertWindow(Window window) {
        requireInitial(window.version());
        jdbc.sql("""
                INSERT INTO provider_budget_window (
                    id, scope_id, starts_at, ends_at, capacity, project_limit, reserve,
                    shared_initial, project_initial, cadence_limit, cadence_period_ms, state,
                    current_observation_id, quota_inconsistent, version, created_at, updated_at,
                    proof_logical_id, proof_sha256
                ) VALUES (
                    :id, :scope, :starts, :ends, :capacity, :projectLimit, :reserve,
                    :sharedInitial, :projectInitial, :cadenceLimit, :cadencePeriod, :state,
                    :observation, :inconsistent, :version, :created, :updated, :proofId, :proofHash
                )
                """)
                .param("id", window.id()).param("scope", window.scopeId())
                .param("starts", utc(window.startsAt())).param("ends", utc(window.endsAt()))
                .param("capacity", window.capacity()).param("projectLimit", window.projectLimit())
                .param("reserve", window.reserve()).param("sharedInitial", window.sharedInitial())
                .param("projectInitial", window.projectInitial()).param("cadenceLimit", window.cadenceLimit())
                .param("cadencePeriod", window.cadencePeriod() == null ? null : window.cadencePeriod().toMillis())
                .param("state", window.state().name()).param("observation", window.currentObservationId())
                .param("inconsistent", window.quotaInconsistent()).param("version", window.version())
                .param("created", utc(window.createdAt())).param("updated", utc(window.updatedAt()))
                .param("proofId", window.proof().logicalId()).param("proofHash", window.proof().sha256())
                .update();
    }

    @Override
    public boolean updateWindow(Window window, long expectedVersion) {
        requireNextVersion(window.version(), expectedVersion);
        return jdbc.sql("""
                UPDATE provider_budget_window
                SET state = :state, current_observation_id = :observation,
                    quota_inconsistent = :inconsistent, version = :version, updated_at = :updated
                WHERE id = :id AND version = :expected
                """)
                .param("state", window.state().name()).param("observation", window.currentObservationId())
                .param("inconsistent", window.quotaInconsistent()).param("version", window.version())
                .param("updated", utc(window.updatedAt())).param("id", window.id())
                .param("expected", expectedVersion).update() == 1;
    }

    @Override
    public Optional<Intent> findIntent(UUID id) {
        return jdbc.sql("SELECT * FROM provider_call_intent WHERE id = :id")
                .param("id", id).query(this::intent).optional();
    }

    @Override
    public Optional<Intent> findIntentByKey(String idempotencyKey) {
        return jdbc.sql("SELECT * FROM provider_call_intent WHERE idempotency_key = :key")
                .param("key", idempotencyKey).query(this::intent).optional();
    }

    @Override
    public Intent insertIntentIfAbsentAndResolve(Intent intent) {
        requireInitial(intent.version());
        jdbc.sql("""
                INSERT INTO provider_call_intent (
                    id, window_id, idempotency_key, logical_endpoint, request_sha256, state,
                    version, created_at, updated_at, committed_at, result_at, http_status, result_fingerprint
                ) VALUES (
                    :id, :window, :key, :endpoint, :hash, :state,
                    :version, :created, :updated, :committed, :result, :httpStatus, :fingerprint
                ) ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .param("id", intent.id()).param("window", intent.windowId())
                .param("key", intent.idempotencyKey()).param("endpoint", intent.logicalEndpoint())
                .param("hash", intent.requestSha256()).param("state", intent.state().name())
                .param("version", intent.version()).param("created", utc(intent.createdAt()))
                .param("updated", utc(intent.updatedAt())).param("committed", utc(intent.committedAt()))
                .param("result", utc(intent.resultAt())).param("httpStatus", intent.httpStatus())
                .param("fingerprint", intent.resultFingerprint()).update();
        return findIntentByKey(intent.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Budget intention could not be resolved"));
    }

    @Override
    public boolean updateIntent(Intent intent, long expectedVersion) {
        requireNextVersion(intent.version(), expectedVersion);
        return jdbc.sql("""
                UPDATE provider_call_intent
                SET state = :state, version = :version, updated_at = :updated,
                    committed_at = :committed, result_at = :result,
                    http_status = :httpStatus, result_fingerprint = :fingerprint
                WHERE id = :id AND version = :expected
                """)
                .param("state", intent.state().name()).param("version", intent.version())
                .param("updated", utc(intent.updatedAt())).param("committed", utc(intent.committedAt()))
                .param("result", utc(intent.resultAt())).param("httpStatus", intent.httpStatus())
                .param("fingerprint", intent.resultFingerprint()).param("id", intent.id())
                .param("expected", expectedVersion).update() == 1;
    }

    @Override
    public List<Intent> findWindowIntents(UUID windowId) {
        return jdbc.sql("SELECT * FROM provider_call_intent WHERE window_id = :id ORDER BY created_at, id")
                .param("id", windowId).query(this::intent).list();
    }

    @Override
    public List<Intent> findScopeIntents(UUID scopeId) {
        return jdbc.sql("""
                SELECT i.* FROM provider_call_intent i
                JOIN provider_budget_window w ON w.id = i.window_id
                WHERE w.scope_id = :scope ORDER BY i.created_at, i.id
                """).param("scope", scopeId).query(this::intent).list();
    }

    @Override
    public Optional<QuotaObservation> findObservation(UUID id) {
        var covered = new HashSet<>(jdbc.sql("""
                SELECT intent_id FROM provider_quota_observation_intent WHERE observation_id = :id
                """).param("id", id).query(UUID.class).list());
        return jdbc.sql("SELECT * FROM provider_quota_observation WHERE id = :id")
                .param("id", id).query((rs, row) -> new QuotaObservation(
                        uuid(rs, "id"), uuid(rs, "window_id"), rs.getLong("remaining"),
                        instant(rs, "observed_at"), instant(rs, "valid_until"),
                        new Proof(rs.getString("proof_logical_id"), rs.getString("proof_sha256")), covered,
                        ObservationDisposition.valueOf(rs.getString("disposition")), instant(rs, "created_at")))
                .optional();
    }

    @Override
    public void insertObservation(QuotaObservation observation) {
        jdbc.sql("""
                INSERT INTO provider_quota_observation (
                    id, window_id, remaining, observed_at, valid_until,
                    proof_logical_id, proof_sha256, disposition, created_at
                ) VALUES (:id, :window, :remaining, :observed, :valid, :proofId, :proofHash, :disposition, :created)
                """)
                .param("id", observation.id()).param("window", observation.windowId())
                .param("remaining", observation.remaining()).param("observed", utc(observation.observedAt()))
                .param("valid", utc(observation.validUntil())).param("proofId", observation.proof().logicalId())
                .param("proofHash", observation.proof().sha256()).param("disposition", observation.disposition().name())
                .param("created", utc(observation.createdAt())).update();
        for (UUID intentId : observation.coveredIntentIds()) {
            jdbc.sql("""
                    INSERT INTO provider_quota_observation_intent (observation_id, window_id, intent_id)
                    VALUES (:observation, :window, :intent)
                    """).param("observation", observation.id()).param("window", observation.windowId())
                    .param("intent", intentId).update();
        }
    }

    @Override
    public void insertIncident(Incident incident) {
        jdbc.sql("""
                INSERT INTO provider_budget_incident (id, window_id, intent_id, code, created_at)
                VALUES (:id, :window, :intent, :code, :created)
                """).param("id", incident.id()).param("window", incident.windowId())
                .param("intent", incident.intentId()).param("code", incident.code())
                .param("created", utc(incident.createdAt())).update();
    }

    @Override
    public List<Incident> findIncidents(UUID windowId) {
        return jdbc.sql("SELECT * FROM provider_budget_incident WHERE window_id = :window ORDER BY created_at, id")
                .param("window", windowId).query((rs, row) -> new Incident(uuid(rs, "id"),
                        uuid(rs, "window_id"), uuid(rs, "intent_id"), rs.getString("code"),
                        instant(rs, "created_at"))).list();
    }

    @Override
    public void appendEvent(Event event) {
        jdbc.sql("""
                INSERT INTO provider_budget_event (
                    id, scope_id, window_id, intent_id, type, reason_code, operator_id,
                    justification, proof_logical_id, proof_sha256, created_at
                ) VALUES (
                    :id, :scope, :window, :intent, :type, :reason, :operator,
                    :justification, :proofId, :proofHash, :created
                )
                """).param("id", event.id()).param("scope", event.scopeId())
                .param("window", event.windowId()).param("intent", event.intentId())
                .param("type", event.type()).param("reason", event.reasonCode())
                .param("operator", event.operatorId()).param("justification", event.justification())
                .param("proofId", event.proof() == null ? null : event.proof().logicalId())
                .param("proofHash", event.proof() == null ? null : event.proof().sha256())
                .param("created", utc(event.createdAt())).update();
    }

    private Window window(ResultSet rs, int row) throws SQLException {
        Long cadenceMillis = rs.getObject("cadence_period_ms", Long.class);
        return new Window(uuid(rs, "id"), uuid(rs, "scope_id"), instant(rs, "starts_at"),
                instant(rs, "ends_at"), rs.getObject("capacity", Long.class), rs.getLong("project_limit"),
                rs.getLong("reserve"), rs.getLong("shared_initial"), rs.getLong("project_initial"),
                rs.getObject("cadence_limit", Integer.class), cadenceMillis == null ? null : Duration.ofMillis(cadenceMillis),
                WindowState.valueOf(rs.getString("state")), uuid(rs, "current_observation_id"),
                rs.getBoolean("quota_inconsistent"), rs.getLong("version"), instant(rs, "created_at"),
                instant(rs, "updated_at"), new Proof(rs.getString("proof_logical_id"), rs.getString("proof_sha256")));
    }

    private Intent intent(ResultSet rs, int row) throws SQLException {
        return new Intent(uuid(rs, "id"), uuid(rs, "window_id"), rs.getString("idempotency_key"),
                rs.getString("logical_endpoint"), rs.getString("request_sha256"),
                IntentState.valueOf(rs.getString("state")), rs.getLong("version"), instant(rs, "created_at"),
                instant(rs, "updated_at"), instant(rs, "committed_at"), instant(rs, "result_at"),
                rs.getObject("http_status", Integer.class), rs.getString("result_fingerprint"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static void requireInitial(long version) {
        if (version != 1) {
            throw new IllegalArgumentException("A new budget projection must have initial version 1");
        }
    }

    private static void requireNextVersion(long next, long expected) {
        if (expected < 1 || expected == Long.MAX_VALUE || next != expected + 1) {
            throw new IllegalArgumentException("A budget mutation must advance exactly one version");
        }
    }
}
