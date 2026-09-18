package com.bettingproject.collection.application.calendar;

import java.util.UUID;

public record CalendarCollectionResult(UUID collectionId, String status, String reasonCode) {
}
