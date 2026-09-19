package com.bettingproject.collection.application.calendar;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import com.bettingproject.collection.application.budget.BudgetCommands;
import com.bettingproject.collection.application.budget.ProviderBudgetTransactions;
import com.bettingproject.collection.application.budget.ProviderBudgetRepository;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;
import com.bettingproject.collection.domain.budget.BudgetModel.Proof;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short atomic transactions; no transport or parsing runs in this component. */
@Service
@Profile({"control-api", "batch-worker"})
@Transactional
public class CalendarEvidenceTransactions {
    private final CalendarCollectionStore store;
    private final ProviderBudgetTransactions budget;
    private final CalendarApplicationPort application;
    private final Clock clock;
    private final ProviderBudgetRepository budgets;

    public CalendarEvidenceTransactions(CalendarCollectionStore store, ProviderBudgetTransactions budget,
            CalendarApplicationPort application, Clock clock, ProviderBudgetRepository budgets) {
        this.store = store;
        this.budget = budget;
        this.application = application;
        this.clock = clock;
        this.budgets = budgets;
    }

    public void recordResponse(CalendarPageRecord page, String provider, CalendarPageResponse response) {
        String code = response.failureCode() != null ? response.failureCode()
                : response.httpStatus() >= 200 && response.httpStatus() < 300 ? "RECEIVED" : "HTTP_ERROR";
        RawSnapshot raw = "SECRET_ECHO".equals(code) ? null : RawSnapshot.capture(provider,
                "calendar/matches", response.receivedAt(), response.body(), page.connectorVersion());
        // Budget locks precede intent/page locks throughout the collection path.
        String responseFingerprint = SnapshotHasher.sha256((response.httpStatus() + ":" + code + ":"
                + response.quotaRemaining() + ":" + SnapshotHasher.sha256(response.body()))
                .getBytes(StandardCharsets.UTF_8));
        var result = response.httpStatus() != 0
                ? budget.recordOutcome(new BudgetCommands.Outcome(page.intentId(), response.httpStatus(),
                        responseFingerprint, null))
                : budget.markUncertain(page.intentId());
        if (result.code() != ResultCode.OK) {
            throw new IllegalStateException("calendar budget outcome rejected");
        }
        boolean alreadyRecorded = !"PENDING".equals(store.findPage(page.id()).orElseThrow().responseCode());
        store.recordResponse(page.id(), raw, response.requestedAt(), response.receivedAt(),
                response.httpStatus() == 0 ? null : response.httpStatus(), response.quotaRemaining(), code);
        if (!alreadyRecorded && response.quotaRemaining() != null) {
            var window = budgets.findWindow(result.windowId()).orElseThrow();
            if (window.capacity() != null) {
                Proof proof = new Proof("calendar-response:" + page.id(), SnapshotHasher.sha256(
                        (response.httpStatus() + ":" + response.quotaRemaining() + ":"
                                + SnapshotHasher.sha256(response.body())).getBytes(StandardCharsets.UTF_8)));
                Instant observed = response.receivedAt().truncatedTo(ChronoUnit.MICROS);
                var previous = window.currentObservationId() == null ? null
                        : budgets.findObservation(window.currentObservationId()).orElseThrow();
                if (response.quotaRemaining() > window.capacity() || observed.isBefore(window.startsAt())) {
                    budget.recordUnusableQuota(window.id(), proof);
                }
                else if (previous != null && observed.isBefore(previous.validUntil())
                        && observed.isBefore(window.endsAt())) {
                    // A header proves a remaining count, never which in-flight intentions it covers.
                    // Keep the explicitly initialized validity horizon; do not extend it from a header.
                    budget.observeQuota(window.id(), new BudgetCommands.QuotaReading(UUID.randomUUID(),
                            response.quotaRemaining(), observed, previous.validUntil(), proof, Set.of()));
                }
            }
        }
    }

    public void apply(CalendarPageRecord page, String parserVersion, RawSnapshot derived) {
        UUID derivedId = application.normalize(derived);
        store.appendDerivation(new CalendarDerivationRecord(UUID.randomUUID(), page.id(), parserVersion,
                "APPLIED", derivedId, page.rawSha256(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    public void refused(CalendarPageRecord page, String parserVersion, String code) {
        store.appendDerivation(new CalendarDerivationRecord(UUID.randomUUID(), page.id(), parserVersion,
                code, null, page.rawSha256(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
