package bf.faso.analytics.adapters.rest;

import bf.faso.analytics.adapters.rest.dto.CreateWorkflowRequestDto;
import bf.faso.analytics.adapters.rest.dto.PageDto;
import bf.faso.analytics.adapters.rest.dto.WorkflowDetailDto;
import bf.faso.analytics.adapters.rest.dto.WorkflowDto;
import bf.faso.analytics.application.CreateWorkflowCommand;
import bf.faso.analytics.application.CreateWorkflowUseCase;
import bf.faso.analytics.domain.port.WorkflowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/v1/workflows")
public class WorkflowController {

    private final CreateWorkflowUseCase createWorkflowUseCase;
    private final WorkflowRepository workflowRepository;

    public WorkflowController(CreateWorkflowUseCase createWorkflowUseCase,
                              WorkflowRepository workflowRepository) {
        this.createWorkflowUseCase = createWorkflowUseCase;
        this.workflowRepository = workflowRepository;
    }

    @PostMapping
    public Mono<ResponseEntity<WorkflowDto>> create(@RequestBody CreateWorkflowRequestDto body) {
        CreateWorkflowCommand cmd = new CreateWorkflowCommand(
                body.subProject(),
                body.name(),
                body.description(),
                body.ownerSubject(),
                Boolean.TRUE.equals(body.isCritical()),
                body.definition()
        );
        return createWorkflowUseCase.execute(cmd)
                .map(WorkflowDto::from)
                .map(dto -> ResponseEntity.status(HttpStatus.CREATED).body(dto));
    }

    @GetMapping
    public Mono<PageDto<WorkflowDto>> list(@RequestParam("sub_project") String subProject,
                                           @RequestParam(value = "page", defaultValue = "1") int page,
                                           @RequestParam(value = "size", defaultValue = "20") int size) {
        return workflowRepository.findAllBySubProject(subProject, page, size)
                .map(WorkflowDto::from)
                .collectList()
                .zipWith(workflowRepository.count(subProject))
                .map(t -> new PageDto<>(t.getT1(), page, size, t.getT2()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<WorkflowDetailDto>> getById(@PathVariable("id") UUID id) {
        return workflowRepository.findById(id)
                .flatMap(wf -> workflowRepository.findVersionsByWorkflow(id)
                        .collectList()
                        .map(versions -> WorkflowDetailDto.from(wf, versions)))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
