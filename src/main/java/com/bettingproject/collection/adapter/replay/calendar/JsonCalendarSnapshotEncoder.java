package com.bettingproject.collection.adapter.replay.calendar;

import com.bettingproject.collection.application.CalendarSnapshot;
import com.bettingproject.collection.application.calendar.CalendarSnapshotEncoder;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonCalendarSnapshotEncoder implements CalendarSnapshotEncoder {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Override
    public byte[] encode(CalendarSnapshot snapshot) {
        return mapper.writeValueAsBytes(snapshot);
    }
}
