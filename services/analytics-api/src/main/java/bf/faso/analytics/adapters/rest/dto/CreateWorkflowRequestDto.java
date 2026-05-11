package bf.faso.analytics.adapters.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record CreateWorkflowRequestDto(
        @JsonProperty("sub_project") String subProject,
        String name,
        String description,
        @JsonProperty("owner_subject") String ownerSubject,
        @JsonProperty("is_critical") Boolean isCritical,
        JsonNode definition
) {
}
