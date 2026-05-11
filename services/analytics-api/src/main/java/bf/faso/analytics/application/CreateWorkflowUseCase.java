package bf.faso.analytics.application;

import bf.faso.analytics.domain.model.Workflow;
import bf.faso.analytics.domain.model.WorkflowVersion;
import bf.faso.analytics.domain.port.AuditChainPort;
import bf.faso.analytics.domain.port.AuthorizationPort;
import bf.faso.analytics.domain.port.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.rctcwyvrn.blake3.Blake3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Service
public class CreateWorkflowUseCase {

    private final WorkflowRepository workflowRepository;
    private final AuditChainPort auditChainPort;
    private final AuthorizationPort authorizationPort;
    private final ObjectMapper objectMapper;

    @Autowired
    public CreateWorkflowUseCase(WorkflowRepository workflowRepository,
                                 AuditChainPort auditChainPort,
                                 AuthorizationPort authorizationPort,
                                 ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.auditChainPort = auditChainPort;
        this.authorizationPort = authorizationPort;
        this.objectMapper = objectMapper;
    }

    public Mono<Workflow> execute(CreateWorkflowCommand cmd) {
        return Mono.fromCallable(() -> Workflow.create(
                        cmd.subProject(),
                        cmd.name(),
                        cmd.description(),
                        cmd.ownerSubject(),
                        cmd.isCritical()))
                .flatMap(wf -> workflowRepository.save(wf)
                        .then(Mono.fromCallable(() -> buildInitialVersion(wf, cmd.definitionJson())))
                        .flatMap(workflowRepository::saveVersion)
                        .thenReturn(wf));
        // TODO Phase 1+ : transactional boundary (Spring R2DBC TransactionalOperator),
        // TODO Phase 1+ : Redpanda publish workflow.created event,
        // TODO Phase 1+ : AuditChainPort.recordCreate (BLAKE3 parent-chain via Vault transit).
    }

    private WorkflowVersion buildInitialVersion(Workflow wf, JsonNode definitionJson) {
        JsonNode definition = definitionJson != null
                ? definitionJson
                : objectMapper.createObjectNode();
        JsonNode emptySchemaSnapshot = JsonNodeFactory.instance.objectNode();
        byte[] blake3Self = blake3OfCanonical(definition);
        return WorkflowVersion.initialDraft(wf.workflowId(), definition, emptySchemaSnapshot, blake3Self);
    }

    private byte[] blake3OfCanonical(JsonNode definition) {
        try {
            String canonical = JsonCanonicalizer.canonicalize(definition, objectMapper);
            Blake3 hasher = Blake3.newInstance();
            hasher.update(canonical.getBytes(StandardCharsets.UTF_8));
            return hasher.digest();
        } catch (Exception e) {
            throw new IllegalStateException("failed to canonicalize/hash workflow definition", e);
        }
    }
}
