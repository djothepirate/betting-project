package com.bettingproject.bootstrap;

import com.bettingproject.collection.adapter.configuration.J7ClientCertificateFilter;
import com.bettingproject.collection.adapter.configuration.J7ReceiverActivationGuard;
import com.bettingproject.collection.adapter.configuration.J7ReceiverEarlyWebServerGuard;
import com.bettingproject.collection.adapter.persistence.JdbcJ7ImportStore;
import com.bettingproject.collection.adapter.web.j7.J7ImportController;
import com.bettingproject.collection.adapter.web.j7.J7ImportProblemHandler;
import com.bettingproject.collection.adapter.web.j7.J7RequestEnvelopeFilter;
import com.bettingproject.collection.adapter.web.j7.StrictJ7ImportParser;
import com.bettingproject.collection.application.imports.J7ImportPurgeService;
import com.bettingproject.collection.application.imports.J7ImportService;
import com.bettingproject.collection.application.imports.J7ImportStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                "betting.integration.j7-receiver.enabled=true",
                "betting.integration.j7-receiver.client-certificate-sha256-allowlist=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "server.address=127.0.0.1",
                "server.port=8444",
                "server.ssl.enabled=true",
                "server.ssl.client-auth=NEED",
                "server.ssl.key-store=file:///C:/int001-test-only/server.p12",
                "server.ssl.key-store-password=TEST_ONLY_PLACEHOLDER",
                "server.ssl.trust-store=file:///C:/int001-test-only/trust.p12",
                "server.ssl.trust-store-password=TEST_ONLY_PLACEHOLDER"
        })
@ActiveProfiles("control-api")
@Import(NoDatabaseTestConfiguration.class)
class J7ReceiverEnabledProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void explicitQualifiedConfigurationCreatesTheReceiverOnlyInControlApi() {
        assertThat(applicationContext.getBeansOfType(J7ReceiverActivationGuard.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7ReceiverEarlyWebServerGuard.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7ImportController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7ImportProblemHandler.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7RequestEnvelopeFilter.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7ClientCertificateFilter.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(StrictJ7ImportParser.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7ImportService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7ImportPurgeService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7ImportStore.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcJ7ImportStore.class)).hasSize(1);
    }
}
