package com.bettingproject.catalog.adapter.collection;

import java.util.UUID;
import com.bettingproject.catalog.application.CalendarNormalizationService;
import com.bettingproject.collection.application.calendar.CalendarApplicationPort;
import com.bettingproject.collection.domain.RawSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"control-api", "batch-worker"})
public class CatalogCalendarApplicationAdapter implements CalendarApplicationPort {
    private final CalendarNormalizationService normalizer;

    public CatalogCalendarApplicationAdapter(CalendarNormalizationService normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public UUID normalize(RawSnapshot derived) {
        var result = normalizer.normalize(derived);
        if (!result.compatible()) {
            throw new IllegalStateException("derived calendar contract rejected");
        }
        return result.snapshotId();
    }
}
