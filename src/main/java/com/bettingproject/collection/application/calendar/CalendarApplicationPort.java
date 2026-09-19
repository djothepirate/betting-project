package com.bettingproject.collection.application.calendar;

import java.util.UUID;
import com.bettingproject.collection.domain.RawSnapshot;

/** Implemented by the catalogue: collection never depends on catalog. */
public interface CalendarApplicationPort {
    UUID normalize(RawSnapshot derived);
}
