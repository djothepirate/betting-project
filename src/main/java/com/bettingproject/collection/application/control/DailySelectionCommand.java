package com.bettingproject.collection.application.control;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

public record DailySelectionCommand(UUID windowId, LocalDate date, List<UUID> calendarCollectionIds,
        int estimatedCallsPerMatch, Set<UUID> priorityFixtureIds) {
    public DailySelectionCommand {
        Objects.requireNonNull(windowId); Objects.requireNonNull(date);
        calendarCollectionIds=List.copyOf(calendarCollectionIds); priorityFixtureIds=Set.copyOf(priorityFixtureIds);
        if(date.getYear()<1 || date.getYear()>9999 || calendarCollectionIds.size()!=4
                || calendarCollectionIds.stream().distinct().count()!=4 || priorityFixtureIds.size()>100
                || estimatedCallsPerMatch<1 || estimatedCallsPerMatch>80){throw new IllegalArgumentException("Invalid selection command");}
    }
}
