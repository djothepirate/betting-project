package com.bettingproject.collection.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.collection.application.control.CollectionQueryPort;
import com.bettingproject.shared.adapter.persistence.ReadSql;
import com.bettingproject.shared.application.ReadPage;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import static com.bettingproject.shared.adapter.persistence.ReadSql.time;

@Repository
@Profile("control-api")
public class JdbcCollectionQueryAdapter implements CollectionQueryPort {
    private final JdbcClient jdbc;
    public JdbcCollectionQueryAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    private static final String WINDOWS = """
            SELECT w.id, s.provider, w.starts_at, w.ends_at, w.state, w.version, w.capacity,
                w.project_limit, w.reserve, w.shared_initial, w.project_initial, w.cadence_limit,
                w.cadence_period_ms, w.quota_inconsistent, w.created_at, w.updated_at,
                o.remaining, o.observed_at, o.valid_until,
                (SELECT count(*) FROM provider_call_intent i WHERE i.window_id=w.id AND i.state='RESERVED') AS reserved,
                (SELECT count(*) FROM provider_call_intent i WHERE i.window_id=w.id
                    AND i.state NOT IN ('RESERVED','RELEASED')) AS committed
            FROM provider_budget_window w JOIN provider_budget_scope s ON s.id=w.scope_id
            LEFT JOIN provider_quota_observation o ON o.id=w.current_observation_id
            """;
    private static final String CALENDARS = """
            SELECT c.id, c.window_id, c.provider, c.provider_competition_id, c.source_season,
                c.source_phase, c.collection_date, c.status, c.reason_code, c.registry_sha256,
                c.created_at, c.updated_at FROM calendar_collection c
            """;

    public List<WindowView> windows(Filter filter, ReadPage.Request page) {
        return new ReadSql(WINDOWS).filter("s.provider", filter.provider()).filter("w.state", filter.status())
                .filter("w.id", filter.windowId()).page("w.updated_at", "w.id", page).statement(jdbc).query(this::mapWindow).list();
    }
    public Optional<WindowView> window(UUID id) {
        return new ReadSql(WINDOWS).filter("w.id", id).statement(jdbc).query(this::mapWindow).optional();
    }
    public List<IntentView> intents(UUID windowId, String state, ReadPage.Request page) {
        return new ReadSql("""
                SELECT i.id, i.window_id, i.logical_endpoint, i.state, i.version, i.committed_at,
                    i.result_at, i.http_status, i.created_at, i.updated_at FROM provider_call_intent i
                """).filter("i.window_id", windowId).filter("i.state", state).page("i.created_at", "i.id", page)
                .statement(jdbc).query((rs,n) -> new IntentView(id(rs), uuid(rs,"window_id"), rs.getString("logical_endpoint"),
                    rs.getString("state"), rs.getLong("version"), time(rs,"committed_at"), time(rs,"result_at"),
                    rs.getObject("http_status",Integer.class), time(rs,"created_at"),time(rs,"updated_at"))).list();
    }
    public List<IncidentView> incidents(Filter filter, ReadPage.Request page) {
        return new ReadSql("""
                SELECT i.id, i.window_id, i.intent_id, s.provider, i.code, i.created_at
                FROM provider_budget_incident i JOIN provider_budget_window w ON w.id=i.window_id
                JOIN provider_budget_scope s ON s.id=w.scope_id
                """).filter("s.provider",filter.provider()).filter("i.window_id",filter.windowId()).filter("i.code",filter.code())
                .page("i.created_at","i.id",page).statement(jdbc).query((rs,n) -> new IncidentView(id(rs),uuid(rs,"window_id"),
                    uuid(rs,"intent_id"),rs.getString("provider"),rs.getString("code"),time(rs,"created_at"))).list();
    }
    public List<BudgetEventView> budgetEvents(UUID windowId, ReadPage.Request page) {
        return new ReadSql("SELECT e.id, e.window_id, e.intent_id, e.type, e.reason_code, e.created_at FROM provider_budget_event e")
                .filter("e.window_id",windowId).page("e.created_at","e.id",page).statement(jdbc)
                .query((rs,n) -> new BudgetEventView(id(rs),uuid(rs,"window_id"),uuid(rs,"intent_id"),
                    rs.getString("type"),rs.getString("reason_code"),time(rs,"created_at"))).list();
    }
    public List<CalendarView> calendars(Filter filter, LocalDate date, ReadPage.Request page) {
        return new ReadSql(CALENDARS).filter("c.provider",filter.provider()).filter("c.window_id",filter.windowId())
                .filter("c.status",filter.status()).filter("c.collection_date",date)
                .page("c.created_at","c.id",page).statement(jdbc).query(this::mapCalendar).list();
    }
    public Optional<CalendarView> calendar(UUID id) {
        return new ReadSql(CALENDARS).filter("c.id",id).statement(jdbc).query(this::mapCalendar).optional();
    }
    public List<PageView> pages(UUID collectionId, ReadPage.Request page) {
        return new ReadSql("""
                SELECT p.id, p.collection_id, p.intent_id, p.audit_id, p.page_number, p.response_code,
                    p.http_status, p.quota_remaining, p.raw_snapshot_id, p.raw_sha256, p.connector_version,
                    p.requested_at, p.received_at FROM calendar_collection_page p
                """).filter("p.collection_id",collectionId).page("p.requested_at","p.id",page).statement(jdbc)
                .query((rs,n) -> new PageView(id(rs),uuid(rs,"collection_id"),uuid(rs,"intent_id"),uuid(rs,"audit_id"),
                    rs.getInt("page_number"),rs.getString("response_code"),rs.getObject("http_status",Integer.class),
                    rs.getObject("quota_remaining",Long.class),uuid(rs,"raw_snapshot_id"),rs.getString("raw_sha256"),
                    rs.getString("connector_version"),time(rs,"requested_at"),time(rs,"received_at"))).list();
    }
    private WindowView mapWindow(ResultSet rs,int n) throws SQLException {
        return new WindowView(id(rs),rs.getString("provider"),time(rs,"starts_at"),time(rs,"ends_at"),rs.getString("state"),
            rs.getLong("version"),rs.getObject("capacity",Long.class),rs.getLong("project_limit"),rs.getLong("reserve"),
            rs.getLong("shared_initial"),rs.getLong("project_initial"),rs.getObject("cadence_limit",Integer.class),
            rs.getObject("cadence_period_ms",Long.class),rs.getBoolean("quota_inconsistent"),rs.getObject("remaining",Long.class),
            time(rs,"observed_at"),time(rs,"valid_until"),rs.getLong("reserved"),rs.getLong("committed"),
            time(rs,"created_at"),time(rs,"updated_at"));
    }
    private CalendarView mapCalendar(ResultSet rs,int n) throws SQLException {
        return new CalendarView(id(rs),uuid(rs,"window_id"),rs.getString("provider"),rs.getString("provider_competition_id"),
            rs.getString("source_season"),rs.getString("source_phase"),rs.getObject("collection_date",LocalDate.class),
            rs.getString("status"),rs.getString("reason_code"),rs.getString("registry_sha256"),time(rs,"created_at"),time(rs,"updated_at"));
    }
    private static UUID id(ResultSet rs) throws SQLException { return uuid(rs,"id"); }
    private static UUID uuid(ResultSet rs,String name) throws SQLException { return rs.getObject(name,UUID.class); }
}
