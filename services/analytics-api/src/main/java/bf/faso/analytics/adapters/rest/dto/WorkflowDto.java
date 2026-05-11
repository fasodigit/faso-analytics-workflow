package bf.faso.analytics.adapters.rest.dto;

import bf.faso.analytics.domain.model.Workflow;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record WorkflowDto(
        @JsonProperty("workflow_id") UUID workflowId,
        @JsonProperty("sub_project") String subProject,
        String name,
        String description,
        @JsonProperty("owner_subject") String ownerSubject,
        @JsonProperty("parent_workflow_id") UUID parentWorkflowId,
        @JsonProperty("is_critical") boolean isCritical,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static WorkflowDto from(Workflow w) {
        return new WorkflowDto(
                w.workflowId(),
                w.subProject(),
                w.name(),
                w.description(),
                w.ownerSubject(),
                w.parentWorkflowId(),
                w.isCritical(),
                w.createdAt(),
                w.updatedAt()
        );
    }
}
