package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.ControlCommandReceipt;
import com.bettingproject.catalog.application.ControlCommandReceiptStore;
import com.bettingproject.catalog.application.ControlCommandType;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcControlCommandReceiptStore implements ControlCommandReceiptStore {

    private final JdbcClient jdbcClient;

    public JdbcControlCommandReceiptStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ControlCommandReceipt> findByIdempotencyKey(String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT id, idempotency_key, command_type, command_sha256,
                       result_resource_id, created_at
                FROM control_command_receipt
                WHERE idempotency_key = :idempotencyKey
                """)
                .param("idempotencyKey", idempotencyKey)
                .query(this::map)
                .optional();
    }

    @Override
    public void insert(ControlCommandReceipt receipt) {
        jdbcClient.sql("""
                INSERT INTO control_command_receipt (
                    id, idempotency_key, command_type, command_sha256,
                    result_resource_id, created_at
                ) VALUES (
                    :id, :idempotencyKey, :commandType, :commandSha256,
                    :resultResourceId, :createdAt
                )
                """)
                .param("id", receipt.id())
                .param("idempotencyKey", receipt.idempotencyKey())
                .param("commandType", receipt.commandType().name())
                .param("commandSha256", receipt.commandSha256())
                .param("resultResourceId", receipt.resultResourceId())
                .param("createdAt", receipt.createdAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    private ControlCommandReceipt map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ControlCommandReceipt(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("idempotency_key"),
                ControlCommandType.valueOf(resultSet.getString("command_type")),
                resultSet.getString("command_sha256"),
                resultSet.getObject("result_resource_id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
