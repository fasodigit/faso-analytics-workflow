package bf.faso.analytics.domain.port;

import bf.faso.analytics.domain.model.Deployment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface DeploymentRepository {

    Mono<Deployment> save(Deployment deployment);

    Mono<Deployment> findById(UUID deploymentId);

    Flux<Deployment> findByVersionId(UUID versionId);

    Flux<Deployment> findByWorkflowId(UUID workflowId);

    Mono<Deployment> findActiveByWorkflow(UUID workflowId);

    Mono<Integer> countApprovals(UUID versionId);

    Mono<Instant> latestSimulation(UUID versionId);

    Mono<Void> performRollback(UUID deploymentId,
                               UUID versionId,
                               UUID workflowId,
                               String reason);

    Mono<Void> markVersionDeployed(UUID versionId, UUID workflowId);
}
