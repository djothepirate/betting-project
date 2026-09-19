package com.bettingproject.collection.adapter.replay.calendar;

import com.bettingproject.collection.application.CalendarSnapshot;
import com.bettingproject.collection.application.CalendarSnapshotSchemas;
import com.bettingproject.collection.application.DiscoveredCompetition;
import com.bettingproject.collection.application.DiscoveredFixture;
import com.bettingproject.collection.application.calendar.CalendarPageParseException;
import com.bettingproject.collection.application.calendar.CalendarPageParser;
import com.bettingproject.collection.application.calendar.CalendarPageRequest;
import com.bettingproject.collection.application.calendar.ParsedCalendarPage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import static com.bettingproject.collection.adapter.replay.calendar.NativeCalendarFields.*;

@Component
public final class HighlightlyCalendarPageParser implements CalendarPageParser {
    @Override
    public String provider() {
        return "highlightly";
    }

    @Override
    public String version() {
        return "highlightly-calendar-v1";
    }

    @Override
    public ParsedCalendarPage parse(CalendarPageRequest request, byte[] body, Instant observedAt) {
        try {
            JsonNode root = root(body, request, observedAt, provider());
            JsonNode data = array(root, "data");
            JsonNode pagination = object(root.get("pagination"));
            long total = nonNegativeInteger(pagination, "totalCount");
            long offset = nonNegativeInteger(pagination, "offset");
            long limit = nonNegativeInteger(pagination, "limit");
            if (offset != request.offset() || limit != request.limit() || offset > total
                    || data.size() != Math.min(limit, total - offset)) {
                throw incomplete();
            }
            long next = offset + data.size();
            if (next < total && (data.isEmpty() || next > Integer.MAX_VALUE)) {
                throw incomplete();
            }
            var fixtures = new ArrayList<DiscoveredFixture>();
            var identities = new HashSet<String>();
            for (JsonNode entry : data) {
                object(entry);
                String fixtureId = id(entry, "id");
                if (!identities.add(fixtureId)) {
                    throw incompatible();
                }
                JsonNode league = object(entry.get("league"));
                String competitionId = id(league, "id");
                String season = Long.toString(nonNegativeInteger(league, "season"));
                String phase = text(entry, "round");
                exact(request.capability().providerCompetitionId(), competitionId);
                exact(request.capability().sourceSeason(), season);
                exact(Integer.toString(request.seasonStartYear()), season);
                exact(request.capability().sourcePhase(), phase);
                var competition = new DiscoveredCompetition(competitionId, text(league, "name"),
                        optionalText(entry.get("country"), "code"), null, season, phase);
                var home = team(entry, "homeTeam");
                var away = team(entry, "awayTeam");
                distinct(home, away);
                fixtures.add(new DiscoveredFixture(fixtureId, competition, kickoff(entry, "date", request),
                        status(text(object(entry.get("state")), "description")), null, false, home, away));
            }
            return new ParsedCalendarPage(new CalendarSnapshot(CalendarSnapshotSchemas.CANONICAL_V3,
                    provider(), observedAt, fixtures), next < total ? (int) next : null, total);
        }
        catch (CalendarPageParseException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw incompatible();
        }
    }

    private static String status(String raw) {
        return switch (raw) {
            case "Not started" -> "SCHEDULED";
            case "Finished", "Finished after penalties", "Finished after extra time" -> "FINISHED";
            case "Postponed" -> "POSTPONED";
            case "Cancelled" -> "CANCELLED";
            default -> throw new CalendarPageParseException("UNSUPPORTED_STATUS");
        };
    }
}
