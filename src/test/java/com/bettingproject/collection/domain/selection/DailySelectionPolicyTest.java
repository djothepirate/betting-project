package com.bettingproject.collection.domain.selection;

import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import com.bettingproject.collection.domain.selection.DailySelectionPolicy.*;

class DailySelectionPolicyTest {
    private static final Instant NOW=Instant.parse("2030-08-10T12:00:00Z");
    private static final LocalDate DAY=LocalDate.parse("2030-08-10");
    private final DailySelectionPolicy policy=new DailySelectionPolicy();
    @ParameterizedTest @CsvSource({"80,10,7","76,11,6","9,10,0","0,1,0","1,1,1"})
    void respectsResidualBudgetWithoutSubtractingReserveTwice(long available,int cost,int expected){
        var result=policy.select(candidates(10),Set.of(),DAY,NOW,available,cost);
        assertThat(result.selected()).hasSize(expected);assertThat(result.estimatedCalls()).isEqualTo((long)expected*cost);
        assertThat(result.estimatedCalls()).isLessThanOrEqualTo(available);
    }
    @Test void explicitPriorityThenKickoffThenUuidIsIndependentOfInputOrder(){
        var input=new ArrayList<>(candidates(10));Collections.reverse(input);
        var priority=Set.of(id(9));var a=policy.select(input,priority,DAY,NOW,80,10);
        assertThat(a.selected().getFirst().id()).isEqualTo(id(9));
        assertThat(a).isEqualTo(policy.select(candidates(10),priority,DAY,NOW,80,10));
        assertThat(a.excluded()).allMatch(e->e.reasonCode().equals("DAILY_CAP"));
    }
    @Test void rejectsNonCoreNonScheduledPastAndOtherDatesWithoutChangingData(){
        var values=List.of(new Candidate(id(1),"DED",NOW.plusSeconds(1),"SCHEDULED"),
            new Candidate(id(2),"PPL",NOW.plusSeconds(2),"POSTPONED"),new Candidate(id(3),"PD",NOW,"SCHEDULED"),
            new Candidate(id(4),"PD",NOW.plusSeconds(86400),"SCHEDULED"));
        var result=policy.select(values,Set.of(),DAY,NOW,80,1);
        assertThat(result.selected()).isEmpty();assertThat(result.excluded()).extracting(Exclusion::reasonCode)
            .containsExactly("KICKOFF_REACHED","OUTSIDE_ENRICHMENT_CORE","NOT_SCHEDULED","OUTSIDE_UTC_DAY");
    }
    @Test void inputsMustBeBoundedUniqueAndPriorityMustBeKnown(){
        assertThatThrownBy(()->policy.select(candidates(1),Set.of(id(50)),DAY,NOW,80,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->policy.select(candidates(1),Set.of(),DAY,NOW,80,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->policy.select(List.of(candidates(1).getFirst(),candidates(1).getFirst()),Set.of(),DAY,NOW,80,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->policy.select(candidates(1001),Set.of(),DAY,NOW,80,1)).isInstanceOf(IllegalArgumentException.class);
    }
    private static List<Candidate> candidates(int n){return IntStream.rangeClosed(1,n).mapToObj(i->new Candidate(id(i),i%2==0?"PD":"PPL",NOW.plusSeconds(3600),"SCHEDULED")).toList();}
    private static UUID id(int n){return new UUID(0,n);}
}
