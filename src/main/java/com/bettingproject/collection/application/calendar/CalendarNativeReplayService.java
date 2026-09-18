package com.bettingproject.collection.application.calendar;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal manual replay by stored page identity; no client, budget or file path. */
@Service
@Profile("control-api")
@Transactional(propagation = Propagation.NEVER)
public class CalendarNativeReplayService {
    private final CalendarCollectionStore store;
    private final CalendarDerivationService derivation;

    public CalendarNativeReplayService(CalendarCollectionStore store, CalendarDerivationService derivation) {
        this.store = store;
        this.derivation = derivation;
    }

    public String replayPage(UUID pageId) {
        var page = store.findPage(pageId);
        if (page.isEmpty()) {
            return "NOT_FOUND";
        }
        var collection = store.findCollection(page.get().collectionId()).orElseThrow();
        try {
            var parsed = derivation.parse(collection, page.get());
            derivation.apply(collection, page.get(), parsed);
            return "APPLIED";
        }
        catch (CalendarPageParseException failure) {
            return failure.code();
        }
    }
}
