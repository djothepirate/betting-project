package com.bettingproject.bootstrap;

import com.bettingproject.collection.adapter.persistence.JdbcProviderBudgetRepository;
import com.bettingproject.collection.application.budget.BudgetJustificationSanitizer;
import com.bettingproject.collection.application.budget.BudgetOperatorIdentityProvider;
import com.bettingproject.collection.application.budget.ProviderBudgetAdministration;
import com.bettingproject.collection.application.budget.ProviderBudgetAdministrationTransactions;
import com.bettingproject.collection.application.budget.ProviderBudgetRepository;
import com.bettingproject.collection.application.budget.ProviderBudgetService;
import com.bettingproject.collection.application.budget.ProviderBudgetTransactions;
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
import com.bettingproject.catalog.application.RegistryCalendarAuthorityPolicy;
import com.bettingproject.collection.adapter.configuration.ClasspathProviderCapabilityConfiguration;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.application.capability.ProviderRoutingService;
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
import com.bettingproject.collection.adapter.web.j7.J7ImportProblemHandler;
import com.bettingproject.collection.adapter.web.j7.J7RequestEnvelopeFilter;
import com.bettingproject.collection.adapter.web.j7.StrictJ7ImportParser;
import com.bettingproject.collection.application.imports.J7ImportPurgeService;
import com.bettingproject.collection.application.imports.J7ImportService;
import com.bettingproject.collection.application.imports.J7ImportStore;
import com.bettingproject.operations.adapter.web.BootstrapStatusController;
import com.bettingproject.operations.application.RuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
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

    @Autowired
    private CalendarAuthorityPolicy calendarAuthorityPolicy;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    void controlApiStartsFromTheMainApplication() {
        CollectionControlProfileAssertions.verify(applicationContext,true);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.operations.application.jobs.JobRepository.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.operations.application.jobs.JobTransactions.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.collection.application.calendar.CalendarJobPlanningService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.collection.application.calendar.CalendarJobInputStore.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.operations.application.jobs.JobWorker.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(com.bettingproject.operations.application.jobs.JobHandler.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(com.bettingproject.operations.adapter.worker.CollectionWorkerLoop.class)).isEmpty();
        BootstrapStatusController.BootstrapStatus status = controller.status();

        assertThat(status.application()).isEqualTo("betting-project");
        assertThat(status.mode()).isEqualTo(RuntimeMode.CONTROL_API);
        assertThat(status.activeProfiles()).containsExactly("control-api");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(calendarAuthorityPolicy).isInstanceOf(RegistryCalendarAuthorityPolicy.class);
        assertThat(applicationContext.getBeansOfType(CalendarAuthorityPolicy.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ProviderCapabilityRegistry.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ProviderRoutingService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ClasspathProviderCapabilityConfiguration.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ProviderBudgetService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.catalog.application.CalendarCanonicalContextPolicy.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.collection.application.calendar.CalendarCollectionService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.collection.application.calendar.CalendarCollectionStore.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.collection.application.calendar.CalendarDerivationService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.collection.application.calendar.CalendarEvidenceTransactions.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.collection.application.calendar.CalendarApplicationPort.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(com.bettingproject.collection.application.calendar.CalendarNativeReplayService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ProviderBudgetTransactions.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ProviderBudgetRepository.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcProviderBudgetRepository.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ProviderBudgetAdministration.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ProviderBudgetAdministrationTransactions.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(BudgetOperatorIdentityProvider.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(BudgetJustificationSanitizer.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(MappingDecisionService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(OperatorIdentityProvider.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ControlCommandReceiptStore.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ProviderMappingDecisionJournal.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ControlCommandLock.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(MappingDecisionAnomalyStore.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(DecisionJustificationSanitizer.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationAnomalyLifecycleService.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplayRequestService.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplayExecutionService.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(DefaultMappingDecisionReplayPlanner.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(MappingDecisionReplayPlanner.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(StoredRawSnapshotReader.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplayRequestRepository.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplayAttemptJournal.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplayApplicationStore.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplayAnomalyEventStore.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplayAfterCommitExecutor.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(NormalizationReplaySavepoint.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcStoredRawSnapshotReader.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcNormalizationReplayRequestRepository.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcNormalizationReplayAttemptJournal.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcNormalizationReplayApplicationStore.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcNormalizationReplayAnomalyEventStore.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(SpringNormalizationReplayAfterCommitExecutor.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(SpringNormalizationReplaySavepoint.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(AnomalyQueryPort.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(AnomalyQueryService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcAnomalyQueryAdapter.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(MappingQueryPort.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(MappingQueryService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcMappingQueryAdapter.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ReplayQueryPort.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ReplayQueryService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(JdbcReplayQueryAdapter.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(AnomalyControlController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(MappingControlController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ReplayControlController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(CatalogCursorCodec.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(StrictCatalogCommandParser.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(CatalogProblemHandler.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(J7ImportController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ImportProblemHandler.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7RequestEnvelopeFilter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ClientCertificateFilter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(StrictJ7ImportParser.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ImportService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ImportPurgeService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(J7ImportStore.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcJ7ImportStore.class)).isEmpty();
    }
}
