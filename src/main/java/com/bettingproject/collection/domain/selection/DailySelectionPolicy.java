package com.bettingproject.collection.domain.selection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/** Preview only: neither reserves calls nor promises a future enrichment. */
public final class DailySelectionPolicy {
    public static final int MAX_MATCHES = 7;
    public static final int MAX_CANDIDATES = 1000;
    public record Candidate(UUID id, String competitionCode, Instant kickoffAt, String status) {
        public Candidate { Objects.requireNonNull(id); Objects.requireNonNull(competitionCode); Objects.requireNonNull(kickoffAt); Objects.requireNonNull(status); }
    }
    public record Exclusion(Candidate candidate, String reasonCode) { }
    public record Selection(List<Candidate> selected, List<Exclusion> excluded, long estimatedCalls) {
        public Selection { selected=List.copyOf(selected); excluded=List.copyOf(excluded); }
    }
    public Selection select(List<Candidate> candidates, Set<UUID> priorityIds, LocalDate date,
            Instant evaluatedAt, long availableCalls, int callsPerMatch) {
        if (availableCalls<0 || callsPerMatch<1 || callsPerMatch>80 || candidates.size()>MAX_CANDIDATES
                || priorityIds.size()>100 || candidates.stream().map(Candidate::id).distinct().count()!=candidates.size()
                || !candidates.stream().map(Candidate::id).toList().containsAll(priorityIds)) {
            throw new IllegalArgumentException("Invalid selection input");
        }
        var ordered=candidates.stream().sorted(Comparator
                .comparing((Candidate c)->!priorityIds.contains(c.id()))
                .thenComparing(Candidate::kickoffAt).thenComparing(c->c.id().toString())).toList();
        List<Candidate> selected=new ArrayList<>(); List<Exclusion> excluded=new ArrayList<>();
        for(var c:ordered){
            String reason=null;
            if(!Set.of("PPL","PD").contains(c.competitionCode())){reason="OUTSIDE_ENRICHMENT_CORE";}
            else if(!c.kickoffAt().atOffset(ZoneOffset.UTC).toLocalDate().equals(date)){reason="OUTSIDE_UTC_DAY";}
            else if(!c.status().equals("SCHEDULED")){reason="NOT_SCHEDULED";}
            else if(!c.kickoffAt().isAfter(evaluatedAt)){reason="KICKOFF_REACHED";}
            else if(selected.size()>=MAX_MATCHES){reason="DAILY_CAP";}
            else if((long)(selected.size()+1)*callsPerMatch>availableCalls){reason="BUDGET_LIMIT";}
            if(reason==null){selected.add(c);}else{excluded.add(new Exclusion(c,reason));}
        }
        return new Selection(selected,excluded,(long)selected.size()*callsPerMatch);
    }
}
