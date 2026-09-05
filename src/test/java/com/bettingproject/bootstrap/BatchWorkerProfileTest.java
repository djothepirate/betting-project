package com.bettingproject.bootstrap;

import com.bettingproject.catalog.adapter.persistence.JdbcNormalizationReplayAnomalyEventStore;
import com.bettingproject.catalog.adapter.persistence.JdbcNormalizationReplayApplicationStore;
import com.bettingproject.catalog.adapter.persistence.JdbcNormalizationReplayAttemptJournal;
import com.bettingproject.catalog.adapter.persistence.JdbcNormalizationReplayRequestRepository;
import com.bettingproject.catalog.adapter.persistence.JdbcAnomalyQueryAdapter;
import com.bettingproject.catalog.adapter.persistence.JdbcMappingQueryAdapter;
import com.bettingproject.catalog.adapter.persistence.JdbcReplayQueryAdapter;
import com.bettingproject.catalog.adapter.persistence.JdbcStoredRawSnapshotReader;
import com.bettingproject.catalog.adapter.persistence.SpringNormalizationReplayAfterCommitExecutor;
import com.bettingproject.catalog.adapter.persistence.SpringNormalizationReplaySavepoint;
import com.bettingproject.catalog.adapter.web.AnomalyControlController;
import com.bettingproject.catalog.adapter.web.CatalogCursorCodec;
import com.bettingproject.catalog.adapter.web.CatalogProblemHandler;
import com.bettingproject.catalog.adapter.web.MappingControlController;
import com.bettingproject.catalog.adapter.web.ReplayControlController;
import com.bettingproject.catalog.adapter.web.StrictCatalogCommandParser;
import com.bettingproject.catalog.application.CalendarAuthorityPolicy;
import com.bettingproject.catalog.application.AnomalyQueryPort;
import com.bettingproject.catalog.application.AnomalyQueryService;
import com.bettingproject.catalog.application.ControlCommandReceiptStore;
import com.bettingproject.catalog.application.DefaultMappingDecisionReplayPlanner;
import com.bettingproject.catalog.application.DecisionJustificationSanitizer;
import com.bettingproject.catalog.application.MappingDecisionAnomalyStore;
import com.bettingproject.catalog.application.ControlCommandLock;
import com.bettingproject.catalog.application.MappingDecisionReplayPlanner;
import com.bettingproject.catalog.application.MappingDecisionService;
import com.bettingproject.catalog.application.MappingQueryPort;
import com.bettingproject.catalog.application.MappingQueryService;
import com.bettingproject.catalog.application.NormalizationReplayAfterCommitExecutor;
import com.bettingproject.catalog.application.NormalizationReplayAnomalyEventStore;
import com.bettingproject.catalog.application.NormalizationReplayApplicationStore;
import com.bettingproject.catalog.application.NormalizationReplayAttemptJournal;
import com.bettingproject.catalog.application.NormalizationReplayExecutionService;
import com.bettingproject.catalog.application.NormalizationReplayRequestRepository;
import com.bettingproject.catalog.application.NormalizationReplayRequestService;
import com.bettingproject.catalog.application.NormalizationReplaySavepoint;
import com.bettingproject.catalog.application.OperatorIdentityProvider;
import com.bettingproject.catalog.application.ProviderMappingDecisionJournal;
import com.bettingproject.catalog.application.ReplayQueryPort;
import com.bettingproject.catalog.application.ReplayQueryService;
import com.bettingproject.catalog.application.StoredRawSnapshotReader;
import com.bettingproject.identity.application.NormalizationAnomalyLifecycleService;
import com.bettingproject.collection.adapter.configuration.J7ClientCertificateFilter;
import com.bettingproject.collection.adapter.persistence.JdbcJ7ImportStore;
import com.bettingproject.collection.adapter.web.j7.J7ImportController;
import com.bettingproject.collection.adapter.web.j7.J7RequestEnvelopeFilter;
import com.bettingproject.collection.application.imports.J7ImportService;
import com.bettingproject.collection.application.imports.J7ImportStore;
import com.bettingproject.operations.adapter.worker.BatchWorkerProbe;
import com.bettingproject.operations.application.RuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER"
        })
@ActiveProfiles("batch-worker")
@Import(NoDatabaseTestConfiguration.class)
class BatchWorkerProfileTest {

    @Autowired
    private BatchWorkerProbe workerProbe;

    @Autowired
    private CalendarAuthorityPolicy calendarAuthorityPolicy;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void batchWorkerStartsFromTheMainApplication() {
        assertThat(workerProbe.runtimeProperties().mode()).isEqualTo(RuntimeMode.BATCH_WORKER);
        assertThat(calendarAuthorityPolicy).isNotNull();
        assertThat(applicationContext.getBeansOfType(MappingDecisionService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(OperatorIdentityProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ControlCommandReceiptStore.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ProviderMappingDecisionJournal.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ControlCommandLock.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MappingDecisionAnomalyStore.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(DecisionJustificationSanitizer.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NormalizationAnomalyLifecycleService.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplayRequestService.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(NormalizationReplayExecutionService.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(DefaultMappingDecisionReplayPlanner.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(MappingDecisionReplayPlanner.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(StoredRawSnapshotReader.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NormalizationReplayRequestRepository.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(NormalizationReplayAttemptJournal.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(NormalizationReplayApplicationStore.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(NormalizationReplayAnomalyEventStore.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(NormalizationReplayAfterCommitExecutor.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(NormalizationReplaySavepoint.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcStoredRawSnapshotReader.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcNormalizationReplayRequestRepository.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcNormalizationReplayAttemptJournal.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcNormalizationReplayApplicationStore.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcNormalizationReplayAnomalyEventStore.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(SpringNormalizationReplayAfterCommitExecutor.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(SpringNormalizationReplaySavepoint.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(AnomalyQueryPort.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AnomalyQueryService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcAnomalyQueryAdapter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MappingQueryPort.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MappingQueryService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcMappingQueryAdapter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ReplayQueryPort.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ReplayQueryService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcReplayQueryAdapter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AnomalyControlController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MappingControlController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ReplayControlController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(CatalogCursorCodec.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(StrictCatalogCommandParser.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(CatalogProblemHandler.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ImportController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7RequestEnvelopeFilter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ClientCertificateFilter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ImportService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ImportStore.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcJ7ImportStore.class)).isEmpty();
    }
}
