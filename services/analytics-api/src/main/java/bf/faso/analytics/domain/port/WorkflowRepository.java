package bf.faso.analytics.domain.port;

import bf.faso.analytics.domain.model.Workflow;
import bf.faso.analytics.domain.model.WorkflowVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface WorkflowRepository {

    Mono<Workflow> save(Workflow workflow);

    Mono<Workflow> findById(UUID workflowId);

    Flux<Workflow> findAllBySubProject(String subProject, int page, int size);

    Mono<Long> count(String subProject);

    Mono<WorkflowVersion> saveVersion(WorkflowVersion version);

    Flux<WorkflowVersion> findVersionsByWorkflow(UUID workflowId);
}
