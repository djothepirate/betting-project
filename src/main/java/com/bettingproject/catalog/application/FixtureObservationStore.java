package com.bettingproject.catalog.application;

import com.bettingproject.catalog.domain.FixtureObservation;

public interface FixtureObservationStore {

    StoredFixtureObservation storeAndResolve(FixtureObservation observation);
}
