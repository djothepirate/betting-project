package com.bettingproject.shared.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import com.bettingproject.shared.application.ReadPage;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Adapter-only helper; SQL fragments and column names are constants chosen by adapters. */
public final class ReadSql {
    private final StringBuilder sql;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    public ReadSql(String base) { sql = new StringBuilder(base).append(" WHERE 1=1"); }
    public ReadSql condition(String clause) { sql.append(" AND ").append(clause); return this; }
    public ReadSql filter(String column, Object value) {
        if (value != null) {
            String name = "filter" + parameters.size();
            sql.append(" AND ").append(column).append(" = :").append(name);
            parameters.put(name, value instanceof Enum<?> e ? e.name() : value);
        }
        return this;
    }
    public ReadSql page(String time, String id, ReadPage.Request page) {
        if (page.anchor() != null) {
            sql.append(" AND (").append(time).append(", ").append(id).append(") < (:anchorTime, :anchorId)");
            parameters.put("anchorTime", page.anchor().time().atOffset(ZoneOffset.UTC));
            parameters.put("anchorId", page.anchor().id());
        }
        sql.append(" ORDER BY ").append(time).append(" DESC, ").append(id).append(" DESC LIMIT :fetchLimit");
        parameters.put("fetchLimit", page.fetchLimit());
        return this;
    }
    public JdbcClient.StatementSpec statement(JdbcClient jdbc) { return jdbc.sql(sql.toString()).params(parameters); }
    public static Instant time(ResultSet rs, String name) throws SQLException {
        var value = rs.getObject(name, OffsetDateTime.class); return value == null ? null : value.toInstant();
    }
}
