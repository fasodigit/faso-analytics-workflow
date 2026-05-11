package bf.faso.analytics.adapters.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payload {@code POST /v1/exports} :
 * <pre>
 * {
 *   "workflow_id":   "uuid",
 *   "version_id":    "uuid"            (optionnel — sinon dernière version active),
 *   "format":        "pdf"|"excel"|"pptx"|"metabase",
 *   "filters":       { ... }           (optionnel),
 *   "period":        "2025-Q4"         (optionnel — granularité libre),
 *   "deliver_to":    ["email@..."]     (optionnel),
 *   "definition":    { ... JSON ... }  (optionnel — Phase 3 : caller peut fournir directement la def)
 * }
 * </pre>
 */
public record CreateExportRequestDto(
        @JsonProperty("workflow_id") UUID workflowId,
        @JsonProperty("version_id") UUID versionId,
        String format,
        Map<String, Object> filters,
        String period,
        @JsonProperty("deliver_to") List<String> deliverTo,
        JsonNode definition
) {
}
