package com.bettingproject.collection.application.enrichment;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import com.bettingproject.enrichment.domain.ProviderEnrichmentObservation;
import com.bettingproject.qualification.domain.QualityFinding;
import com.bettingproject.collection.domain.SnapshotHasher;

/** Derived JSON only; raw bytes remain exclusively in raw_snapshot and are never copied here. */
public record EnrichmentObservationWrite(ProviderEnrichmentObservation observation,
        String representationJson, String representationSha256, List<QualityFinding> findings) {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    public EnrichmentObservationWrite {
        Objects.requireNonNull(observation);
        if (representationJson == null || representationJson.isBlank()
                || representationSha256 == null || !HASH.matcher(representationSha256).matches()) {
            throw new IllegalArgumentException("Invalid derived representation");
        }
        String actualHash = SnapshotHasher.sha256(representationJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!actualHash.equals(representationSha256)) {
            throw new IllegalArgumentException("Derived representation hash does not match its bytes");
        }
        findings = List.copyOf(Objects.requireNonNull(findings));
        UUID expectedObservation = observation.id();
        if (findings.stream().anyMatch(finding -> !finding.enrichmentObservationId().equals(expectedObservation))) {
            throw new IllegalArgumentException("Quality findings must refer to the candidate observation");
        }
    }
}
