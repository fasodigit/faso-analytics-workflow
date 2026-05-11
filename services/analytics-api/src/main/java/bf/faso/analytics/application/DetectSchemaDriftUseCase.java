package bf.faso.analytics.application;

import bf.faso.analytics.domain.model.SchemaDrift;
import bf.faso.analytics.domain.model.WorkflowVersion;
import bf.faso.analytics.domain.policy.DriftDecision;
import bf.faso.analytics.domain.policy.DriftPolicy;
import bf.faso.analytics.domain.port.DomainEventPublisher;
import bf.faso.analytics.domain.port.SourceSchemaPort;
import bf.faso.analytics.domain.port.WorkflowRepository;
import bf.faso.analytics.domain.service.SchemaDriftDetector;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DetectSchemaDriftUseCase {

    public static final String TOPIC = "analytics.workflow.SCHEMA_DRIFT_DETECTED";

    private static final Logger LOG = LoggerFactory.getLogger(DetectSchemaDriftUseCase.class);

    private final WorkflowRepository workflowRepository;
    private final SourceSchemaPort sourceSchemaPort;
    private final DomainEventPublisher eventPublisher;

    public DetectSchemaDriftUseCase(WorkflowRepository workflowRepository,
                                    SourceSchemaPort sourceSchemaPort,
                                    DomainEventPublisher eventPublisher) {
        this.workflowRepository = workflowRepository;
        this.sourceSchemaPort = sourceSchemaPort;
        this.eventPublisher = eventPublisher;
    }

    public Mono<SchemaDriftReport> execute(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .switchIfEmpty(Mono.error(new WorkflowNotFoundException(workflowId)))
                .flatMap(wf -> workflowRepository.findVersionsByWorkflow(workflowId)
                        .collectList()
                        .flatMap(versions -> {
                            WorkflowVersion target = pickActiveVersion(versions);
                            if (target == null) {
                                return Mono.error(new WorkflowNotFoundException(workflowId));
                            }
                            return detectFor(workflowId, target);
                        }));
    }

    private Mono<SchemaDriftReport> detectFor(UUID workflowId, WorkflowVersion version) {
        DriftPolicy policy = extractPolicy(version.definitionJsonb());
        SourceCoords coords = extractSourceCoords(version.definitionJsonb());

        return sourceSchemaPort.getCurrentSchema(coords.connectionRef(), coords.tableSpec())
                .map(current -> buildReport(workflowId, version, current, policy))
                .onErrorResume(err -> {
                    LOG.warn("schema introspection failed for workflow={} version={} : {}",
                            workflowId, version.versionId(), err.toString());
                    return Mono.just(SchemaDriftReport.unavailable(
                            workflowId,
                            version.versionId(),
                            Instant.now(),
                            "introspection_unavailable"));
                })
                .flatMap(report -> maybeEmitEvent(report).thenReturn(report));
    }

    private SchemaDriftReport buildReport(UUID workflowId,
                                          WorkflowVersion version,
                                          JsonNode currentSchema,
                                          DriftPolicy policy) {
        SchemaDrift drift = SchemaDriftDetector.detect(
                version.schemaSnapshot(),
                currentSchema,
                policy.similarityThreshold());
        DriftDecision decision = policy.evaluate(drift);
        return SchemaDriftReport.ok(workflowId, version.versionId(), drift, decision, Instant.now());
    }

    private Mono<Void> maybeEmitEvent(SchemaDriftReport report) {
        if (report.error() != null) {
            return Mono.empty();
        }
        if (!report.decision().blocked() && report.drift().isEmpty()) {
            return Mono.empty();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", report.workflowId().toString());
        payload.put("versionId", report.versionId().toString());
        payload.put("blocked", report.decision().blocked());
        payload.put("reasons", report.decision().reasons());
        payload.put("newFields", report.drift().newFields());
        payload.put("removedFields", report.drift().removedFields());
        payload.put("renamedFields", report.drift().renamedFields());
        payload.put("typeChanges", report.drift().typeChanges());
        payload.put("detectedAt", report.detectedAt().toString());
        return eventPublisher.publish(TOPIC, payload);
    }

    private WorkflowVersion pickActiveVersion(List<WorkflowVersion> versions) {
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        return versions.stream()
                .filter(v -> "DEPLOYED".equals(v.status()))
                .max(Comparator.comparing(WorkflowVersion::createdAt))
                .orElseGet(() -> versions.stream()
                        .max(Comparator.comparing(WorkflowVersion::createdAt))
                        .orElse(null));
    }

    private DriftPolicy extractPolicy(JsonNode definition) {
        if (definition == null) {
            return DriftPolicy.defaults();
        }
        JsonNode spec = definition.get("spec");
        if (spec == null) {
            return DriftPolicy.defaults();
        }
        JsonNode policyNode = spec.get("driftPolicy");
        if (policyNode == null || !policyNode.isObject()) {
            return DriftPolicy.defaults();
        }
        DriftPolicy defaults = DriftPolicy.defaults();
        DriftPolicy.OnNewField onNew = enumOrDefault(policyNode, "onNewField",
                DriftPolicy.OnNewField.class, defaults.onNewField());
        DriftPolicy.OnRemovedField onRemoved = enumOrDefault(policyNode, "onRemovedField",
                DriftPolicy.OnRemovedField.class, defaults.onRemovedField());
        DriftPolicy.OnTypeChange onType = enumOrDefault(policyNode, "onTypeChange",
                DriftPolicy.OnTypeChange.class, defaults.onTypeChange());
        DriftPolicy.OnRenamed onRenamed = enumOrDefault(policyNode, "onRenamed",
                DriftPolicy.OnRenamed.class, defaults.onRenamed());
        double threshold = policyNode.hasNonNull("similarityThreshold")
                ? policyNode.get("similarityThreshold").asDouble(defaults.similarityThreshold())
                : defaults.similarityThreshold();
        return new DriftPolicy(onNew, onRemoved, onType, onRenamed, threshold);
    }

    private SourceCoords extractSourceCoords(JsonNode definition) {
        String connectionRef = null;
        String tableSpec = null;
        if (definition != null) {
            JsonNode spec = definition.get("spec");
            if (spec != null) {
                JsonNode source = spec.get("source");
                if (source != null && source.isObject()) {
                    if (source.hasNonNull("connectionRef")) {
                        connectionRef = source.get("connectionRef").asText();
                    }
                    tableSpec = buildTableSpec(source);
                }
            }
        }
        return new SourceCoords(connectionRef == null ? "" : connectionRef,
                tableSpec == null ? "" : tableSpec);
    }

    private String buildTableSpec(JsonNode source) {
        String type = source.hasNonNull("type") ? source.get("type").asText() : "";
        return switch (type) {
            case "yugabyte" -> textOrEmpty(source, "schema") + "." + textOrEmpty(source, "table");
            case "kobo" -> textOrEmpty(source, "assetUid");
            case "surveymonkey" -> textOrEmpty(source, "surveyId");
            case "dragonfly" -> textOrEmpty(source, "keyPattern");
            case "redpanda" -> textOrEmpty(source, "topic");
            case "metabase" -> textOrEmpty(source, "cardId");
            case "upload" -> textOrEmpty(source, "objectKey");
            default -> "";
        };
    }

    private static String textOrEmpty(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private static <E extends Enum<E>> E enumOrDefault(JsonNode node, String field, Class<E> type, E fallback) {
        if (!node.hasNonNull(field)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, node.get(field).asText());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private record SourceCoords(String connectionRef, String tableSpec) {}

    public static final class WorkflowNotFoundException extends RuntimeException {
        private final UUID workflowId;

        public WorkflowNotFoundException(UUID workflowId) {
            super("workflow not found: " + workflowId);
            this.workflowId = workflowId;
        }

        public UUID workflowId() {
            return workflowId;
        }
    }
}
