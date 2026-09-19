package com.bettingproject.collection.domain.budget;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static com.bettingproject.collection.domain.budget.BudgetModel.*;
import static com.bettingproject.collection.domain.budget.BudgetModelTest.*;
import static org.assertj.core.api.Assertions.*;

class BudgetCalculatorTest {
    private final BudgetCalculator calculator = new BudgetCalculator();

    private QuotaObservation observation(long remaining, Set<UUID> covered) {
        return new QuotaObservation(UUID.randomUUID(), WINDOW_ID, remaining, NOW, NOW.plusSeconds(3600),
                PROOF, covered, ObservationDisposition.ACCEPTED, NOW);
    }

    @Test
    void reservesEightyUnitsWithoutSubtractingTheReserveTwice() {
        QuotaObservation initial = observation(100, Set.of());
        Window window = window(initial.id());
        List<Intent> intents = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            assertThat(calculator.calculate(window, intents, initial, NOW).available()).isEqualTo(80 - i);
            intents.add(reserved("priority-" + i));
        }
        assertThat(calculator.calculate(window, intents, initial, NOW).code()).isEqualTo(ResultCode.EXHAUSTED);
        assertThat(intents).hasSize(80);
    }

    @Test
    void twelvePriorExternalCallsLeaveSixtyEightUnitsWithoutFakeIntentions() {
        QuotaObservation initial = observation(88, Set.of());
        Window window = new Window(WINDOW_ID, SCOPE_ID, NOW.minusSeconds(60), NOW.plusSeconds(3600),
                100L, 80, 20, 12, 0, null, null, WindowState.ACTIVE, initial.id(),
                false, 1, NOW, NOW, PROOF);
        assertThat(calculator.calculate(window, List.of(), initial, NOW).available()).isEqualTo(68);
    }

    @Test
    void aResponseDoesNotDebitTheLocalLedgerAgainAndCoverageAvoidsDoubleSubtraction() {
        Intent committed = reserved("one-http-call").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null);
        QuotaObservation old = observation(100, Set.of());
        assertThat(calculator.calculate(window(old.id()), List.of(committed), old, NOW).available()).isEqualTo(79);
        Intent result = committed.transition(IntentState.RESULT_RECORDED, NOW, 200, PROOF.sha256());
        QuotaObservation current = observation(99, Set.of(result.id()));
        assertThat(calculator.calculate(window(current.id()), List.of(result), current, NOW).available()).isEqualTo(79);
        QuotaObservation unknownCoverage = observation(99, Set.of());
        assertThat(calculator.calculate(window(unknownCoverage.id()), List.of(result), unknownCoverage, NOW).available())
                .isEqualTo(78);
    }

    @Test
    void anUncertainIntentionKeepsItsCostButDoesNotBlockOtherAvailableUnits() {
        QuotaObservation initial = observation(100, Set.of());
        Intent uncertain = reserved("uncertain").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null)
                .transition(IntentState.UNCERTAIN, NOW, null, null);
        assertThat(calculator.calculate(window(initial.id()), List.of(uncertain), initial, NOW).available()).isEqualTo(79);
        Intent reconciled = uncertain.transition(IntentState.RECONCILED, NOW, null, PROOF.sha256());
        assertThat(calculator.calculate(window(initial.id()), List.of(reconciled), initial, NOW).available()).isEqualTo(79);
    }

    @Test
    void releaseRestoresHeldUnitsButResultKeepsConsumption() {
        QuotaObservation initial = observation(100, Set.of());
        Intent reserved = reserved("optional");
        assertThat(calculator.calculate(window(initial.id()), List.of(reserved), initial, NOW).available()).isEqualTo(79);
        Intent released = reserved.transition(IntentState.RELEASED, NOW, null, null);
        assertThat(calculator.calculate(window(initial.id()), List.of(released), initial, NOW).available()).isEqualTo(80);
    }

    @Test
    void requiredObservationIsNeverSilentlyReplacedByAnUnlimitedBudget() {
        assertThat(calculator.calculate(null, List.of(), null, NOW).code()).isEqualTo(ResultCode.WINDOW_UNINITIALIZED);
        assertThat(calculator.calculate(window(null), List.of(), null, NOW).code())
                .isEqualTo(ResultCode.QUOTA_OBSERVATION_MISSING);
        QuotaObservation initial = observation(100, Set.of());
        assertThat(calculator.calculate(window(initial.id()), List.of(), initial, NOW.plusSeconds(3600)).code())
                .isEqualTo(ResultCode.OUTSIDE_WINDOW);
        QuotaObservation expired = new QuotaObservation(UUID.randomUUID(), WINDOW_ID, 100, NOW,
                NOW.plusSeconds(10), PROOF, Set.of(), ObservationDisposition.ACCEPTED, NOW);
        assertThat(calculator.calculate(window(expired.id()), List.of(), expired, NOW.plusSeconds(10)).code())
                .isEqualTo(ResultCode.STALE_OBSERVATION);
    }

    @Test
    void contradictoryAndUnrelatedObservationsCloseAuthorization() {
        QuotaObservation initial = observation(100, Set.of());
        Window inconsistent = window(initial.id()).withObservation(initial.id(), true, NOW);
        assertThat(calculator.calculate(inconsistent, List.of(), initial, NOW).code())
                .isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
        assertThat(calculator.calculate(window(UUID.randomUUID()), List.of(), initial, NOW).code())
                .isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
        QuotaObservation unrelatedCoverage = observation(99, Set.of(UUID.randomUUID()));
        assertThat(calculator.calculate(window(unrelatedCoverage.id()), List.of(), unrelatedCoverage, NOW).code())
                .isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
        QuotaObservation staleDisposition = new QuotaObservation(UUID.randomUUID(), WINDOW_ID, 100, NOW,
                NOW.plusSeconds(3600), PROOF, Set.of(), ObservationDisposition.STALE, NOW);
        assertThat(calculator.calculate(window(staleDisposition.id()), List.of(), staleDisposition, NOW).code())
                .isEqualTo(ResultCode.CONTRADICTORY_OBSERVATION);
    }

    @Test
    void suspensionDoesNotAlterTheOtherProviderWindow() {
        QuotaObservation initial = observation(100, Set.of());
        assertThat(calculator.calculate(window(initial.id()).suspend(NOW), List.of(), initial, NOW).code())
                .isEqualTo(ResultCode.SUSPENDED);
        Window other = cadenceWindow(10);
        assertThat(calculator.calculate(other, List.of(), null, NOW).available()).isEqualTo(80);
    }

    @Test
    void allThreeIndependentBudgetBoundsCanBeTheMinimum() {
        QuotaObservation lowerProvider = observation(50, Set.of());
        assertThat(calculator.calculate(window(lowerProvider.id()), List.of(), lowerProvider, NOW).available()).isEqualTo(30);
        QuotaObservation initial = observation(100, Set.of());
        Window lowProject = new Window(WINDOW_ID, SCOPE_ID, NOW.minusSeconds(60), NOW.plusSeconds(3600),
                100L, 10, 20, 5, 5, null, null, WindowState.ACTIVE, initial.id(),
                false, 1, NOW, NOW, PROOF);
        assertThat(calculator.calculate(lowProject, List.of(), initial, NOW).available()).isEqualTo(5);
    }

    @Test
    void cadenceDoesNotConsumeReservationsBeforeAuthorization() {
        Window window = cadenceWindow(1);
        List<Intent> priorities = List.of(reserved("calendar"), reserved("lineup"), reserved("postmatch"));
        assertThat(calculator.calculate(window, priorities, null, NOW).available()).isEqualTo(77);
        assertThat(calculator.checkCadence(window, priorities, NOW).code()).isEqualTo(ResultCode.OK);
        assertThat(calculator.checkCadence(window, priorities, NOW).available()).isEqualTo(1);
    }

    @Test
    void unresolvedIntentionsRetainCadenceSlotsAndOtherSlotsRemainUsable() {
        Intent committed = reserved("pending-response").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null);
        Intent uncertain = reserved("lost-response").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null)
                .transition(IntentState.UNCERTAIN, NOW, null, null);
        assertThat(calculator.checkCadence(cadenceWindow(3), List.of(committed, uncertain), NOW.plusSeconds(600)).available())
                .isEqualTo(1);
        Availability full = calculator.checkCadence(cadenceWindow(2), List.of(committed, uncertain), NOW.plusSeconds(600));
        assertThat(full.code()).isEqualTo(ResultCode.RATE_LIMITED);
        assertThat(full.retryAt()).isNull();
    }

    @Test
    void cadenceStartsItsReleaseDelayAtResultOrReconciliationNotAtSend() {
        Intent committed = reserved("slow-response").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null);
        Intent result = committed.transition(IntentState.RESULT_RECORDED, NOW.plusSeconds(120), 200, PROOF.sha256());
        Availability before = calculator.checkCadence(cadenceWindow(1), List.of(result), NOW.plusSeconds(179));
        assertThat(before.code()).isEqualTo(ResultCode.RATE_LIMITED);
        assertThat(before.retryAt()).isEqualTo(NOW.plusSeconds(180));
        assertThat(calculator.checkCadence(cadenceWindow(1), List.of(result), NOW.plusSeconds(180)).code())
                .isEqualTo(ResultCode.OK);
        Intent reconciled = committed.transition(IntentState.UNCERTAIN, NOW, null, null)
                .transition(IntentState.RECONCILED, NOW.plusSeconds(120), null, null);
        assertThat(calculator.checkCadence(cadenceWindow(1), List.of(reconciled), NOW.plusSeconds(179)).retryAt())
                .isEqualTo(NOW.plusSeconds(180));
    }

    @Test
    void retryTimeAccountsForEverySlotNeededWhenTheCadenceWasReduced() {
        List<Intent> results = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            results.add(reserved("prior-window-" + i).transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null)
                    .transition(IntentState.RESULT_RECORDED, NOW.plusSeconds(i), 200, PROOF.sha256()));
        }
        assertThat(calculator.checkCadence(cadenceWindow(2), results, NOW.plusSeconds(4)).retryAt())
                .isEqualTo(NOW.plusSeconds(62));
    }

    @Test
    void duplicateProjectionRowsCannotCreateAFalseBudgetProof() {
        QuotaObservation initial = observation(100, Set.of());
        Intent reserved = reserved("duplicate");
        assertThatIllegalArgumentException().isThrownBy(() ->
                calculator.calculate(window(initial.id()), List.of(reserved, reserved), initial, NOW));
    }

    private Window cadenceWindow(int limit) {
        return new Window(WINDOW_ID, SCOPE_ID, NOW.minusSeconds(60), NOW.plusSeconds(3600),
                null, 80, 0, 0, 0, limit, Duration.ofMinutes(1), WindowState.ACTIVE,
                null, false, 1, NOW, NOW, PROOF);
    }
}
