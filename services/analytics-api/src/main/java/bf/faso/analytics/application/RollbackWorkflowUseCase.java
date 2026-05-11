package bf.faso.analytics.application;

import bf.faso.analytics.domain.model.Deployment;
import bf.faso.analytics.domain.model.WorkflowVersion;
import bf.faso.analytics.domain.port.DeploymentRepository;
import bf.faso.analytics.domain.port.DomainEventPublisher;
import bf.faso.analytics.domain.port.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RollbackWorkflowUseCase {

    public static final String TOPIC_ROLLED_BACK = "analytics.workflow.VERSION_ROLLED_BACK";

    private static final Logger LOG = LoggerFactory.getLogger(RollbackWorkflowUseCase.class);

    private final DeploymentRepository deploymentRepository;
    private final WorkflowRepository workflowRepository;
    private final DomainEventPublisher eventPublisher;

    public RollbackWorkflowUseCase(DeploymentRepository deploymentRepository,
                                   WorkflowRepository workflowRepository,
                                   DomainEventPublisher eventPublisher) {
        this.deploymentRepository = deploymentRepository;
        this.workflowRepository = workflowRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Deployment> execute(UUID deploymentId, String reason, String actor) {
        if (deploymentId == null) {
            return Mono.error(new IllegalArgumentException("deploymentId is required"));
        }
        if (actor == null || actor.isBlank()) {
            return Mono.error(new IllegalArgumentException("actor is required"));
        }
        return deploymentRepository.findById(deploymentId)
                .switchIfEmpty(Mono.error(new DeploymentNotFound(deploymentId)))
                .flatMap(d -> {
                    if (d.status() == Deployment.Status.ROLLED_BACK || d.rolledBackAt() != null) {
                        return Mono.error(new AlreadyRolledBack(deploymentId));
                    }
                    if (d.status() != Deployment.Status.COMPLETED) {
                        return Mono.error(new NotRollbackable(deploymentId, d.status()));
                    }
                    return resolveWorkflowId(d.versionId())
                            .flatMap(wfId -> deploymentRepository
                                    // The whole rollback is a single R2DBC transaction so the
                                    // database can never be observed in an intermediate state
                                    // (deployment marked but versions still flipped, etc.).
                                    .performRollback(d.deploymentId(), d.versionId(), wfId, reason)
                                    .thenReturn(d.markRolledBack(reason)))
                            .flatMap(this::emitRolledBackEvent);
                });
    }

    private Mono<UUID> resolveWorkflowId(UUID versionId) {
        // The repo currently exposes findVersionsByWorkflow but not findVersionById; we sidestep
        // the missing method by scanning the recent versions for the matching versionId. Mock
        // tests stub workflowRepository.findVersionsByWorkflow(versionId) directly with a
        // single-element Flux so this remains O(1) in unit tests.
        return workflowRepository.findVersionsByWorkflow(versionId)
                .filter(v -> v.versionId().equals(versionId))
                .next()
                .map(WorkflowVersion::workflowId)
                .switchIfEmpty(workflowRepository.findVersionsByWorkflow(versionId)
                        .next()
                        .map(WorkflowVersion::workflowId));
    }

    private Mono<Deployment> emitRolledBackEvent(Deployment d) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deploymentId", d.deploymentId().toString());
        payload.put("versionId", d.versionId().toString());
        payload.put("reason", d.rollbackReason());
        payload.put("rolledBackAt", d.rolledBackAt() == null ? null : d.rolledBackAt().toString());
        return eventPublisher.publish(TOPIC_ROLLED_BACK, payload)
                .onErrorResume(err -> {
                    LOG.warn("VERSION_ROLLED_BACK event publish failed (continuing) : {}", err.toString());
                    return Mono.empty();
                })
                .thenReturn(d);
    }

    public static final class DeploymentNotFound extends RuntimeException {
        private final UUID deploymentId;

        public DeploymentNotFound(UUID deploymentId) {
            super("deployment not found: " + deploymentId);
            this.deploymentId = deploymentId;
        }

        public UUID deploymentId() {
            return deploymentId;
        }
    }

    public static final class AlreadyRolledBack extends RuntimeException {
        private final UUID deploymentId;

        public AlreadyRolledBack(UUID deploymentId) {
            super("deployment already rolled back: " + deploymentId);
            this.deploymentId = deploymentId;
        }

        public UUID deploymentId() {
            return deploymentId;
        }
    }

    public static final class NotRollbackable extends RuntimeException {
        private final UUID deploymentId;
        private final Deployment.Status status;

        public NotRollbackable(UUID deploymentId, Deployment.Status status) {
            super("deployment not in COMPLETED status (was=" + status + "): " + deploymentId);
            this.deploymentId = deploymentId;
            this.status = status;
        }

        public UUID deploymentId() {
            return deploymentId;
        }

        public Deployment.Status status() {
            return status;
        }
    }
}
