package com.bettingproject.collection.adapter.configuration;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.web.server.Ssl;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("control-api")
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class J7ReceiverActivationGuard implements InitializingBean {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern LOOPBACK_POSTGRES_URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:5433/[A-Za-z_][A-Za-z0-9_$]{0,62}");
    private static final int MAXIMUM_ROTATION_FINGERPRINTS = 2;

    private final J7ReceiverProperties properties;
    private final Environment environment;
    private final DataSource dataSource;
    private final JdbcConnectionDetails connectionDetails;

    public J7ReceiverActivationGuard(
            J7ReceiverProperties properties,
            Environment environment,
            DataSource dataSource,
            JdbcConnectionDetails connectionDetails) {
        this.properties = properties;
        this.environment = environment;
        this.dataSource = dataSource;
        this.connectionDetails = connectionDetails;
    }

    @Override
    public void afterPropertiesSet() {
        validateStaticConfiguration(properties, environment);
        requireEffectiveLoopbackDatasource();
    }

    static void validateStaticConfiguration(
            J7ReceiverProperties properties,
            Environment environment) {
        J7ReceiverActivationGuard guard = new J7ReceiverActivationGuard(
                properties, environment, null, null);
        guard.requireStaticConfiguration();
        guard.requireStaticDatasourceConfiguration();
    }

    private void requireStaticConfiguration() {
        requireControlApiProfile();
        requireExactProperty("server.address", "127.0.0.1");
        requireExactProperty("server.port", "8444");
        requireExactProperty("server.ssl.enabled", "true");
        requireExactProperty("server.ssl.client-auth", "need");
        requireExactProperty("server.compression.enabled", "false");
        requireLocalFileProperty("server.ssl.key-store");
        requireNonBlank("server.ssl.key-store-password");
        requireLocalFileProperty("server.ssl.trust-store");
        requireNonBlank("server.ssl.trust-store-password");
        requireNoAlternativeTlsMaterial();
        requireNoServletPrefix();
        requireNoDedicatedManagementServer();
        requireContractBounds();
        requireRetentionBounds();
        requireCertificateAllowlist();
    }

    private void requireControlApiProfile() {
        if (Arrays.stream(environment.getActiveProfiles())
                .noneMatch("control-api"::equals)) {
            throw invalidConfiguration("control-api profile is required");
        }
    }

    private void requireExactProperty(String key, String expected) {
        String actual = effectiveProperty(key);
        if (actual == null || !expected.equals(actual.strip().toLowerCase(Locale.ROOT))) {
            throw invalidConfiguration(key + " must equal " + expected);
        }
    }

    private void requireNonBlank(String key) {
        String value = effectiveProperty(key);
        if (value == null || value.isBlank()) {
            throw invalidConfiguration(key + " must be supplied outside Git");
        }
    }

    private void requireNoDedicatedManagementServer() {
        for (String key : List.of(
                "management.server.port",
                "management.server.address",
                "management.server.ssl.enabled")) {
            String value = effectiveProperty(key);
            if (value != null && !value.isBlank()) {
                throw invalidConfiguration(
                        key + " must be absent so Actuator shares the mTLS connector");
            }
        }
    }

    private void requireStaticDatasourceConfiguration() {
        String value = effectiveProperty("spring.datasource.url");
        requireLoopbackJdbcUrl(value, "spring.datasource.url");
        for (String key : List.of(
                "spring.datasource.jndi-name",
                "spring.datasource.type",
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-j-n-d-i",
                "spring.flyway.url",
                "spring.flyway.user",
                "spring.flyway.password",
                "spring.flyway.driver-class-name")) {
            String override = effectiveProperty(key);
            if (override != null && !override.isBlank()) {
                throw invalidConfiguration(
                        key + " must be absent so no dedicated database endpoint can be configured");
            }
        }
        Map<String, Object> dataSourceProperties = Binder.get(environment)
                .bind(
                        "spring.datasource.hikari.data-source-properties",
                        Bindable.mapOf(String.class, Object.class))
                .orElse(Map.of());
        if (!dataSourceProperties.isEmpty()) {
            throw invalidConfiguration(
                    "spring.datasource.hikari.data-source-properties must be empty so the loopback JDBC URL cannot be overridden");
        }
    }

    private void requireEffectiveLoopbackDatasource() {
        requireLoopbackJdbcUrl(
                connectionDetails.getJdbcUrl(),
                "effective JdbcConnectionDetails URL");
        if (!(dataSource instanceof HikariDataSource hikari)) {
            throw invalidConfiguration(
                    "the effective DataSource must be the inspected HikariDataSource");
        }
        requireLoopbackJdbcUrl(hikari.getJdbcUrl(), "effective Hikari JDBC URL");
        if (hikari.getDataSource() != null
                || hasText(hikari.getDataSourceClassName())
                || hasText(hikari.getDataSourceJNDI())
                || !hikari.getDataSourceProperties().isEmpty()) {
            throw invalidConfiguration(
                    "the effective HikariDataSource must use only its inspected loopback JDBC URL");
        }
        if (hasText(hikari.getDriverClassName())
                && !"org.postgresql.Driver".equals(hikari.getDriverClassName())) {
            throw invalidConfiguration(
                    "the effective HikariDataSource driver must be PostgreSQL");
        }
    }

    private void requireLoopbackJdbcUrl(String value, String source) {
        if (value == null || !LOOPBACK_POSTGRES_URL.matcher(value).matches()) {
            throw invalidConfiguration(
                    source + " must use 127.0.0.1:5433 without URL parameters");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void requireNoAlternativeTlsMaterial() {
        for (String key : List.of(
                "server.ssl.bundle",
                "server.ssl.certificate",
                "server.ssl.certificate-private-key",
                "server.ssl.trust-certificate",
                "server.ssl.trust-certificate-private-key")) {
            String value = effectiveProperty(key);
            if (value != null && !value.isBlank()) {
                throw invalidConfiguration(
                        key + " must be absent so the inspected key and trust stores remain authoritative");
            }
        }
        if (Binder.get(environment)
                .bind(
                        "server.ssl.server-name-bundles",
                        Bindable.listOf(Ssl.ServerNameSslBundle.class))
                .isBound()) {
            throw invalidConfiguration(
                    "server.ssl.server-name-bundles must be absent so SNI cannot substitute TLS material");
        }
    }

    private void requireLocalFileProperty(String key) {
        String value = effectiveProperty(key);
        if (value == null || value.isBlank()) {
            throw invalidConfiguration(key + " must be supplied outside Git");
        }
        String stripped = value.strip();
        try {
            if (isUncOrDevicePath(stripped)) {
                throw invalidConfiguration(key + " must not use a network share");
            }
            if (isWindowsAbsolutePath(stripped)) {
                return;
            }
            URI uri = URI.create(stripped);
            if (uri.getScheme() == null) {
                Path path = Path.of(stripped);
                if (path.isAbsolute() && !isUncOrDevicePath(path.toString())) {
                    return;
                }
            }
            else if ("file".equalsIgnoreCase(uri.getScheme())
                    && (uri.getAuthority() == null || uri.getAuthority().isBlank())) {
                // Inspect decoded separators before Path can collapse a UNC prefix on Unix.
                if (uri.getPath() != null && isUncOrDevicePath(uri.getPath())) {
                    throw invalidConfiguration(key + " must not use a network share");
                }
                Path path = Path.of(uri);
                if (path.isAbsolute() && !isUncOrDevicePath(path.toString())) {
                    return;
                }
            }
        }
        catch (IllegalArgumentException exception) {
            throw invalidConfiguration(key + " must be an absolute local file resource");
        }
        throw invalidConfiguration(key + " must be an absolute local file resource");
    }

    private boolean isWindowsAbsolutePath(String value) {
        return value.length() >= 4
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && (value.charAt(2) == '\\' || value.charAt(2) == '/');
    }

    private boolean isUncOrDevicePath(String path) {
        String windowsForm = path.replace('/', '\\');
        return windowsForm.startsWith("\\\\");
    }

    private void requireNoServletPrefix() {
        for (String key : List.of(
                "server.servlet.context-path",
                "spring.mvc.servlet.path")) {
            String value = effectiveProperty(key);
            if (value != null && !value.isBlank()) {
                throw invalidConfiguration(
                        key + " must be absent so the receiver keeps its exact contract path");
            }
        }
    }

    private String effectiveProperty(String canonicalKey) {
        return Binder.get(environment).bind(canonicalKey, String.class).orElse(null);
    }

    private void requireContractBounds() {
        if (properties.getMaxRequestBytes()
                != J7ReceiverProperties.CONTRACT_MAX_REQUEST_BYTES) {
            throw invalidConfiguration("max-request-bytes must equal the fixed contract limit");
        }
        if (properties.getMaxAckBytes() != J7ReceiverProperties.CONTRACT_MAX_ACK_BYTES) {
            throw invalidConfiguration("max-ack-bytes must equal the fixed contract limit");
        }
    }

    private void requireRetentionBounds() {
        if (properties.getRetentionDays() < 1 || properties.getRetentionDays() > 3_650) {
            throw invalidConfiguration("retention-days must be between 1 and 3650");
        }
    }

    private void requireCertificateAllowlist() {
        List<String> fingerprints = properties.getClientCertificateSha256Allowlist();
        if (fingerprints.isEmpty() || fingerprints.size() > MAXIMUM_ROTATION_FINGERPRINTS) {
            throw invalidConfiguration(
                    "one or two client certificate SHA-256 fingerprints are required");
        }
        Set<String> unique = new HashSet<>();
        for (String fingerprint : fingerprints) {
            if (fingerprint == null || !SHA256.matcher(fingerprint).matches()) {
                throw invalidConfiguration(
                        "client certificate fingerprints must be lowercase SHA-256 values");
            }
            if (!unique.add(fingerprint)) {
                throw invalidConfiguration("client certificate fingerprints must be unique");
            }
        }
    }

    private IllegalStateException invalidConfiguration(String reason) {
        return new IllegalStateException("J7 receiver activation refused: " + reason);
    }
}
