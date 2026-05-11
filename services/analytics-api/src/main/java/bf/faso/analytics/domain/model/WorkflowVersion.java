package bf.faso.analytics.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record WorkflowVersion(
        UUID versionId,
        UUID workflowId,
        int semverMajor,
        int semverMinor,
        int semverPatch,
        String semverPreRelease,
        String status,
        JsonNode definitionJsonb,
        JsonNode schemaSnapshot,
        byte[] blake3Self,
        byte[] blake3Parent,
        Instant createdAt,
        Instant deployedAt,
        Instant deprecatedAt
) {
    private static final java.util.Set<String> ALLOWED_STATUS = java.util.Set.of(
            "DRAFT", "SIMULATING", "VALIDATED", "DEPLOYED", "DEPRECATED", "ARCHIVED"
    );

    public WorkflowVersion {
        if (workflowId == null) {
            throw new IllegalArgumentException("workflowId is required");
        }
        if (semverMajor < 0 || semverMinor < 0 || semverPatch < 0) {
            throw new IllegalArgumentException("semver components must be >= 0");
        }
        if (status == null || !ALLOWED_STATUS.contains(status)) {
            throw new IllegalArgumentException("invalid status: " + status);
        }
        if (definitionJsonb == null) {
            throw new IllegalArgumentException("definitionJsonb is required");
        }
        if (schemaSnapshot == null) {
            throw new IllegalArgumentException("schemaSnapshot is required");
        }
        if (blake3Self == null || blake3Self.length == 0) {
            throw new IllegalArgumentException("blake3Self is required");
        }
    }

    public static WorkflowVersion initialDraft(UUID workflowId,
                                               JsonNode definitionJsonb,
                                               JsonNode schemaSnapshot,
                                               byte[] blake3Self) {
        return new WorkflowVersion(
                UUID.randomUUID(),
                workflowId,
                1,
                0,
                0,
                "draft.1",
                "DRAFT",
                definitionJsonb,
                schemaSnapshot,
                blake3Self,
                null,
                Instant.now(),
                null,
                null
        );
    }
}
