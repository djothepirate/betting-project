package com.bettingproject.identity.application;

import com.bettingproject.identity.domain.NormalizationAnomalyEvent;

public interface NormalizationAnomalyEventJournal {

    void append(NormalizationAnomalyEvent event);
}
