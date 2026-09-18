package com.bettingproject.collection.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.application.SnapshotStore;
import com.bettingproject.collection.application.calendar.CalendarCollectionRecord;
import com.bettingproject.collection.application.calendar.CalendarCollectionStore;
import com.bettingproject.collection.application.calendar.CalendarDerivationRecord;
import com.bettingproject.collection.application.calendar.CalendarPageRecord;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcCalendarCollectionStore implements CalendarCollectionStore {
    private final JdbcClient jdbc;
    private final SnapshotStore snapshots;

    public JdbcCalendarCollectionStore(JdbcClient jdbc, SnapshotStore snapshots) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
    }

    @Override
    @Transactional
    public StoredCollection createAndResolve(CalendarCollectionRecord collection) {
        if (!windowMatchesProvider(collection.windowId(), collection.capability().provider())) {
            throw new IllegalStateException("Collection and budget provider differ");
        }
        int inserted = jdbc.sql("""
                INSERT INTO calendar_collection (
                    id, window_id, provider, provider_competition_id, source_season, source_phase,
                    data_type, collection_date, season_start_year, command_sha256, registry_sha256,
                    status, reason_code, created_at, updated_at
                ) VALUES (
                    :id, :windowId, :provider, :competition, :season, :phase, :dataType, :date,
                    :year, :commandHash, :registryHash, :status, :reason, :createdAt, :updatedAt
                ) ON CONFLICT (id) DO NOTHING
                """)
                .param("id", collection.id()).param("windowId", collection.windowId())
                .param("provider", collection.capability().provider())
                .param("competition", collection.capability().providerCompetitionId())
                .param("season", collection.capability().sourceSeason())
                .param("phase", collection.capability().sourcePhase())
                .param("dataType", collection.capability().dataType().name())
                .param("date", collection.date()).param("year", collection.seasonStartYear())
                .param("commandHash", collection.commandSha256()).param("registryHash", collection.registrySha256())
                .param("status", collection.status()).param("reason", collection.reasonCode())
                .param("createdAt", utc(collection.createdAt())).param("updatedAt", utc(collection.updatedAt()))
                .update();
        return new StoredCollection(findCollection(collection.id()).orElseThrow(), inserted == 1);
    }

    @Override
    public Optional<CalendarCollectionRecord> findCollection(UUID id) {
        return jdbc.sql("SELECT * FROM calendar_collection WHERE id = :id")
                .param("id", id).query(this::collection).optional();
    }

    @Override
    @Transactional
    public void finish(UUID id, String status, String reason, Instant now) {
        if (!List.of("COMPLETED", "INCOMPLETE").contains(status)) {
            throw new IllegalArgumentException("Invalid terminal collection state");
        }
        int changed = jdbc.sql("""
                UPDATE calendar_collection SET status = :status, reason_code = :reason, updated_at = :now
                WHERE id = :id AND status = 'RUNNING'
                """).param("id", id).param("status", status).param("reason", reason)
                .param("now", utc(now)).update();
        if (changed != 1) {
            throw new IllegalStateException("Collection is not running");
        }
    }

    @Override
    @Transactional
    public CalendarPageRecord preparePage(UUID collectionId, UUID intentId, int pageNumber,
            int pageOffset, int pageLimit, String connectorVersion, Instant now) {
        UUID intentWindow = jdbc.sql("SELECT window_id FROM provider_call_intent WHERE id = :id FOR UPDATE")
                .param("id", intentId).query(UUID.class).single();
        CalendarCollectionRecord collection = findCollection(collectionId).orElseThrow();
        if (!collection.windowId().equals(intentWindow) || !"RUNNING".equals(collection.status())) {
            throw new IllegalStateException("Collection and intention are incompatible");
        }
        UUID pageId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        int inserted = jdbc.sql("""
                INSERT INTO calendar_collection_page (
                    id, collection_id, window_id, intent_id, audit_id, page_number,
                    page_offset, page_limit, connector_version, requested_at, response_code
                ) VALUES (:id, :collection, :window, :intent, :audit, :number,
                          :offset, :limit, :connector, :now, 'PENDING')
                ON CONFLICT (intent_id) DO NOTHING
                """).param("id", pageId).param("collection", collectionId).param("window", intentWindow)
                .param("intent", intentId).param("audit", auditId).param("number", pageNumber)
                .param("offset", pageOffset).param("limit", pageLimit).param("connector", connectorVersion)
                .param("now", utc(now)).update();
        if (inserted == 1) {
            jdbc.sql("""
                    INSERT INTO provider_call_audit (
                        id, provider, logical_endpoint, requested_at, connector_version, created_at
                    ) VALUES (:id, :provider, 'calendar/matches', :now, :connector, :now)
                    """).param("id", auditId).param("provider", collection.capability().provider())
                    .param("now", utc(now)).param("connector", connectorVersion).update();
            jdbc.sql("""
                    INSERT INTO outbox_message (
                        id, idempotency_key, aggregate_type, aggregate_id, destination,
                        payload_json, status, attempt_count, created_at, updated_at
                    ) VALUES (
                        :id, :key, 'calendar_call', :pageId, 'PROVIDER_CALENDAR',
                        jsonb_build_object('pageId', CAST(:pageId AS text), 'intentId', CAST(:intentId AS text)),
                        'PENDING', 0, :now, :now
                    )
                    """).param("id", UUID.randomUUID()).param("key", outboxKey(intentId))
                    .param("pageId", pageId).param("intentId", intentId).param("now", utc(now)).update();
        }
        CalendarPageRecord stored = jdbc.sql("SELECT * FROM calendar_collection_page WHERE intent_id = :intent")
                .param("intent", intentId).query(this::page).single();
        if (!stored.collectionId().equals(collectionId) || stored.pageNumber() != pageNumber
                || stored.pageOffset() != pageOffset || stored.pageLimit() != pageLimit
                || !stored.connectorVersion().equals(connectorVersion)) {
            throw new IllegalStateException("Page idempotency conflict");
        }
        return stored;
    }

    @Override
    @Transactional
    public void recordResponse(UUID pageId, RawSnapshot snapshot, Instant requestedAt,
            Instant receivedAt, Integer httpStatus, Long quotaRemaining, String responseCode) {
        CalendarPageRecord page = jdbc.sql("SELECT * FROM calendar_collection_page WHERE id = :id FOR UPDATE")
                .param("id", pageId).query(this::page).single();
        String hash = snapshot == null ? null : snapshot.sha256();
        if (!"PENDING".equals(page.responseCode())) {
            if (!Objects.equals(page.rawSha256(), hash) || !Objects.equals(page.requestedAt(), requestedAt)
                    || !Objects.equals(page.receivedAt(), receivedAt) || !Objects.equals(page.httpStatus(), httpStatus)
                    || !Objects.equals(page.quotaRemaining(), quotaRemaining) || !page.responseCode().equals(responseCode)) {
                throw new IllegalStateException("Response idempotency conflict");
            }
            return;
        }
        if (snapshot != null) {
            CalendarCollectionRecord collection = findCollection(page.collectionId()).orElseThrow();
            if (!snapshot.provider().equals(collection.capability().provider())
                    || !snapshot.endpoint().equals("calendar/matches")
                    || !snapshot.connectorVersion().equals(page.connectorVersion())
                    || !snapshot.receivedAt().equals(receivedAt)) {
                throw new IllegalStateException("Snapshot provenance mismatch");
            }
        }
        UUID snapshotId = snapshot == null ? null : snapshots.storeAndResolve(snapshot).id();
        long latency = Duration.between(requestedAt, receivedAt).toMillis();
        jdbc.sql("""
                UPDATE calendar_collection_page
                SET raw_snapshot_id = :snapshot, raw_sha256 = :hash, requested_at = :requested,
                    received_at = :received, http_status = :status, quota_remaining = :quota,
                    response_code = :code
                WHERE id = :id AND response_code = 'PENDING'
                """).param("snapshot", snapshotId).param("hash", hash).param("requested", utc(requestedAt))
                .param("received", utc(receivedAt)).param("status", httpStatus).param("quota", quotaRemaining)
                .param("code", responseCode).param("id", pageId).update();
        jdbc.sql("""
                UPDATE provider_call_audit
                SET requested_at = :requested, received_at = :received, http_status = :status,
                    latency_ms = :latency, quota_remaining = :quota, payload_sha256 = :hash, error_code = :error
                WHERE id = :id
                """).param("requested", utc(requestedAt)).param("received", utc(receivedAt))
                .param("status", httpStatus).param("latency", latency).param("quota", quotaRemaining)
                .param("hash", hash).param("error", "RECEIVED".equals(responseCode) ? null : responseCode)
                .param("id", page.auditId()).update();
        boolean complete = "RECEIVED".equals(responseCode) || "HTTP_ERROR".equals(responseCode);
        int outboxChanged = jdbc.sql("""
                UPDATE outbox_message SET status = :status, attempt_count = 1,
                    delivered_at = :delivered, last_error_code = :error, updated_at = :now
                WHERE idempotency_key = :key AND status = 'PENDING'
                """).param("status", complete ? "DELIVERED" : "FAILED")
                .param("delivered", complete ? utc(receivedAt) : null)
                .param("error", "RECEIVED".equals(responseCode) ? null : responseCode)
                .param("now", utc(receivedAt)).param("key", outboxKey(page.intentId())).update();
        if (outboxChanged != 1) {
            throw new IllegalStateException("Calendar outbox is not pending");
        }
    }

    @Override
    public List<CalendarPageRecord> pages(UUID collectionId) {
        return jdbc.sql("SELECT * FROM calendar_collection_page WHERE collection_id = :id ORDER BY page_number")
                .param("id", collectionId).query(this::page).list();
    }

    @Override
    public Optional<CalendarPageRecord> findPage(UUID id) {
        return jdbc.sql("SELECT * FROM calendar_collection_page WHERE id = :id")
                .param("id", id).query(this::page).optional();
    }

    @Override
    public Optional<RawSnapshot> readRaw(UUID snapshotId) {
        return jdbc.sql("SELECT * FROM raw_snapshot WHERE id = :id").param("id", snapshotId)
                .query((rs, row) -> {
                    if (!"identity".equals(rs.getString("payload_compression"))) {
                        throw new IllegalStateException("Unsupported raw snapshot compression");
                    }
                    return new RawSnapshot(rs.getString("provider"), rs.getString("endpoint"),
                            instant(rs, "received_at"), rs.getBytes("payload"), rs.getString("payload_sha256"),
                            rs.getString("connector_version"));
                }).optional();
    }

    @Override
    @Transactional
    public void appendDerivation(CalendarDerivationRecord derivation) {
        jdbc.sql("""
                INSERT INTO calendar_collection_derivation (
                    id, page_id, parser_version, outcome, derived_snapshot_id, raw_sha256, evaluated_at
                ) VALUES (:id, :page, :parser, :outcome, :snapshot, :hash, :evaluated)
                """).param("id", derivation.id()).param("page", derivation.pageId())
                .param("parser", derivation.parserVersion()).param("outcome", derivation.outcome())
                .param("snapshot", derivation.derivedSnapshotId()).param("hash", derivation.rawSha256())
                .param("evaluated", utc(derivation.evaluatedAt())).update();
    }

    @Override
    public List<CalendarDerivationRecord> derivations(UUID pageId) {
        return jdbc.sql("""
                SELECT * FROM calendar_collection_derivation WHERE page_id = :id ORDER BY evaluated_at, id
                """).param("id", pageId).query((rs, row) -> new CalendarDerivationRecord(
                        rs.getObject("id", UUID.class), rs.getObject("page_id", UUID.class),
                        rs.getString("parser_version"), rs.getString("outcome"),
                        rs.getObject("derived_snapshot_id", UUID.class), rs.getString("raw_sha256"),
                        instant(rs, "evaluated_at"))).list();
    }

    @Override
    public boolean windowMatchesProvider(UUID windowId, String provider) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM provider_budget_window w
                    JOIN provider_budget_scope s ON s.id = w.scope_id
                    WHERE w.id = :id AND s.provider = :provider
                )
                """).param("id", windowId).param("provider", provider).query(Boolean.class).single();
    }

    @Override
    @Transactional
    public void markMissingResponse(UUID pageId, Instant now) {
        CalendarPageRecord page = jdbc.sql("SELECT * FROM calendar_collection_page WHERE id = :id FOR UPDATE")
                .param("id", pageId).query(this::page).single();
        if (!"PENDING".equals(page.responseCode())) { return; }
        jdbc.sql("UPDATE calendar_collection_page SET response_code = 'SEND_UNCERTAIN' WHERE id = :id")
                .param("id", pageId).update();
        jdbc.sql("UPDATE provider_call_audit SET error_code = 'SEND_UNCERTAIN' WHERE id = :id")
                .param("id", page.auditId()).update();
        int changed = jdbc.sql("""
                UPDATE outbox_message SET status = 'FAILED', attempt_count = 1,
                    last_error_code = 'SEND_UNCERTAIN', updated_at = :now
                WHERE idempotency_key = :key AND status = 'PENDING'
                """).param("key", outboxKey(page.intentId())).param("now", utc(now)).update();
        if (changed != 1) { throw new IllegalStateException("Calendar outbox invariant"); }
    }

    private CalendarCollectionRecord collection(ResultSet rs, int row) throws SQLException {
        return new CalendarCollectionRecord(rs.getObject("id", UUID.class), rs.getObject("window_id", UUID.class),
                new ProviderCapabilityKey(rs.getString("provider"), rs.getString("provider_competition_id"),
                        rs.getString("source_season"), rs.getString("source_phase"),
                        CapabilityDataType.valueOf(rs.getString("data_type"))),
                rs.getDate("collection_date").toLocalDate(), rs.getInt("season_start_year"),
                rs.getString("command_sha256"), rs.getString("registry_sha256"), rs.getString("status"),
                rs.getString("reason_code"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private CalendarPageRecord page(ResultSet rs, int row) throws SQLException {
        return new CalendarPageRecord(rs.getObject("id", UUID.class), rs.getObject("collection_id", UUID.class),
                rs.getObject("intent_id", UUID.class), rs.getObject("audit_id", UUID.class), rs.getInt("page_number"),
                rs.getInt("page_offset"), rs.getInt("page_limit"), rs.getString("connector_version"),
                rs.getObject("raw_snapshot_id", UUID.class), rs.getString("raw_sha256"),
                instant(rs, "requested_at"), instant(rs, "received_at"), rs.getObject("http_status", Integer.class),
                rs.getObject("quota_remaining", Long.class), rs.getString("response_code"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static String outboxKey(UUID intentId) {
        return "calendar-call:" + intentId;
    }
}
