package com.bettingproject.collection.domain.budget;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.bettingproject.collection.domain.budget.BudgetModel.*;
import static org.assertj.core.api.Assertions.*;

class BudgetModelTest {
    static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
    static final UUID WINDOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID SCOPE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final Proof PROOF = new Proof("synthetic-budget", "a".repeat(64));

    static Window window(UUID observationId) {
        return new Window(WINDOW_ID, SCOPE_ID, NOW.minusSeconds(60), NOW.plusSeconds(3600),
                100L, 80, 20, 0, 0, null, null, WindowState.ACTIVE, observationId,
                false, 1, NOW, NOW, PROOF);
    }

    static Intent reserved(String key) {
        return new Intent(UUID.randomUUID(), WINDOW_ID, key, "MATCH_DETAIL", "b".repeat(64),
                IntentState.RESERVED, 1, NOW, NOW, null, null, null, null);
    }

    @Test
    void transitionsPreserveIdentityAndNeverRefundCommittedCost() {
        Intent original = reserved("one-call-multiple-data-families");
        Intent committed = original.transition(IntentState.COMMITTED_FOR_SEND, NOW.plusSeconds(1), null, null);
        Intent uncertain = committed.transition(IntentState.UNCERTAIN, NOW.plusSeconds(2), null, null);
        Intent reconciled = uncertain.transition(IntentState.RECONCILED, NOW.plusSeconds(3), null, PROOF.sha256());
        assertThat(original.held()).isTrue();
        assertThat(original.consumed()).isFalse();
        assertThat(committed.held()).isFalse();
        for (Intent next : new Intent[] {committed, uncertain, reconciled}) {
            assertThat(next.consumed()).isTrue();
            assertThat(next.id()).isEqualTo(original.id());
            assertThat(next.windowId()).isEqualTo(original.windowId());
            assertThat(next.createdAt()).isEqualTo(original.createdAt());
            assertThat(next.idempotencyKey()).isEqualTo(original.idempotencyKey());
            assertThat(next.requestSha256()).isEqualTo(original.requestSha256());
            assertThat(next.logicalEndpoint()).isEqualTo(original.logicalEndpoint());
            assertThat(next.committedAt()).isEqualTo(NOW.plusSeconds(1));
        }
        assertThat(reconciled.version()).isEqualTo(4);
        assertThat(reconciled.resultAt()).isEqualTo(NOW.plusSeconds(3));
        assertThatIllegalArgumentException().isThrownBy(() ->
                reconciled.transition(IntentState.RELEASED, NOW.plusSeconds(4), null, null));
    }

