package com.bettingproject.collection.application.enrichment;

/** Exact, versioned interpretation of native provider final statuses. Unknown values are never final. */
public final class EnrichmentFinalStatusPolicy {
    public static final String VERSION = "enrichment-final-status-v1";

    public String version() { return VERSION; }

    public boolean isFinal(String provider, String status) {
        if (provider == null || status == null) { return false; }
        return switch (provider) {
            case "highlightly" -> switch (status) {
                case "Finished", "Finished after penalties", "Finished after extra time" -> true;
                default -> false;
            };
            case "football-data.org" -> "FINISHED".equals(status) || "AWARDED".equals(status);
            default -> false;
        };
    }
}
