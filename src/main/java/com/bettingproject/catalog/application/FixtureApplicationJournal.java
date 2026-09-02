package com.bettingproject.catalog.application;

import com.bettingproject.catalog.domain.FixtureApplicationLog;

public interface FixtureApplicationJournal {

    void append(FixtureApplicationLog applicationLog);
}
