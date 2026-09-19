package com.bettingproject.collection.application.calendar;

import java.util.List;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.SnapshotHasher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"control-api", "batch-worker"})
@Transactional(propagation = Propagation.SUPPORTS)
public class CalendarDerivationService {
    private final CalendarCollectionStore store;
    private final List<CalendarPageParser> parsers;
    private final CalendarSnapshotEncoder encoder;
    private final CalendarEvidenceTransactions transactions;

    public CalendarDerivationService(CalendarCollectionStore store, List<CalendarPageParser> parsers,
            CalendarSnapshotEncoder encoder, CalendarEvidenceTransactions transactions) {
        this.store = store;
        this.parsers = List.copyOf(parsers);
        this.encoder = encoder;
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.SUPPORTS, noRollbackFor = CalendarPageParseException.class)
    public ParsedCalendarPage parse(CalendarCollectionRecord collection, CalendarPageRecord page) {
        var parser = parser(collection.capability().provider());
        if (!"RECEIVED".equals(page.responseCode()) || page.rawSnapshotId() == null) {
            throw new CalendarPageParseException("INCOMPATIBLE");
        }
        RawSnapshot raw;
        try {
            raw = store.readRaw(page.rawSnapshotId()).orElseThrow(
                    () -> new CalendarPageParseException("INTEGRITY_ERROR"));
        }
        catch (CalendarPageParseException | IllegalStateException failure) {
            transactions.refused(page, parser.version(), "INTEGRITY_ERROR");
            throw new CalendarPageParseException("INTEGRITY_ERROR");
        }
        if (!raw.sha256().equals(page.rawSha256())
                || !SnapshotHasher.sha256(raw.payload()).equals(page.rawSha256())
                || !raw.provider().equals(collection.capability().provider())
                || !raw.endpoint().equals("calendar/matches")
                || !raw.connectorVersion().equals(page.connectorVersion())) {
            transactions.refused(page, parser.version(), "INTEGRITY_ERROR");
            throw new CalendarPageParseException("INTEGRITY_ERROR");
        }
        try {
            // Per-call receive time, not the timestamp of a deduplicated raw row.
            return parser.parse(new CalendarPageRequest(collection.capability(), collection.date(),
                    collection.seasonStartYear(), page.pageOffset(), page.pageLimit()),
                    raw.payload(), page.receivedAt());
        }
        catch (CalendarPageParseException failure) {
            transactions.refused(page, parser.version(), failure.code());
            throw failure;
        }
    }

    public void apply(CalendarCollectionRecord collection, CalendarPageRecord page, ParsedCalendarPage parsed) {
        var parser = parser(collection.capability().provider());
        RawSnapshot derived = RawSnapshot.capture(collection.capability().provider(), "calendar/derived/v1",
                page.receivedAt(), encoder.encode(parsed.snapshot()), parser.version());
        transactions.apply(page, parser.version(), derived);
    }

    private CalendarPageParser parser(String provider) {
        return parsers.stream().filter(parser -> parser.provider().equals(provider)).findFirst()
                .orElseThrow(() -> new CalendarPageParseException("INCOMPATIBLE"));
    }

    public String parserVersion(String provider) { return parser(provider).version(); }
}
