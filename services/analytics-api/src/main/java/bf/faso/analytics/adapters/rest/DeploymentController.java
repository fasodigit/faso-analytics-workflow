package bf.faso.analytics.adapters.rest;

import bf.faso.analytics.adapters.rest.dto.DeployRequestDto;
import bf.faso.analytics.adapters.rest.dto.DeploymentDto;
import bf.faso.analytics.adapters.rest.dto.RollbackRequestDto;
import bf.faso.analytics.application.DeployWorkflowUseCase;
import bf.faso.analytics.application.DeployWorkflowUseCase.DeployCommand;
import bf.faso.analytics.application.DeployWorkflowUseCase.DeploymentRefused;
import bf.faso.analytics.application.DeployWorkflowUseCase.WorkflowNotFoundForDeploy;
import bf.faso.analytics.application.RollbackWorkflowUseCase;
import bf.faso.analytics.application.RollbackWorkflowUseCase.AlreadyRolledBack;
import bf.faso.analytics.application.RollbackWorkflowUseCase.DeploymentNotFound;
import bf.faso.analytics.application.RollbackWorkflowUseCase.NotRollbackable;
import bf.faso.analytics.domain.model.Deployment;
import bf.faso.analytics.domain.port.DeploymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class DeploymentController {

    private final DeployWorkflowUseCase deployUseCase;
    private final RollbackWorkflowUseCase rollbackUseCase;
    private final DeploymentRepository deploymentRepository;

    public DeploymentController(DeployWorkflowUseCase deployUseCase,
                                RollbackWorkflowUseCase rollbackUseCase,
                                DeploymentRepository deploymentRepository) {
        this.deployUseCase = deployUseCase;
        this.rollbackUseCase = rollbackUseCase;
        this.deploymentRepository = deploymentRepository;
    }

    @PostMapping("/v1/workflows/{workflowId}/versions/{versionId}/deploy")
    public Mono<ResponseEntity<Object>> deploy(@PathVariable UUID workflowId,
                                               @PathVariable UUID versionId,
                                               @RequestBody DeployRequestDto req) {
        Deployment.Strategy strategy;
        try {
            strategy = Deployment.Strategy.valueOf(req.strategy());
        } catch (Exception e) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid strategy: " + req.strategy())));
        }
        DeployCommand cmd = new DeployCommand(
                workflowId, versionId, strategy, req.shadowDurationHours(), req.actorSubject());
        return deployUseCase.execute(cmd)
                .map(d -> (Object) DeploymentDto.from(d))
                .map(dto -> ResponseEntity.status(HttpStatus.CREATED).body(dto))
                .onErrorResume(DeploymentRefused.class, e -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("error", "deployment_refused");
                    body.put("reasons", e.reasons());
                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body((Object) body));
                })
                .onErrorResume(WorkflowNotFoundForDeploy.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) Map.of("error", "workflow_not_found",
                                        "workflowId", e.workflowId().toString()))));
    }

    @PostMapping("/v1/workflows/{workflowId}/versions/{versionId}/rollback")
    public Mono<ResponseEntity<Object>> rollback(@PathVariable UUID workflowId,
                                                 @PathVariable UUID versionId,
                                                 @RequestBody RollbackRequestDto req) {
        UUID deploymentId = req.deploymentId();
        Mono<UUID> deploymentIdMono = deploymentId != null
                ? Mono.just(deploymentId)
                : deploymentRepository.findByVersionId(versionId)
                        .filter(d -> d.status() == Deployment.Status.COMPLETED)
                        .next()
                        .map(Deployment::deploymentId)
                        .switchIfEmpty(Mono.error(new DeploymentNotFound(versionId)));
        return deploymentIdMono
                .flatMap(id -> rollbackUseCase.execute(id, req.reason(), req.actorSubject()))
                .map(d -> (Object) DeploymentDto.from(d))
                .map(dto -> ResponseEntity.ok().body(dto))
                .onErrorResume(DeploymentNotFound.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) Map.of("error", "deployment_not_found",
                                        "id", e.deploymentId().toString()))))
                .onErrorResume(AlreadyRolledBack.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body((Object) Map.of("error", "already_rolled_back",
                                        "deploymentId", e.deploymentId().toString()))))
                .onErrorResume(NotRollbackable.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body((Object) Map.of("error", "not_rollbackable",
                                        "status", e.status().name()))));
    }

    @GetMapping("/v1/workflows/{workflowId}/deployments")
    public Flux<DeploymentDto> list(@PathVariable UUID workflowId) {
        return deploymentRepository.findByWorkflowId(workflowId)
                .map(DeploymentDto::from);
    }
}
