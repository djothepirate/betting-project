package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;
import com.bettingproject.operations.domain.JobModel.Type;

/** Durable dispatch input; payload bytes and credentials never belong here. */
public record EnrichmentJobInput(UUID jobId, UUID admissionId, UUID stepId,
        EnrichmentPlanStepCode stepCode, Type jobType, UUID budgetWindowId,
        String registrySha256, String routeSha256, List<EnrichmentJobRoute> routes,
        Instant createdAt) {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    public EnrichmentJobInput {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(admissionId, "admissionId");
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(stepCode, "stepCode");
        Objects.requireNonNull(jobType, "jobType");
        Objects.requireNonNull(budgetWindowId, "budgetWindowId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (registrySha256 == null || !HASH.matcher(registrySha256).matches()
                || routeSha256 == null || !HASH.matcher(routeSha256).matches()) {
            throw new IllegalArgumentException("invalid registry or route hash");
        }
        routes = Objects.requireNonNull(routes, "routes").stream()
                .sorted(Comparator.comparing(route -> route.family().name())).toList();
        if (routes.isEmpty() || routes.stream().map(EnrichmentJobRoute::family).distinct().count() != routes.size()
                || expectedType(stepCode) != jobType || !routesMatchStep(stepCode, routes)) {
            throw new IllegalArgumentException("invalid enrichment job input routes");
        }
    }

    public static Type expectedType(EnrichmentPlanStepCode step) {
        return switch (step) {
            case LINEUP_T_MINUS_30, LINEUP_T_MINUS_15, DETAIL_AT_KICKOFF, DETAIL_PLUS_45 -> Type.PREMATCH_ENRICHMENT;
            case POSTMATCH_AFTER_FINAL -> Type.POSTMATCH_ENRICHMENT;
            case POSTMATCH_RECHECK_FINAL_PLUS_60 -> Type.POSTMATCH_RECHECK;
        };
    }

    private static boolean routesMatchStep(EnrichmentPlanStepCode step, List<EnrichmentJobRoute> routes) {
        return switch (step) {
            case LINEUP_T_MINUS_30, LINEUP_T_MINUS_15 -> routes.size() == 1
                    && routes.getFirst().family().name().equals("LINEUP");
            case DETAIL_AT_KICKOFF, DETAIL_PLUS_45 -> routes.size() == 1
                    && routes.getFirst().family().name().equals("MATCH_DETAIL");
            case POSTMATCH_AFTER_FINAL, POSTMATCH_RECHECK_FINAL_PLUS_60 -> routes.stream()
                    .noneMatch(route -> route.family().name().equals("LINEUP"));
        };
    }
}