    @Test
    void releaseIsAvailableOnlyBeforeSendAndIsTerminal() {
        Intent original = reserved("never-sent");
        Intent released = original.transition(IntentState.RELEASED, NOW.plusSeconds(1), null, null);
        assertThat(released.held()).isFalse();
        assertThat(released.consumed()).isFalse();
        assertThat(released.committedAt()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() ->
                released.transition(IntentState.COMMITTED_FOR_SEND, NOW.plusSeconds(2), null, null));
        Intent committed = original.transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null);
        assertThatIllegalArgumentException().isThrownBy(() ->
                committed.transition(IntentState.RELEASED, NOW, null, null));
    }

    @Test
    void resultRequiresStatusAndFingerprintAndRemainsConsumed() {
        Intent committed = reserved("result").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null);
        Intent result = committed.transition(IntentState.RESULT_RECORDED, NOW.plusSeconds(1), 429, PROOF.sha256());
        assertThat(result.httpStatus()).isEqualTo(429);
        assertThat(result.resultFingerprint()).isEqualTo(PROOF.sha256());
        assertThat(result.consumed()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() ->
                committed.transition(IntentState.RESULT_RECORDED, NOW, null, PROOF.sha256()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                committed.transition(IntentState.RESULT_RECORDED, NOW, 200, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                committed.transition(IntentState.RESULT_RECORDED, NOW, 600, PROOF.sha256()));
    }

    @ParameterizedTest
    @EnumSource(IntentState.class)
    void everyStateRejectsARepeatedTransition(IntentState state) {
        Intent intent = switch (state) {
            case RESERVED -> reserved("state");
            case RELEASED -> reserved("state").transition(state, NOW, null, null);
            case COMMITTED_FOR_SEND -> reserved("state").transition(state, NOW, null, null);
            case UNCERTAIN -> reserved("state").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null)
                    .transition(state, NOW, null, null);
            case RESULT_RECORDED -> reserved("state").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null)
                    .transition(state, NOW, 200, PROOF.sha256());
            case RECONCILED -> reserved("state").transition(IntentState.COMMITTED_FOR_SEND, NOW, null, null)
                    .transition(IntentState.UNCERTAIN, NOW, null, null).transition(state, NOW, null, null);
        };
        assertThatIllegalArgumentException().isThrownBy(() -> intent.transition(state, NOW, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " spaced", "spaced ", "two words", "é", "line\nbreak", "\u007f"})
    void idempotencyKeysRequireVisibleAsciiWithoutEchoingInvalidInput(String key) {
        assertThatIllegalArgumentException().isThrownBy(() -> reserved(key))
                .withMessage("invalid budget value or transition");
    }

    @Test
    void literalCharactersAreNotTreatedAsWildcards() {
        Scope scope = new Scope(SCOPE_ID, "provider*?%", "account*?%", NOW);
        assertThat(scope.provider()).isEqualTo("provider*?%");
        assertThat(reserved("literal*?%").idempotencyKey()).isEqualTo("literal*?%");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://provider.invalid/calendar", "calendar?key=value", "calendar#fragment",
            "calendar&option", "option=value", "urn:calendar", "/calendar/matches", "calendar\\matches"})
    void logicalEndpointsRejectUriAndAuthenticationSyntax(String endpoint) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Intent(UUID.randomUUID(), WINDOW_ID,
                "logical-endpoint-only", endpoint, PROOF.sha256(), IntentState.RESERVED, 1,
                NOW, NOW, null, null, null, null)).withMessage("invalid budget value or transition");
    }

    @Test
    void acceptsALogicalEndpointFamilyWithoutAUri() {
        Intent intent = new Intent(UUID.randomUUID(), WINDOW_ID, "logical-endpoint-only", "calendar/matches",
                PROOF.sha256(), IntentState.RESERVED, 1, NOW, NOW, null, null, null, null);
        assertThat(intent.logicalEndpoint()).isEqualTo("calendar/matches");
    }

    @Test
    void windowsPreserveInitialConsumptionAndRequireCoherentOptionalCadence() {
        Window base = window(null);
        Window observed = base.withObservation(UUID.randomUUID(), false, NOW.plusSeconds(1));
        Window suspended = observed.suspend(NOW.plusSeconds(2));
        Window closed = suspended.close(NOW.plusSeconds(3));
        assertThat(closed.id()).isEqualTo(base.id());
        assertThat(closed.createdAt()).isEqualTo(base.createdAt());
        assertThat(closed.proof()).isEqualTo(PROOF);
        assertThat(closed.capacity()).isEqualTo(100);
        assertThat(closed.version()).isEqualTo(4);
        assertThat(closed.state()).isEqualTo(WindowState.CLOSED);
        assertThatIllegalArgumentException().isThrownBy(() -> suspended.suspend(NOW.plusSeconds(3)));
        assertThatIllegalArgumentException().isThrownBy(() -> closed.close(NOW.plusSeconds(4)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Window(WINDOW_ID, SCOPE_ID, NOW,
                NOW.plusSeconds(3600), null, 80, 0, 0, 0, null, null, WindowState.ACTIVE,
                null, false, 1, NOW, NOW, PROOF));
        Window cadenceOnly = new Window(WINDOW_ID, SCOPE_ID, NOW, NOW.plusSeconds(3600),
                null, 80, 0, 0, 4, 10, Duration.ofMinutes(1), WindowState.ACTIVE,
                null, false, 1, NOW, NOW, PROOF);
        assertThat(cadenceOnly.capacity()).isNull();
        assertThat(cadenceOnly.projectInitial()).isEqualTo(4);
        assertThatIllegalArgumentException().isThrownBy(() -> new Window(WINDOW_ID, SCOPE_ID, NOW,
                NOW.plusSeconds(3600), null, 80, 0, 0, 0, 10, Duration.ofSeconds(1).plusNanos(1),
                WindowState.ACTIVE, null, false, 1, NOW, NOW, PROOF));
    }

    @Test
    void rejectsExcessiveAmountsAndInvalidTimeRanges() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Window(WINDOW_ID, SCOPE_ID, NOW,
                NOW, 100L, 80, 20, 0, 0, null, null, WindowState.ACTIVE,
                null, false, 1, NOW, NOW, PROOF));
        assertThatIllegalArgumentException().isThrownBy(() -> new Window(WINDOW_ID, SCOPE_ID, NOW,
                NOW.plusSeconds(1), Long.MAX_VALUE, 80, 20, 0, 0, null, null, WindowState.ACTIVE,
                null, false, 1, NOW, NOW, PROOF));
        assertThatIllegalArgumentException().isThrownBy(() -> new Window(WINDOW_ID, SCOPE_ID, NOW,
                NOW.plusSeconds(1), 100L, 80, 20, 10, 11, null, null, WindowState.ACTIVE,
                null, false, 1, NOW, NOW, PROOF));
    }

    @Test
    void observationDefensivelyCopiesCoverageWithoutInventingAnInitialAttempt() {
        Set<UUID> source = new HashSet<>();
        QuotaObservation observation = new QuotaObservation(UUID.randomUUID(), WINDOW_ID, 88, NOW,
                NOW.plusSeconds(30), PROOF, source, ObservationDisposition.ACCEPTED, NOW);
        source.add(UUID.randomUUID());
        assertThat(observation.coveredIntentIds()).isEmpty();
        assertThatThrownBy(() -> observation.coveredIntentIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new QuotaObservation(UUID.randomUUID(),
                WINDOW_ID, 88, NOW, NOW, PROOF, Set.of(), ObservationDisposition.ACCEPTED, NOW));
    }

    @Test
    void journalMetadataAndAvailabilityDoNotPermitInconsistentValues() {
        Event event = new Event(UUID.randomUUID(), SCOPE_ID, WINDOW_ID, null,
                "WINDOW_INITIALIZED", null, "synthetic-operator", "proof checked", PROOF, NOW);
        assertThat(event.operatorId()).isEqualTo("synthetic-operator");
        assertThatIllegalArgumentException().isThrownBy(() -> new Event(UUID.randomUUID(), SCOPE_ID,
                WINDOW_ID, null, "WINDOW_INITIALIZED", null, "operator\n", "reason", PROOF, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new Availability(ResultCode.EXHAUSTED, 1, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Availability(ResultCode.OK, 1, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new Proof("proof", "A".repeat(64)));
    }
}
