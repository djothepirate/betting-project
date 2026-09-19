package com.bettingproject.collection.application.control;

import java.time.*;
import java.util.*;
import com.bettingproject.collection.application.budget.ProviderBudgetTransactions;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.budget.BudgetModel.*;
import com.bettingproject.collection.domain.capability.*;
import com.bettingproject.collection.domain.selection.DailySelectionPolicy.Candidate;
import com.bettingproject.shared.application.ReadPage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DailySelectionServiceTest {
    static final Instant NOW=Instant.parse("2030-08-10T12:00:00Z");
    static final LocalDate DAY=LocalDate.parse("2030-08-10");
    static final String SHA="b".repeat(64);
    static final UUID WINDOW=new UUID(0,1);
    final Data data=new Data(); final Registry registry=new Registry();
    final Clock clock=Clock.fixed(NOW,ZoneOffset.UTC);
    Availability available=new Availability(ResultCode.OK,76,null);
    DailySelectionCandidates candidates=(id,cap,limit)->List.of(new Candidate(id,cap.route().competitionCode(),NOW.plusSeconds(3600),"SCHEDULED"));

    @Test void completedExactCalendarsUseResidualBudgetAndNeverReserve(){
        var result=service().preview(command());
        assertThat(result.code()).isEqualTo(DailySelectionService.Code.OK);
        assertThat(result.budget().available()).isEqualTo(76);
        assertThat(result.selection().selected()).hasSize(2);assertThat(result.reservationCreated()).isFalse();
        assertThat(result.selection().excluded()).hasSize(2);assertThat(result.registrySha256()).isEqualTo(SHA);
    }
    @Test void missingWindowAndMissingCollectionHaveDistinctResults(){
        data.windowExists=false;assertThat(service().preview(command()).code()).isEqualTo(DailySelectionService.Code.WINDOW_NOT_FOUND);
        data.windowExists=true;data.rows.removeFirst();assertThat(service().preview(command()).code()).isEqualTo(DailySelectionService.Code.COLLECTION_NOT_FOUND);
    }
    @Test void incompleteChangedRegistryDisabledAndControlCalendarsCannotQualify(){
        var row=data.rows.getFirst();data.rows.set(0,new CollectionQueryPort.CalendarView(row.id(),row.windowId(),row.provider(),row.providerCompetitionId(),row.sourceSeason(),row.sourcePhase(),row.date(),"INCOMPLETE",null,SHA,NOW,NOW));
        assertThat(service().preview(command()).code()).isEqualTo(DailySelectionService.Code.CALENDAR_NOT_READY);
        data.rows.set(0,row);registry.sha="c".repeat(64);
        assertThat(service().preview(command()).code()).isEqualTo(DailySelectionService.Code.CALENDAR_NOT_READY);
        registry.sha=SHA;registry.active=false;
        assertThat(service().preview(command()).code()).isEqualTo(DailySelectionService.Code.CALENDAR_NOT_READY);
        registry.active=true;registry.control=true;
        assertThat(service().preview(command()).code()).isEqualTo(DailySelectionService.Code.CALENDAR_NOT_READY);
    }
    @Test void quotaRefusalProducesEmptyPreviewWithReasonAndNotAnInfiniteBudget(){
        available=new Availability(ResultCode.STALE_OBSERVATION,0,null);
        var result=service().preview(command());assertThat(result.code()).isEqualTo(DailySelectionService.Code.OK);
        assertThat(result.budget().code()).isEqualTo(ResultCode.STALE_OBSERVATION);assertThat(result.selection().selected()).isEmpty();
    }
    @Test void candidateLimitDuplicateIdentityAndUnknownPriorityAreClosed(){
        candidates=(id,cap,limit)->java.util.stream.IntStream.range(0,1001).mapToObj(i->new Candidate(new UUID(0,i),"PPL",NOW.plusSeconds(1),"SCHEDULED")).toList();
        assertThat(service().preview(command()).code()).isEqualTo(DailySelectionService.Code.CANDIDATE_LIMIT);
        candidates=(id,cap,limit)->List.of(new Candidate(WINDOW,"PPL",NOW.plusSeconds(1),"SCHEDULED"));
        assertThat(service().preview(command()).code()).isEqualTo(DailySelectionService.Code.CALENDAR_NOT_READY);
        candidates=(id,cap,limit)->List.of();var c=command();
        assertThat(service().preview(new DailySelectionCommand(c.windowId(),c.date(),c.calendarCollectionIds(),10,Set.of(WINDOW))).code()).isEqualTo(DailySelectionService.Code.INVALID_PRIORITY);
    }
    private DailySelectionService service(){
        var budget=new ProviderBudgetTransactions(null,clock){@Override public Availability availability(UUID id){assertThat(id).isEqualTo(WINDOW);return available;}};
        return new DailySelectionService(data,candidates,registry,budget,clock);
    }
    private DailySelectionCommand command(){return new DailySelectionCommand(WINDOW,DAY,List.of(new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5)),10,Set.of());}
    static final class Registry implements ProviderCapabilityRegistry {
        String sha=SHA;boolean active=true;boolean control=false;
        public String registryVersion(){return "synthetic";}public String documentSha256(){return sha;}
        public Optional<ProviderCapability> find(ProviderCapabilityKey key){
            return Optional.of(new ProviderCapability(key,new CapabilityRouteKey(key.providerCompetitionId(),"2030/2031","LEAGUE",CapabilityDataType.CALENDAR),
                control?CapabilityStatus.CONTROL:CapabilityStatus.PRIMARY,control?CapabilityAuthorityRole.CONTROL:CapabilityAuthorityRole.PRIMARY,
                active,List.of(new CapabilityEvidenceReference("synthetic",NOW,SHA))));
        }
        public List<ProviderCapability> candidates(CapabilityRouteKey route){return List.of();}
    }
    static final class Data implements CollectionQueryPort {
        boolean windowExists=true;List<CalendarView> rows=new ArrayList<>();
        Data(){String[] codes={"PPL","PD","DED","ELC"};for(int i=0;i<4;i++){rows.add(new CalendarView(new UUID(0,2+i),WINDOW,"highlightly",codes[i],"2030","round",DAY,"COMPLETED",null,SHA,NOW,NOW));}}
        public Optional<WindowView> window(UUID id){return windowExists?Optional.of(new WindowView(id,"highlightly",NOW.minusSeconds(1),NOW.plusSeconds(10000),"ACTIVE",1,100L,80,20,0,0,null,null,false,100L,NOW,NOW.plusSeconds(10000),0,4,NOW,NOW)):Optional.empty();}
        public Optional<CalendarView> calendar(UUID id){return rows.stream().filter(c->c.id().equals(id)).findFirst();}
        public List<WindowView> windows(Filter f,ReadPage.Request p){return List.of();}
        public List<IntentView> intents(UUID id,String s,ReadPage.Request p){return List.of();}
        public List<IncidentView> incidents(Filter f,ReadPage.Request p){return List.of();}
        public List<BudgetEventView> budgetEvents(UUID id,ReadPage.Request p){return List.of();}
        public List<CalendarView> calendars(Filter f,LocalDate d,ReadPage.Request p){return List.of();}
        public List<PageView> pages(UUID id,ReadPage.Request p){return List.of();}
    }
}
