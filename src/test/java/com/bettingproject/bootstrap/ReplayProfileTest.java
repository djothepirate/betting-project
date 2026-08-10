package com.bettingproject.bootstrap;

import com.bettingproject.collection.application.ReplayService;
import com.bettingproject.operations.application.RuntimeMode;
import com.bettingproject.operations.application.RuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("replay")
class ReplayProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ReplayService replayService;

    @Autowired
    private RuntimeProperties runtimeProperties;

    @Test
    void replayStartsOfflineWithoutDatabaseInfrastructure() {
        assertThat(replayService).isNotNull();
        assertThat(runtimeProperties.mode()).isEqualTo(RuntimeMode.REPLAY);
        assertThat(applicationContext.getBeansOfType(JdbcClient.class)).isEmpty();
    }
}
