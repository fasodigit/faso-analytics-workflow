package bf.faso.analytics.adapters.rest.dto;

import bf.faso.analytics.domain.model.Deployment;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record DeploymentDto(
        @JsonProperty("deployment_id") UUID deploymentId,
        @JsonProperty("version_id") UUID versionId,
        String strategy,
        String status,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("completed_at") Instant completedAt,
        @JsonProperty("rolled_back_at") Instant rolledBackAt,
        @JsonProperty("rollback_reason") String rollbackReason,
        @JsonProperty("shadow_ends_at") Instant shadowEndsAt,
        @JsonProperty("actor_subject") String actorSubject
) {
    public static DeploymentDto from(Deployment d) {
        return new DeploymentDto(
                d.deploymentId(),
                d.versionId(),
                d.strategy().name(),
                d.status().name(),
                d.startedAt(),
                d.completedAt(),
                d.rolledBackAt(),
                d.rollbackReason(),
                d.shadowEndsAt(),
                d.actorSubject()
        );
    }
}
