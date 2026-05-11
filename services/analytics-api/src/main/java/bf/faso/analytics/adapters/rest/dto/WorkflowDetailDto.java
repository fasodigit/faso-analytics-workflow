package bf.faso.analytics.adapters.rest.dto;

import bf.faso.analytics.domain.model.Workflow;
import bf.faso.analytics.domain.model.WorkflowVersion;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WorkflowDetailDto(
        WorkflowDto workflow,
        @JsonProperty("versions") List<VersionSummaryDto> versions
) {
    public static WorkflowDetailDto from(Workflow w, List<WorkflowVersion> versions) {
        return new WorkflowDetailDto(
                WorkflowDto.from(w),
                versions.stream().map(VersionSummaryDto::from).toList()
        );
    }
}
