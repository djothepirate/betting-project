package com.bettingproject.collection.application;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceCollectionRequestTest {

    @Test
    void buildsTheDocumentedHighlightlyPaths() {
        assertEquals("/matches/1347698848", CollectionEndpoint.DETAIL.relativePath("1347698848"));
        assertEquals("/lineups/1347698848", CollectionEndpoint.LINEUP.relativePath("1347698848"));
        assertEquals("/statistics/1347698848", CollectionEndpoint.STATISTICS.relativePath("1347698848"));
        assertEquals("/events/1347698848", CollectionEndpoint.EVENTS.relativePath("1347698848"));
        assertEquals("/box-score/1347698848", CollectionEndpoint.BOX_SCORE.relativePath("1347698848"));
    }

    @Test
    void rejectsUnknownScenariosAndUnsafeIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new EvidenceCollectionRequest(
                "ENR-P01",
                "1347698848?key=secret",
                CollectionEndpoint.DETAIL,
                "FIRST_AUTHORIZED",
                List.of("ID-01")));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceCollectionRequest(
                "ENR-P01",
                "1347698848",
                CollectionEndpoint.DETAIL,
                "FIRST_AUTHORIZED",
                List.of("UNKNOWN")));
    }
}
