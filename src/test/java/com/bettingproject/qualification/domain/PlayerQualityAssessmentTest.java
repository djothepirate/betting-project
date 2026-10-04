package com.bettingproject.qualification.domain;

import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ObservedScalar;
import com.bettingproject.enrichment.domain.ObservedScalarType;
import com.bettingproject.enrichment.domain.ParsedEvents;
import com.bettingproject.enrichment.domain.ParsedPlayerStatistics;
import com.bettingproject.enrichment.domain.PlayerStatisticsTeam;
import com.bettingproject.enrichment.domain.ProviderEvent;
import com.bettingproject.enrichment.domain.ProviderPlayerStatistics;
import com.bettingproject.enrichment.domain.ProviderStatistic;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerQualityAssessmentTest {

    @Test
    void crossEndpointMismatchRequiresUniqueExactTeamNameRoleAndMinutesEvidence() {
        var first = evidence("box-score", "100", "10", "Player A", "MF", "90");
        var second = evidence("detail", "100", "99", "Player A", "MF", "90.0");

        assertThat(CrossEndpointPlayerIdentityAssessor.assess(List.of(first), List.of(second)))
                .containsExactly(new PlayerQualityConcern(QualityIssueCode.CROSS_ENDPOINT_PLAYER_ID_MISMATCH,
                        "100", "10", "99"));
        assertThat(CrossEndpointPlayerIdentityAssessor.assess(
                List.of(evidence("lineup", "100", "10", "Player A", "MF", null)), List.of(second)))
                .containsExactly(new PlayerQualityConcern(QualityIssueCode.CROSS_ENDPOINT_PLAYER_ID_MISMATCH,
                        "100", "10", "99"));
        assertThat(CrossEndpointPlayerIdentityAssessor.assess(List.of(first), List.of(
                evidence("detail", "100", "99", "Player A", "DF", "90")))).isEmpty();
        assertThat(CrossEndpointPlayerIdentityAssessor.assess(List.of(first), List.of(
                evidence("detail", "100", "99", "Player A", null, "90")))).isEmpty();
        assertThat(CrossEndpointPlayerIdentityAssessor.assess(List.of(first, first), List.of(second))).isEmpty();
        assertThat(CrossEndpointPlayerIdentityAssessor.assess(List.of(first), List.of(
                evidence("box-score", "100", "99", "Player A", "MF", "90")))).isEmpty();
    }

    @Test
    void yellowCardFindingRequiresAnUnambiguousExactTeamPlayerAndNameMatch() {
        ParsedEvents events = new ParsedEvents(EnrichmentObservationState.AVAILABLE,
                List.of(new ProviderEvent(0, "100", text("45+2"), text("Yellow Card"), integer("10"),
                        text("Player A"), ObservedScalar.nullValue(), ObservedScalar.nullValue(),
                        ObservedScalar.nullValue())));
        ParsedPlayerStatistics withoutCardMetric = playerStats("100", "10", "Player A", List.of());

        assertThat(YellowCardQualityAssessor.assess(events, withoutCardMetric)).containsExactly(
                new PlayerQualityConcern(QualityIssueCode.MISSING_PLAYER_YELLOW_CARD, "100", "10", null));

        ParsedPlayerStatistics withCardMetric = playerStats("100", "10", "Player A",
                List.of(new ProviderStatistic("cardsYellow", ObservedScalar.of(ObservedScalarType.INTEGER, "1"))));
        assertThat(YellowCardQualityAssessor.assess(events, withCardMetric)).isEmpty();
        assertThat(YellowCardQualityAssessor.assess(events, playerStats("100", "99", "Other Player", List.of())))
                .isEmpty();
    }

    private static PlayerEndpointIdentityEvidence evidence(
            String endpoint, String teamId, String playerId, String name, String role, String minutes) {
        return new PlayerEndpointIdentityEvidence(endpoint, teamId, playerId, name, role,
                minutes == null ? null : new BigDecimal(minutes));
    }

    private static ParsedPlayerStatistics playerStats(
            String teamId, String playerId, String name, List<ProviderStatistic> metrics) {
        var player = new ProviderPlayerStatistics(playerId, name, ObservedScalar.of(ObservedScalarType.TEXT, name),
                ObservedScalar.missing(), ObservedScalar.missing(), ObservedScalar.of(ObservedScalarType.TEXT, "MF"),
                ObservedScalar.missing(), ObservedScalar.missing(), ObservedScalar.missing(),
                ObservedScalar.of(ObservedScalarType.INTEGER, "90"),
                metrics.isEmpty() ? EnrichmentObservationState.EMPTY : EnrichmentObservationState.AVAILABLE,
                metrics);
        return new ParsedPlayerStatistics(EnrichmentObservationState.AVAILABLE,
                List.of(new PlayerStatisticsTeam(teamId, EnrichmentObservationState.AVAILABLE, List.of(player))));
    }

    private static ObservedScalar text(String value) {
        return ObservedScalar.of(ObservedScalarType.TEXT, value);
    }

    private static ObservedScalar integer(String value) {
        return ObservedScalar.of(ObservedScalarType.INTEGER, value);
    }
}
