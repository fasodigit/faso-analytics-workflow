package bf.faso.analytics.adapters.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeployRequestDto(
        String strategy,
        @JsonProperty("shadow_duration_hours") Integer shadowDurationHours,
        @JsonProperty("actor_subject") String actorSubject
) {
}
