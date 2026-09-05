package com.bettingproject.collection.adapter.configuration;

import java.nio.file.Path;
import java.util.stream.Stream;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7ReceiverActivationGuardTest {

    private static final String FINGERPRINT = "a".repeat(64);
    private static final String LOOPBACK_JDBC_URL =
            "jdbc:postgresql://127.0.0.1:5433/betting";
    private static final String DOCUMENTATION_REMOTE_JDBC_URL =
            "jdbc:postgresql://192.0.2.1:5432/remote";

    @Test
    void acceptsTheExactLoopbackMutualTlsEnvelope() {
        J7ReceiverActivationGuard guard = newGuard(
                validProperties(), validEnvironment());

        guard.afterPropertiesSet();
    }

    @Test
    void refusesAReceiverBoundOutsideLoopback() {
        MockEnvironment environment = validEnvironment()
                .withProperty("server.address", "0.0.0.0");

        assertThatThrownBy(() -> newGuard(
                validProperties(), environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.address");
    }

    @Test
    void refusesMissingPrivateTlsConfiguration() {
        MockEnvironment environment = validEnvironment()
                .withProperty("server.ssl.trust-store-password", " ");

        assertThatThrownBy(() -> newGuard(
                validProperties(), environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trust-store-password");
    }

    @ParameterizedTest(name = "{0} refuses {1}")
    @MethodSource("invalidStoreProperties")
    void refusesNonLocalStoresBeforeEitherActivationPhase(String key, String invalidStore) {
        MockEnvironment environment = validEnvironment().withProperty(key, invalidStore);
        assertThatThrownBy(() -> newGuard(validProperties(), environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(key);
        assertThatThrownBy(() -> new J7ReceiverEarlyWebServerGuard(
                validProperties(), environment).customize(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(key);
    }

    private static Stream<Arguments> invalidStoreProperties() {
        return Stream.of(
                "relative/server.p12",
                "classpath:server.p12",
                "https://192.0.2.1/server.p12",
                "\\\\synthetic-host\\share\\server.p12",
                "//synthetic-host/share/server.p12",
                "file:////synthetic-host/share/server.p12",
                "file://///synthetic-host/share/server.p12",
                "file://synthetic-host/share/server.p12",
                "file:///%2Fsynthetic-host/share/server.p12",
                "file:///%5Csynthetic-host/share/server.p12",
                "file:/%5C%5Csynthetic-host/share/server.p12")
                .flatMap(store -> Stream.of("server.ssl.key-store", "server.ssl.trust-store")
                        .map(key -> Arguments.of(key, store)));
    }

    @Test
    void acceptsNativeAbsoluteFilesAndLocalFileUris(@TempDir Path directory) {
        for (String store : java.util.List.of(
                directory.resolve("server.p12").toString(),
                directory.resolve("server store.p12").toUri().toString())) {
            MockEnvironment environment = validEnvironment()
                    .withProperty("server.ssl.key-store", store)
                    .withProperty("server.ssl.trust-store", store);
            newGuard(validProperties(), environment).afterPropertiesSet();
            new J7ReceiverEarlyWebServerGuard(validProperties(), environment).customize(null);
        }
    }

    @Test
    void refusesAlternativeTlsMaterial() {
        for (String key : java.util.List.of(
                "server.ssl.bundle",
                "server.ssl.certificate",
                "server.ssl.certificate-private-key",
                "server.ssl.trust-certificate",
                "server.ssl.trust-certificate-private-key")) {
            MockEnvironment environment = validEnvironment()
                    .withProperty(key, "file:///tmp/alternative.pem");
            assertThatThrownBy(() -> newGuard(validProperties(), environment)
                    .afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key);
        }

        MockEnvironment sni = validEnvironment()
                .withProperty("server.ssl.server-name-bundles[0].server-name", "localhost")
                .withProperty("server.ssl.server-name-bundles[0].bundle", "alternative");
        assertThatThrownBy(() -> newGuard(validProperties(), sni).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server-name-bundles");
    }

    @Test
    void refusesAnyDatasourceOutsideTheExactLocalPostgresBoundary() {
        for (String url : java.util.List.of(
                "jdbc:postgresql://192.0.2.1:5433/betting",
                "jdbc:postgresql://localhost:5433/betting",
                "jdbc:postgresql://127.0.0.1:5432/betting",
                "jdbc:postgresql://127.0.0.1:5433/betting?sslmode=disable",
                "jdbc:postgresql://127.0.0.1:5433/betting,remote/betting")) {
            MockEnvironment environment = validEnvironment()
                    .withProperty("spring.datasource.url", url);

            assertThatThrownBy(() -> newGuard(
                    validProperties(), environment).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("spring.datasource.url");
        }
    }

    @Test
    void refusesDatasourceLocationOverridesThatWouldBypassTheCanonicalUrl() {
        for (String key : java.util.List.of(
                "spring.datasource.jndi-name",
                "spring.datasource.type",
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-j-n-d-i",
                "spring.flyway.url",
                "spring.flyway.user",
                "spring.flyway.password",
                "spring.flyway.driver-class-name")) {
            MockEnvironment environment = validEnvironment()
                    .withProperty(key, DOCUMENTATION_REMOTE_JDBC_URL);

            assertThatThrownBy(() -> newGuard(
                    validProperties(), environment).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key);
        }

        MockEnvironment dataSourceProperties = validEnvironment()
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.serverName",
                        "192.0.2.1");
        assertThatThrownBy(() -> newGuard(
                validProperties(), dataSourceProperties).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data-source-properties");
    }

    @Test
    void refusesEffectiveConnectionDetailsOrDataSourceSubstitution() {
        assertThatThrownBy(() -> newGuard(
                validProperties(),
                validEnvironment(),
                validDataSource(LOOPBACK_JDBC_URL),
                connectionDetails(DOCUMENTATION_REMOTE_JDBC_URL)).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JdbcConnectionDetails");

        assertThatThrownBy(() -> newGuard(
                validProperties(),
                validEnvironment(),
                validDataSource(DOCUMENTATION_REMOTE_JDBC_URL),
                connectionDetails(LOOPBACK_JDBC_URL)).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("effective Hikari JDBC URL");

        DriverManagerDataSource custom = new DriverManagerDataSource(
                DOCUMENTATION_REMOTE_JDBC_URL, "test", "test");
        assertThatThrownBy(() -> newGuard(
                validProperties(),
                validEnvironment(),
                custom,
                connectionDetails(LOOPBACK_JDBC_URL)).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inspected HikariDataSource");

        HikariDataSource jndi = validDataSource(LOOPBACK_JDBC_URL);
        jndi.setDataSourceJNDI("java:comp/env/jdbc/remote");
        assertThatThrownBy(() -> newGuard(
                validProperties(),
                validEnvironment(),
                jndi,
                connectionDetails(LOOPBACK_JDBC_URL)).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("effective HikariDataSource");
    }

    @Test
    void refusesAckCompressionIncludingAHigherPriorityOverride() {
        MockEnvironment canonical = validEnvironment()
                .withProperty("server.compression.enabled", "true");
        assertThatThrownBy(() -> newGuard(
                validProperties(), canonical).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.compression.enabled");

        MockEnvironment highPriority = validEnvironment();
        highPriority.getPropertySources().addFirst(new MapPropertySource(
                "commandLineArgs",
                java.util.Map.of("server.compression.enabled", "TRUE")));
        assertThatThrownBy(() -> newGuard(
                validProperties(), highPriority).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.compression.enabled");
    }

    @Test
    void refusesHigherPriorityRelaxedBindingAliases() {
        MockEnvironment clientAuthEnvironment = validEnvironment();
        clientAuthEnvironment.getPropertySources().addFirst(new MapPropertySource(
                "commandLineArgs",
                java.util.Map.of("server.ssl.clientAuth", "WANT")));

        assertThatThrownBy(() -> newGuard(
                validProperties(), clientAuthEnvironment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.ssl.client-auth");

        MockEnvironment contextPathEnvironment = validEnvironment();
        contextPathEnvironment.getPropertySources().addFirst(new MapPropertySource(
                "commandLineArgs",
                java.util.Map.of("server.servlet.contextPath", "/prefixed")));

        assertThatThrownBy(() -> newGuard(
                validProperties(), contextPathEnvironment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.servlet.context-path");
    }

    @Test
    void refusesAnyDedicatedManagementConnector() {
        for (String key : java.util.List.of(
                "management.server.port",
                "management.server.address",
                "management.server.ssl.enabled")) {
            MockEnvironment environment = validEnvironment().withProperty(key, "9443");

            assertThatThrownBy(() -> newGuard(
                    validProperties(), environment).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key);
        }
    }

    @Test
    void refusesContextOrServletPathPrefixes() {
        for (String key : java.util.List.of(
                "server.servlet.context-path",
                "spring.mvc.servlet.path")) {
            MockEnvironment environment = validEnvironment().withProperty(key, "/prefixed");

            assertThatThrownBy(() -> newGuard(
                    validProperties(), environment).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key);
        }
    }

    @Test
    void refusesAnAlteredProtocolLimit() {
        J7ReceiverProperties properties = validProperties();
        properties.setMaxRequestBytes(J7ReceiverProperties.CONTRACT_MAX_REQUEST_BYTES - 1);

        assertThatThrownBy(() -> newGuard(
                properties, validEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-request-bytes");
    }

    @Test
    void acceptsOnlyTheDocumentedRetentionBounds() {
        for (int accepted : java.util.List.of(1, 3_650)) {
            J7ReceiverProperties properties = validProperties();
            properties.setRetentionDays(accepted);

            newGuard(properties, validEnvironment())
                    .afterPropertiesSet();
        }

        for (int refused : java.util.List.of(0, 3_651)) {
            J7ReceiverProperties properties = validProperties();
            properties.setRetentionDays(refused);

            assertThatThrownBy(() -> newGuard(
                    properties, validEnvironment()).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("retention-days");
        }
    }

    @Test
    void refusesNonCanonicalOrDuplicateCertificateFingerprints() {
        J7ReceiverProperties nonCanonical = validProperties();
        nonCanonical.setClientCertificateSha256Allowlist(java.util.List.of("A".repeat(64)));
        assertThatThrownBy(() -> newGuard(
                nonCanonical, validEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lowercase SHA-256");

        J7ReceiverProperties duplicate = validProperties();
        duplicate.setClientCertificateSha256Allowlist(
                java.util.List.of(FINGERPRINT, FINGERPRINT));
        assertThatThrownBy(() -> newGuard(
                duplicate, validEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique");
    }

    private J7ReceiverProperties validProperties() {
        J7ReceiverProperties properties = new J7ReceiverProperties();
        properties.setEnabled(true);
        properties.setClientCertificateSha256Allowlist(java.util.List.of(FINGERPRINT));
        return properties;
    }

    private J7ReceiverActivationGuard newGuard(
            J7ReceiverProperties properties,
            MockEnvironment environment) {
        return newGuard(
                properties,
                environment,
                validDataSource(LOOPBACK_JDBC_URL),
                connectionDetails(LOOPBACK_JDBC_URL));
    }

    private J7ReceiverActivationGuard newGuard(
            J7ReceiverProperties properties,
            MockEnvironment environment,
            DataSource dataSource,
            JdbcConnectionDetails connectionDetails) {
        return new J7ReceiverActivationGuard(
                properties, environment, dataSource, connectionDetails);
    }

    private HikariDataSource validDataSource(String jdbcUrl) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername("test");
        dataSource.setPassword("test");
        return dataSource;
    }

    private JdbcConnectionDetails connectionDetails(String jdbcUrl) {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return "test";
            }

            @Override
            public String getPassword() {
                return "test";
            }

            @Override
            public String getJdbcUrl() {
                return jdbcUrl;
            }
        };
    }

    private MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("control-api");
        return environment
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "8444")
                .withProperty("server.ssl.enabled", "true")
                .withProperty("server.ssl.client-auth", "NEED")
                .withProperty("server.compression.enabled", "false")
                .withProperty("server.ssl.key-store", "C:\\int001-test-only\\server.p12")
                .withProperty("server.ssl.key-store-password", "TEST_ONLY")
                .withProperty("server.ssl.trust-store", "C:\\int001-test-only\\trust.p12")
                .withProperty("server.ssl.trust-store-password", "TEST_ONLY")
                .withProperty(
                        "spring.datasource.url",
                        LOOPBACK_JDBC_URL);
    }
}
