package com.bettingproject.qualification.domain;

import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ObservedScalar;
import com.bettingproject.enrichment.domain.ProviderPlayerStatistics;
import com.bettingproject.enrichment.domain.ProviderStatistic;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Deterministic player-statistics checks over provider-neutral enrichment observations. */
public final class PlayerQualityAssessor {

    private PlayerQualityAssessor() {
    }

    public static List<QualityIssueCode> assess(ProviderPlayerStatistics player) {
        if (player == null) {
            throw new IllegalArgumentException("player statistics are required");
        }
        ObservedScalar fullName = player.fullName();
        ObservedScalar minutesPlayed = player.minutesPlayed();
        List<ProviderStatistic> metrics = player.statistics();
        var issues = new ArrayList<QualityIssueCode>();
        if (fullName.state() != EnrichmentObservationState.AVAILABLE
                || fullName.type() != com.bettingproject.enrichment.domain.ObservedScalarType.TEXT
                || fullName.value().isBlank()) {
            issues.add(QualityIssueCode.MISSING_PLAYER_FULL_NAME);
        }
        if (minutesPlayed.isNumeric() && minutesPlayed.decimalValue().compareTo(BigDecimal.ZERO) == 0
                && hasPositiveExpectedMetric(metrics)) {
            issues.add(QualityIssueCode.ZERO_MINUTE_EXPECTED_METRICS);
        }
        for (ProviderStatistic secondYellow : findAll(metrics, "cardsSecondYellow")) {
            if (!secondYellow.value().isNumeric()) { continue; }
            BigDecimal value = secondYellow.value().decimalValue();
            if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0
                    || value.stripTrailingZeros().scale() > 0) {
                issues.add(QualityIssueCode.INVALID_SECOND_YELLOW_VALUE);
                break;
            }
        }
        return List.copyOf(issues);
    }

    private static boolean hasPositiveExpectedMetric(List<ProviderStatistic> metrics) {
        for (String metric : List.of("expectedGoals", "expectedAssists", "expectedGoalsOnTarget")) {
            var value = find(metrics, metric);
            if (value.present() && value.scalar().isNumeric()
                    && value.scalar().decimalValue().signum() > 0) {
                return true;
            }
        }
        return false;
    }

    private static NumericMetric find(List<ProviderStatistic> metrics, String name) {
        for (ProviderStatistic metric : metrics) {
            if (name.equals(metric.sourceName())) {
                return new NumericMetric(metric.value());
            }
        }
        return new NumericMetric(null);
    }

    private static List<ProviderStatistic> findAll(List<ProviderStatistic> metrics, String name) {
        return metrics.stream().filter(metric -> name.equals(metric.sourceName())).toList();
    }

    private record NumericMetric(ObservedScalar scalar) {
        boolean present() { return scalar != null; }
        void ifNumeric(java.util.function.Consumer<BigDecimal> consumer) {
            if (scalar != null && scalar.isNumeric()) {
                consumer.accept(scalar.decimalValue());
            }
        }
    }
}
