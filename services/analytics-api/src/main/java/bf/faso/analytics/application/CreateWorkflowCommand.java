package bf.faso.analytics.application;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateWorkflowCommand(
        String subProject,
        String name,
        String description,
        String ownerSubject,
        boolean isCritical,
        JsonNode definitionJson
) {
}
