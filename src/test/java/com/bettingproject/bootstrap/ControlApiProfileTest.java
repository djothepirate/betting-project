package com.bettingproject.bootstrap;

import com.bettingproject.operations.adapter.web.BootstrapStatusController;
import com.bettingproject.operations.application.RuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER"
        })
@ActiveProfiles("control-api")
@Import(NoDatabaseTestConfiguration.class)
class ControlApiProfileTest {

    @Autowired
    private BootstrapStatusController controller;

    @Test
    void controlApiStartsFromTheMainApplication() {
        BootstrapStatusController.BootstrapStatus status = controller.status();

        assertThat(status.application()).isEqualTo("betting-project");
        assertThat(status.mode()).isEqualTo(RuntimeMode.CONTROL_API);
        assertThat(status.activeProfiles()).containsExactly("control-api");
    }
}
