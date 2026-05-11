package bf.faso.analytics.adapters.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RollbackRequestDto(
        String reason,
        @JsonProperty("actor_subject") String actorSubject,
        @JsonProperty("deployment_id") java.util.UUID deploymentId
) {
}
