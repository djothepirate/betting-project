package com.bettingproject.collection.application.calendar;

import java.util.UUID;
import com.bettingproject.operations.domain.JobModel.Type;

public record CalendarJobInput(UUID jobId, Type type, CalendarCollectionCommand discovery,
        UUID replayPageId, String parserVersion, String registrySha256) {
    public CalendarJobInput {
        if (jobId == null || type == null || registrySha256 == null || !registrySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid calendar job input");
        }
        com.bettingproject.operations.domain.JobModel.text(parserVersion, 64);
        if (type == Type.CALENDAR_DISCOVERY) {
            if (discovery == null || !jobId.equals(discovery.id()) || replayPageId != null) {
                throw new IllegalArgumentException("Invalid discovery input");
            }
        }
        else if (type != Type.REPLAY_NORMALIZATION || discovery != null || replayPageId == null) {
            throw new IllegalArgumentException("Invalid replay input");
        }
    }
}
