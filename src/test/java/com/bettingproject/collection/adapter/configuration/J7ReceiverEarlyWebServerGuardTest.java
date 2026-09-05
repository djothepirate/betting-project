package com.bettingproject.collection.adapter.configuration;

import com.bettingproject.BettingProjectApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7ReceiverEarlyWebServerGuardTest {

    @AfterEach
    void resetSentinel() {
        J7SentinelProtocolResolver.reset();
    }

    @Test
    void rejectsARemoteTlsLocationBeforeBootCanResolveTheResource() {
        J7SentinelProtocolResolver.reset();
        ApplicationResourceLoader.get().getResource("j7sentinel:probe");
        assertThat(J7SentinelProtocolResolver.resolutionCount()).isEqualTo(1);
        J7SentinelProtocolResolver.reset();

        SpringApplication application = new SpringApplication(BettingProjectApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);

        assertThatThrownBy(() -> application.run(
                "--spring.profiles.active=control-api",
                "--spring.main.banner-mode=off",
                "--logging.level.root=off",
                "--betting.integration.j7-receiver.enabled=true",
                "--betting.integration.j7-receiver.client-certificate-sha256-allowlist="
                        + "a".repeat(64),
                "--server.address=127.0.0.1",
                "--server.port=8444",
                "--server.ssl.enabled=true",
                "--server.ssl.client-auth=need",
                "--server.compression.enabled=false",
                "--server.ssl.key-store=j7sentinel:remote-server.p12",
                "--server.ssl.key-store-password=TEST_ONLY",
                "--server.ssl.trust-store=C:/int001-test-only/trust.p12",
                "--server.ssl.trust-store-password=TEST_ONLY",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/betting",
                "--spring.datasource.username=test",
                "--spring.datasource.password=TEST_ONLY"))
                .hasStackTraceContaining(
                        "J7 receiver activation refused: server.ssl.key-store must be an absolute local file resource");

        assertThat(J7SentinelProtocolResolver.resolutionCount()).isZero();
    }

    @Test
    void rejectsDatabaseEndpointOverridesInTheEarlyWebServerPhase() {
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
                    .withProperty(key, "jdbc:postgresql://192.0.2.1:5432/remote");
            J7ReceiverEarlyWebServerGuard guard = new J7ReceiverEarlyWebServerGuard(
                    validProperties(), environment);

            assertThatThrownBy(() -> guard.customize(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key);
        }

        J7ReceiverEarlyWebServerGuard remoteCanonical = new J7ReceiverEarlyWebServerGuard(
                validProperties(),
                validEnvironment().withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://192.0.2.1:5432/remote"));
        assertThatThrownBy(() -> remoteCanonical.customize(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url");
    }

    private J7ReceiverProperties validProperties() {
        J7ReceiverProperties properties = new J7ReceiverProperties();
        properties.setEnabled(true);
        properties.setClientCertificateSha256Allowlist(
                java.util.List.of("a".repeat(64)));
        return properties;
    }

    private MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("control-api");
        return environment
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "8444")
                .withProperty("server.ssl.enabled", "true")
                .withProperty("server.ssl.client-auth", "need")
                .withProperty("server.compression.enabled", "false")
                .withProperty("server.ssl.key-store", "C:/int001-test-only/server.p12")
                .withProperty("server.ssl.key-store-password", "TEST_ONLY")
                .withProperty("server.ssl.trust-store", "C:/int001-test-only/trust.p12")
                .withProperty("server.ssl.trust-store-password", "TEST_ONLY")
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://127.0.0.1:5433/betting");
    }
}
