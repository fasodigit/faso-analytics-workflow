package bf.faso.analytics.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Requête d'export (commande applicative).
 *
 * <p>Reçue depuis {@code POST /v1/exports}. Le caller a déjà résolu
 * la définition JSONB du workflow (depuis {@code workflow_versions}) et la passe ici
 * via {@link #definitionJsonb()} pour éviter de coupler l'adaptateur au {@link bf.faso.analytics.domain.port.WorkflowRepository}.
 */
public record ExportRequest(
        UUID workflowId,
        UUID versionId,
        String format,
        Map<String, Object> filters,
        String period,
        List<String> deliverTo,
        JsonNode definitionJsonb
) {
    /**
     * Formats supportés (alignés sur le discriminator {@code Output.kind} du JSON Schema workflow-v1).
     */
    public static final List<String> SUPPORTED_FORMATS = List.of("pdf", "excel", "pptx", "metabase");

    public ExportRequest {
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }
    }
}
