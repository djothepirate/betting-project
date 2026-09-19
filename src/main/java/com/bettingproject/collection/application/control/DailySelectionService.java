package com.bettingproject.collection.application.control;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import com.bettingproject.collection.application.budget.ProviderBudgetTransactions;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.budget.BudgetModel;
import com.bettingproject.collection.domain.capability.*;
import com.bettingproject.collection.domain.selection.DailySelectionPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
public class DailySelectionService {
    public enum Code { OK, WINDOW_NOT_FOUND, COLLECTION_NOT_FOUND, CALENDAR_NOT_READY, CANDIDATE_LIMIT, INVALID_PRIORITY }
    public record Preview(Code code, Instant evaluatedAt, UUID windowId, String registrySha256,
            BudgetModel.Availability budget, int estimatedCallsPerMatch, int maximumMatches,
            boolean reservationCreated, DailySelectionPolicy.Selection selection) { }
    private final CollectionQueryPort queries;
    private final DailySelectionCandidates candidates;
    private final ProviderCapabilityRegistry registry;
    private final ProviderBudgetTransactions budget;
    private final Clock clock;
    public DailySelectionService(CollectionQueryPort queries, DailySelectionCandidates candidates,
            ProviderCapabilityRegistry registry, ProviderBudgetTransactions budget, Clock clock){
        this.queries=queries;this.candidates=candidates;this.registry=registry;this.budget=budget;this.clock=clock;
    }
    // No writes. A short transaction holds the existing budget locks while calculating the preview.
    @Transactional
    public Preview preview(DailySelectionCommand command){
        var window=queries.window(command.windowId());
        if(window.isEmpty()){return refused(Code.WINDOW_NOT_FOUND,command);}
        if(!window.get().provider().equals("highlightly")){return refused(Code.CALENDAR_NOT_READY,command);}
        Map<UUID,ProviderCapability> accepted=new LinkedHashMap<>(); Set<String> routes=new HashSet<>();
        for(UUID id:command.calendarCollectionIds()){
            var found=queries.calendar(id); if(found.isEmpty()){return refused(Code.COLLECTION_NOT_FOUND,command);}
            var c=found.get();
            if(!c.status().equals("COMPLETED") || !c.date().equals(command.date()) || !c.windowId().equals(command.windowId())
                    || !c.provider().equals("highlightly") || !c.registrySha256().equals(registry.documentSha256())){
                return refused(Code.CALENDAR_NOT_READY,command);
            }
            var key=new ProviderCapabilityKey(c.provider(),c.providerCompetitionId(),c.sourceSeason(),c.sourcePhase(),CapabilityDataType.CALENDAR);
            var capability=registry.find(key);
            if(capability.isEmpty() || !capability.get().operational() || capability.get().authorityRole()!=CapabilityAuthorityRole.PRIMARY
                    || !routes.add(capability.get().route().competitionCode())){return refused(Code.CALENDAR_NOT_READY,command);}
            accepted.put(id,capability.get());
        }
        if(!routes.equals(Set.of("PPL","PD","DED","ELC"))){return refused(Code.CALENDAR_NOT_READY,command);}
        var available=budget.availability(command.windowId());
        List<DailySelectionPolicy.Candidate> all=new ArrayList<>();
        for(var entry:accepted.entrySet()){
            all.addAll(candidates.find(entry.getKey(),entry.getValue(),DailySelectionPolicy.MAX_CANDIDATES+1));
            if(all.size()>DailySelectionPolicy.MAX_CANDIDATES){return refused(Code.CANDIDATE_LIMIT,command);}
        }
        // A canonical identity cannot silently belong to two logical competitions.
        if(all.stream().map(DailySelectionPolicy.Candidate::id).distinct().count()!=all.size()){
            return refused(Code.CALENDAR_NOT_READY,command);
        }
        if(!all.stream().map(DailySelectionPolicy.Candidate::id).toList().containsAll(command.priorityFixtureIds())){
            return refused(Code.INVALID_PRIORITY,command);
        }
        Instant now=clock.instant();
        var selection=new DailySelectionPolicy().select(all,command.priorityFixtureIds(),command.date(),now,
                available.code()==BudgetModel.ResultCode.OK?available.available():0,command.estimatedCallsPerMatch());
        return new Preview(Code.OK,now,command.windowId(),registry.documentSha256(),available,
                command.estimatedCallsPerMatch(),DailySelectionPolicy.MAX_MATCHES,false,selection);
    }
    private Preview refused(Code code,DailySelectionCommand c){
        return new Preview(code,clock.instant(),c.windowId(),registry.documentSha256(),null,c.estimatedCallsPerMatch(),7,false,null);
    }
}
