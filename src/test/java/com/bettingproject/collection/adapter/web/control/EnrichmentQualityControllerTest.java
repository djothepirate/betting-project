package com.bettingproject.collection.adapter.web.control;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.bettingproject.collection.application.enrichment.EnrichmentQualityQueryPort;
import com.bettingproject.collection.application.enrichment.EnrichmentQualityQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EnrichmentQualityControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new EnrichmentQualityQueryService(new EmptyQualityQueryPort(),
                Clock.fixed(Instant.parse("2030-08-10T12:00:00Z"), ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new EnrichmentQualityController(service))
                .setControllerAdvice(new CollectionProblemHandler()).build();
    }

    @Test
    void validDateReturnsAnExplicitEmptyDailyProjection() throws Exception {
        mvc.perform(get("/internal/collection/enrichment/daily").param("date", "2030-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2030-08-10"))
                .andExpect(jsonPath("$.planPresent").value(false))
                .andExpect(jsonPath("$.fixtures").isEmpty());
    }

    @Test
    void dateIsRequiredAndUnknownDuplicateOrPathParametersAreRejected() throws Exception {
        mvc.perform(get("/internal/collection/enrichment/daily"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/internal/collection/enrichment/daily").param("date", "2030-08-10").param("extra", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/internal/collection/enrichment/daily").param("date", "2030-08-10", "2030-08-11"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/internal/collection/enrichment/daily").param("path", "C:/private"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARBITRARY_PATH_FORBIDDEN"));
    }

    private static final class EmptyQualityQueryPort implements EnrichmentQualityQueryPort {
        @Override public Optional<DailyPlan> dailyPlan(LocalDate date) { return Optional.empty(); }
        @Override public List<AdmittedFixture> admittedFixtures(LocalDate date) { throw new AssertionError("No plan means no child reads"); }
        @Override public List<PlannedStep> planSteps(LocalDate date) { throw new AssertionError("No plan means no child reads"); }
        @Override public List<LatestObservation> latestObservations(LocalDate date) { throw new AssertionError("No plan means no child reads"); }
        @Override public List<FindingCount> findingCounts(LocalDate date) { throw new AssertionError("No plan means no child reads"); }
        @Override public List<LatestAttempt> latestAttempts(LocalDate date) { throw new AssertionError("No plan means no child reads"); }
    }
}
