package com.bettingproject.collection.application.control;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.operations.application.jobs.JobQueryPort;
import com.bettingproject.operations.domain.JobModel;
import com.bettingproject.shared.application.ReadPage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
@Transactional(readOnly = true)
public class CollectionQueryService {
    private final CollectionQueryPort data;
    private final JobQueryPort jobs;
    public CollectionQueryService(CollectionQueryPort data, JobQueryPort jobs) { this.data = data; this.jobs = jobs; }
    public ReadPage<CollectionQueryPort.WindowView> windows(CollectionQueryPort.Filter filter, ReadPage.Request page) {
        return ReadPage.of(data.windows(filter, page), page);
    }
    public Optional<CollectionQueryPort.WindowView> window(UUID id) { return data.window(id); }
    public ReadPage<CollectionQueryPort.IntentView> intents(UUID id, String state, ReadPage.Request page) {
        return ReadPage.of(data.intents(id, state, page), page);
    }
    public ReadPage<CollectionQueryPort.IncidentView> incidents(CollectionQueryPort.Filter filter, ReadPage.Request page) {
        return ReadPage.of(data.incidents(filter, page), page);
    }
    public ReadPage<CollectionQueryPort.BudgetEventView> budgetEvents(UUID id, ReadPage.Request page) {
        return ReadPage.of(data.budgetEvents(id, page), page);
    }
    public ReadPage<CollectionQueryPort.CalendarView> calendars(CollectionQueryPort.Filter filter, LocalDate date, ReadPage.Request page) {
        return ReadPage.of(data.calendars(filter, date, page), page);
    }
    public Optional<CollectionQueryPort.CalendarView> calendar(UUID id) { return data.calendar(id); }
    public ReadPage<CollectionQueryPort.PageView> pages(UUID id, ReadPage.Request page) {
        return ReadPage.of(data.pages(id, page), page);
    }
    public ReadPage<JobQueryPort.JobView> jobs(JobModel.Type type, JobModel.Status state, ReadPage.Request page) {
        return ReadPage.of(jobs.jobs(type, state, page), page);
    }
    public Optional<JobQueryPort.JobView> job(UUID id) { return jobs.job(id); }
    public ReadPage<JobQueryPort.EventView> jobEvents(UUID id, ReadPage.Request page) {
        return ReadPage.of(jobs.events(id, page), page);
    }
}
