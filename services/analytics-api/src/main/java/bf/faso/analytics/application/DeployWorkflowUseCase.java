package bf.faso.analytics.application;

import bf.faso.analytics.domain.model.Deployment;
import bf.faso.analytics.domain.model.Deployment.Strategy;
import bf.faso.analytics.domain.model.Workflow;
import bf.faso.analytics.domain.policy.DeploymentPolicy;
import bf.faso.analytics.domain.policy.DeploymentPolicy.DeploymentDecision;
import bf.faso.analytics.domain.port.DeploymentRepository;
import bf.faso.analytics.domain.port.DomainEventPublisher;
import bf.faso.analytics.domain.port.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeployWorkflowUseCase {

    public static final String TOPIC_DEPLOYED = "analytics.workflow.VERSION_DEPLOYED";

    private static final Logger LOG = LoggerFactory.getLogger(DeployWorkflowUseCase.class);

    private final WorkflowRepository workflowRepository;
    private final DeploymentRepository deploymentRepository;
    private final DomainEventPublisher eventPublisher;

    public DeployWorkflowUseCase(WorkflowRepository workflowRepository,
                                 DeploymentRepository deploymentRepository,
                                 DomainEventPublisher eventPublisher) {
        this.workflowRepository = workflowRepository;
        this.deploymentRepository = deploymentRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Deployment> execute(DeployCommand cmd) {
        if (cmd.strategy() == null) {
            return Mono.error(new IllegalArgumentException("strategy is required"));
        }
        return workflowRepository.findById(cmd.workflowId())
                .switchIfEmpty(Mono.error(new WorkflowNotFoundForDeploy(cmd.workflowId())))
                .flatMap(workflow -> evaluateAndExecute(workflow, cmd));
    }

    private Mono<Deployment> evaluateAndExecute(Workflow workflow, DeployCommand cmd) {
        Mono<Integer> approvalsMono = deploymentRepository.countApprovals(cmd.versionId())
                .defaultIfEmpty(0);
        Mono<Instant> simMono = deploymentRepository.latestSimulation(cmd.versionId())
                .map(i -> (Instant) i)
                .defaultIfEmpty(Instant.EPOCH);
        return Mono.zip(approvalsMono, simMono)
                .flatMap(tuple -> {
                    int approvals = tuple.getT1();
                    Instant lastSim = Instant.EPOCH.equals(tuple.getT2()) ? null : tuple.getT2();
                    DeploymentPolicy policy = DeploymentPolicy.defaults(workflow.isCritical());
                    DeploymentDecision decision = policy.evaluate(approvals, lastSim, cmd.strategy());
                    if (!decision.allowed()) {
                        return Mono.error(new DeploymentRefused(decision.reasons()));
                    }
                    Deployment pending = Deployment.create(
                            cmd.versionId(), cmd.strategy(), cmd.actorSubject(), cmd.shadowDurationHours());
                    return deploymentRepository.save(pending)
                            .flatMap(saved -> applyStrategy(workflow, saved))
                            .flatMap(this::emitDeployedEvent);
                });
    }

    private Mono<Deployment> applyStrategy(Workflow workflow, Deployment saved) {
        Deployment completed = saved.withStatus(Deployment.Status.COMPLETED);
        return switch (saved.strategy()) {
            // DIRECT : swap immediate. The new version is promoted and any previously DEPLOYED
            // version of the same workflow is demoted to DEPRECATED in the same transaction
            // (Phase 3 simplification : routing layer reads v_active_versions on each request).
            case DIRECT -> deploymentRepository
                    .markVersionDeployed(saved.versionId(), workflow.workflowId())
                    .thenReturn(completed);
            // SHADOW : the new version is promoted to DEPLOYED with a shadow_ends_at horizon.
            // Both versions coexist as DEPLOYED until shadow_ends_at, then a Phase 4 scheduler
            // demotes the old one. Phase 3 only persists the intent.
            case SHADOW -> deploymentRepository
                    .markVersionDeployed(saved.versionId(), workflow.workflowId())
                    .thenReturn(completed);
            // BLUE_GREEN : atomic swap (stub). Phase 3 = persist the swap, the actual
            // DragonflyDB MULTI/EXEC active_version_id rotation is deferred to Phase 4.
            case BLUE_GREEN -> deploymentRepository
                    .markVersionDeployed(saved.versionId(), workflow.workflowId())
                    .thenReturn(completed);
        };
    }

    private Mono<Deployment> emitDeployedEvent(Deployment d) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deploymentId", d.deploymentId().toString());
        payload.put("versionId", d.versionId().toString());
        payload.put("strategy", d.strategy().name());
        payload.put("actor", d.actorSubject());
        payload.put("startedAt", d.startedAt() == null ? null : d.startedAt().toString());
        payload.put("completedAt", d.completedAt() == null ? null : d.completedAt().toString());
        payload.put("shadowEndsAt", d.shadowEndsAt() == null ? null : d.shadowEndsAt().toString());
        return eventPublisher.publish(TOPIC_DEPLOYED, payload)
                .onErrorResume(err -> {
                    LOG.warn("VERSION_DEPLOYED event publish failed (continuing) : {}", err.toString());
                    return Mono.empty();
                })
                .thenReturn(d);
    }

    public record DeployCommand(
            UUID workflowId,
            UUID versionId,
            Strategy strategy,
            Integer shadowDurationHours,
            String actorSubject
    ) {
        public DeployCommand {
            if (workflowId == null) {
                throw new IllegalArgumentException("workflowId is required");
            }
            if (versionId == null) {
                throw new IllegalArgumentException("versionId is required");
            }
            if (actorSubject == null || actorSubject.isBlank()) {
                throw new IllegalArgumentException("actorSubject is required");
            }
        }
    }

    public static final class DeploymentRefused extends RuntimeException {
        private final List<String> reasons;

        public DeploymentRefused(List<String> reasons) {
            super("deployment refused: " + String.join("; ", reasons));
            this.reasons = List.copyOf(reasons);
        }

        public List<String> reasons() {
            return reasons;
        }
    }

    public static final class WorkflowNotFoundForDeploy extends RuntimeException {
        private final UUID workflowId;

        public WorkflowNotFoundForDeploy(UUID workflowId) {
            super("workflow not found: " + workflowId);
            this.workflowId = workflowId;
        }

        public UUID workflowId() {
            return workflowId;
        }
    }
}
