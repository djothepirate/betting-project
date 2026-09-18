package com.bettingproject.catalog.application;

import java.util.Optional;

public interface CalendarCanonicalContextPolicy {
    Optional<CalendarCanonicalContext> resolve(CalendarAuthorityKey sourceKey);
}
