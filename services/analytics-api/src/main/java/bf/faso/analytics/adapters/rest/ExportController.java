package bf.faso.analytics.adapters.rest;

import bf.faso.analytics.adapters.rest.dto.CreateExportRequestDto;
import bf.faso.analytics.adapters.rest.dto.ExportResultDto;
import bf.faso.analytics.application.ExportRequest;
import bf.faso.analytics.application.ExportWorkflowUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST adapter for the export API.
 *
 * <pre>
 * POST /v1/exports          → enqueue an export job → 202 {job_id, status: QUEUED}
 * GET  /v1/exports/{jobId}  → poll job state        → 200 {job_id, status, result_uri?, ...}
 * </pre>
 *
 * Asynchrony : {@code POST} returns immediately ; the actual render runs on the
 * {@code exportTaskExecutor} pool inside {@link ExportWorkflowUseCase}.
 */
@RestController
@RequestMapping("/v1/exports")
public class ExportController {

    private final ExportWorkflowUseCase useCase;

    public ExportController(ExportWorkflowUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public Mono<ResponseEntity<ExportResultDto>> create(@RequestBody CreateExportRequestDto body) {
        ExportRequest req = new ExportRequest(
                body.workflowId(),
                body.versionId(),
                body.format(),
                body.filters() != null ? body.filters() : Map.of(),
                body.period(),
                body.deliverTo() != null ? body.deliverTo() : List.of(),
                body.definition()
        );
        return useCase.execute(req)
                .map(ExportResultDto::from)
                .map(dto -> ResponseEntity.accepted().body(dto))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().body(error(e.getMessage()))));
    }

    @GetMapping("/{jobId}")
    public Mono<ResponseEntity<ExportResultDto>> get(@PathVariable("jobId") UUID jobId) {
        return useCase.getJob(jobId)
                .map(ExportResultDto::from)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private static ExportResultDto error(String message) {
        return new ExportResultDto(null, "REJECTED", null, null, null, message);
    }
}
