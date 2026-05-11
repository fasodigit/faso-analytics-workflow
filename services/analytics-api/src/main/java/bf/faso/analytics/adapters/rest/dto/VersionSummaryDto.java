package bf.faso.analytics.adapters.rest.dto;

import bf.faso.analytics.domain.model.WorkflowVersion;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record VersionSummaryDto(
        @JsonProperty("version_id") UUID versionId,
        String semver,
        String status,
        @JsonProperty("created_at") Instant createdAt
) {
    public static VersionSummaryDto from(WorkflowVersion v) {
        String semver = v.semverMajor() + "." + v.semverMinor() + "." + v.semverPatch()
                + (v.semverPreRelease() != null ? "-" + v.semverPreRelease() : "");
        return new VersionSummaryDto(v.versionId(), semver, v.status(), v.createdAt());
    }
}
