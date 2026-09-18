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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import static com.bettingproject.collection.adapter.replay.calendar.NativeCalendarFields.*;

@Component
public final class FootballDataCalendarPageParser implements CalendarPageParser {
    @Override
    public String provider() {
        return "football-data.org";
    }

    @Override
    public String version() {
        return "football-data-calendar-v1";
    }

    @Override
    public ParsedCalendarPage parse(CalendarPageRequest request, byte[] body, Instant observedAt) {
        try {
            JsonNode root = root(body, request, observedAt, provider());
            if (request.offset() != 0) {
                throw incomplete();
            }
            JsonNode matches = array(root, "matches");
            long count = nonNegativeInteger(object(root.get("resultSet")), "count");
            if (count != matches.size()) {
                throw incomplete();
            }
            JsonNode envelopeCompetition = object(root.get("competition"));
            String envelopeCompetitionId = id(envelopeCompetition, "id");
            exact(request.capability().providerCompetitionId(), text(envelopeCompetition, "code"));
            var fixtures = new ArrayList<DiscoveredFixture>();
            var identities = new HashSet<String>();
            for (JsonNode entry : matches) {
                object(entry);
                String fixtureId = id(entry, "id");
                if (!identities.add(fixtureId)) {
                    throw incompatible();
                }
                JsonNode competition = object(entry.get("competition"));
                String competitionCode = text(competition, "code");
                exact(request.capability().providerCompetitionId(), competitionCode);
                exact(envelopeCompetitionId, id(competition, "id"));
                JsonNode season = object(entry.get("season"));
                String seasonId = id(season, "id");
                String phase = text(entry, "stage");
                exact(request.capability().sourceSeason(), seasonId);
                exact(request.capability().sourcePhase(), phase);
                if (LocalDate.parse(text(season, "startDate")).getYear() != request.seasonStartYear()) {
                    throw incompatible();
                }
                var discoveredCompetition = new DiscoveredCompetition(competitionCode, text(competition, "name"),
                        optionalText(entry.get("area"), "code"), null, seasonId, phase);
                var home = team(entry, "homeTeam");
                var away = team(entry, "awayTeam");
                distinct(home, away);
                fixtures.add(new DiscoveredFixture(fixtureId, discoveredCompetition, kickoff(entry, "utcDate", request),
                        status(text(entry, "status")), null, false, home, away));
            }
            return new ParsedCalendarPage(new CalendarSnapshot(CalendarSnapshotSchemas.CANONICAL_V3,
                    provider(), observedAt, fixtures), null, count);
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
            case "SCHEDULED", "TIMED" -> "SCHEDULED";
            case "FINISHED", "POSTPONED", "CANCELLED" -> raw;
            default -> throw new CalendarPageParseException("UNSUPPORTED_STATUS");
        };
    }
}
