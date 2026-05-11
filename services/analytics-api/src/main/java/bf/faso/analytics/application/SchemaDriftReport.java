package bf.faso.analytics.application;

import bf.faso.analytics.domain.model.SchemaDrift;
import bf.faso.analytics.domain.policy.DriftDecision;

import java.time.Instant;
import java.util.UUID;

public record SchemaDriftReport(
        UUID workflowId,
        UUID versionId,
        SchemaDrift drift,
        DriftDecision decision,
        Instant detectedAt,
        String error
) {
    public static SchemaDriftReport ok(UUID workflowId,
                                       UUID versionId,
                                       SchemaDrift drift,
                                       DriftDecision decision,
                                       Instant detectedAt) {
        return new SchemaDriftReport(workflowId, versionId, drift, decision, detectedAt, null);
    }

    public static SchemaDriftReport unavailable(UUID workflowId,
                                                UUID versionId,
                                                Instant detectedAt,
                                                String error) {
        SchemaDrift empty = new SchemaDrift(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of());
        return new SchemaDriftReport(
                workflowId,
                versionId,
                empty,
                new DriftDecision(false, java.util.List.of()),
                detectedAt,
                error);
    }
}
