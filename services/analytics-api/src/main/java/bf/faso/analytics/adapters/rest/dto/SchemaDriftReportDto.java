package bf.faso.analytics.adapters.rest.dto;

import bf.faso.analytics.application.SchemaDriftReport;
import bf.faso.analytics.domain.model.SchemaDrift;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchemaDriftReportDto(
        @JsonProperty("workflow_id") UUID workflowId,
        @JsonProperty("version_id") UUID versionId,
        @JsonProperty("detected_at") Instant detectedAt,
        boolean blocked,
        List<String> reasons,
        @JsonProperty("new_fields") List<String> newFields,
        @JsonProperty("removed_fields") List<String> removedFields,
        @JsonProperty("renamed_fields") List<RenamedFieldDto> renamedFields,
        @JsonProperty("type_changes") List<TypeChangeDto> typeChanges,
        String error
) {
    public record RenamedFieldDto(
            String from,
            String to,
            @JsonProperty("similarity_score") double similarityScore
    ) {}

    public record TypeChangeDto(
            String field,
            @JsonProperty("from_type") String fromType,
            @JsonProperty("to_type") String toType
    ) {}

    public static SchemaDriftReportDto from(SchemaDriftReport r) {
        SchemaDrift d = r.drift();
        return new SchemaDriftReportDto(
                r.workflowId(),
                r.versionId(),
                r.detectedAt(),
                r.decision().blocked(),
                r.decision().reasons(),
                d.newFields(),
                d.removedFields(),
                d.renamedFields().stream()
                        .map(rf -> new RenamedFieldDto(rf.from(), rf.to(), rf.similarityScore()))
                        .toList(),
                d.typeChanges().stream()
                        .map(tc -> new TypeChangeDto(tc.field(), tc.fromType(), tc.toType()))
                        .toList(),
                r.error()
        );
    }
}
