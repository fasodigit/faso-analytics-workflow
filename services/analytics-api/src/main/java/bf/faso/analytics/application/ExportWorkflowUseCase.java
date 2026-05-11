package bf.faso.analytics.application;

import bf.faso.analytics.domain.port.ExportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cas d'usage applicatif : déclencher un job d'export asynchrone.
 *
 * <ol>
 *   <li>Valider que {@code format ∈ {pdf, excel, pptx, metabase}}.</li>
 *   <li>Trouver l'adaptateur correspondant via le {@code Map<String, ExportPort>} (clé = {@link ExportPort#kind()}).</li>
 *   <li>Générer un {@code jobId} (UUID v4) et stocker l'état initial {@code QUEUED}.</li>
 *   <li>Dispatcher le rendu effectif sur le {@link TaskExecutor} ({@code RUNNING → SUCCEEDED|FAILED}).</li>
 *   <li>Retourner immédiatement le {@code ExportResult} avec statut {@code QUEUED}.</li>
 * </ol>
 *
 * Phase 3 : stockage in-memory {@code ConcurrentHashMap}.
 * Phase 4 : remplacé par persistance YugabyteDB + dispatch via Redpanda.
 */
@Service
public class ExportWorkflowUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ExportWorkflowUseCase.class);

    private final Map<String, ExportPort> adaptersByKind;
    private final TaskExecutor taskExecutor;
    private final ConcurrentHashMap<UUID, ExportResult> jobs = new ConcurrentHashMap<>();

    @Autowired
    public ExportWorkflowUseCase(Map<String, ExportPort> adaptersByKind,
                                 @Qualifier("exportTaskExecutor") TaskExecutor taskExecutor) {
        // Spring injects all ExportPort @Component beans keyed by bean name. We re-key by kind().
        Map<String, ExportPort> byKind = new HashMap<>();
        for (ExportPort port : adaptersByKind.values()) {
            byKind.put(port.kind(), port);
        }
        this.adaptersByKind = Map.copyOf(byKind);
        this.taskExecutor = taskExecutor;
    }

    public Mono<ExportResult> execute(ExportRequest req) {
        return Mono.fromCallable(() -> {
            if (req.format() == null || !ExportRequest.SUPPORTED_FORMATS.contains(req.format())) {
                throw new IllegalArgumentException(
                        "unsupported export format: " + req.format()
                                + " (allowed: " + ExportRequest.SUPPORTED_FORMATS + ")");
            }
            ExportPort port = adaptersByKind.get(req.format());
            if (port == null) {
                throw new IllegalStateException(
                        "no ExportPort adapter registered for kind=" + req.format());
            }

            UUID jobId = UUID.randomUUID();
            Instant requestedAt = Instant.now();
            ExportResult queued = ExportResult.queued(jobId, requestedAt);
            jobs.put(jobId, queued);

            taskExecutor.execute(() -> runRender(jobId, port, req));

            return queued;
        });
    }

    private void runRender(UUID jobId, ExportPort port, ExportRequest req) {
        try {
            ExportResult current = jobs.get(jobId);
            if (current != null) {
                jobs.put(jobId, current.withStatus(ExportResult.RUNNING));
            }
            LOG.info("export.render.start jobId={} kind={} workflowId={}",
                    jobId, port.kind(), req.workflowId());

            // The adapter is reactive but we block here intentionally — we're already on
            // a dedicated TaskExecutor thread so this is safe and keeps the API simple.
            ExportResult rendered = port.render(req).block();

            Instant finishedAt = Instant.now();
            if (rendered == null) {
                jobs.put(jobId, jobs.get(jobId).failed("adapter returned null", finishedAt));
                return;
            }
            // Adapter may have already set status; we honor SUCCEEDED/FAILED, otherwise mark SUCCEEDED.
            String finalStatus = rendered.status() != null ? rendered.status() : ExportResult.SUCCEEDED;
            ExportResult finalResult = new ExportResult(
                    jobId,
                    finalStatus,
                    rendered.resultUri(),
                    jobs.get(jobId).requestedAt(),
                    finishedAt,
                    rendered.errorMessage()
            );
            jobs.put(jobId, finalResult);
            LOG.info("export.render.done jobId={} status={} resultUri={}",
                    jobId, finalStatus, rendered.resultUri());
        } catch (Throwable t) {
            LOG.error("export.render.error jobId={} kind={}", jobId, port.kind(), t);
            Instant finishedAt = Instant.now();
            ExportResult prev = jobs.get(jobId);
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            if (prev != null) {
                jobs.put(jobId, prev.failed(msg, finishedAt));
            }
        }
    }

    public Mono<ExportResult> getJob(UUID jobId) {
        ExportResult result = jobs.get(jobId);
        return result == null ? Mono.empty() : Mono.just(result);
    }

    /**
     * Configuration interne du {@link TaskExecutor} utilisé pour dispatcher les exports.
     *
     * <p>Pool dédié (4 threads core, 16 max, queue 100) isolé du reactor-netty event loop
     * pour ne pas bloquer les requêtes HTTP entrantes.
     */
    @Configuration
    public static class ExportTaskExecutorConfig {

        @Bean(name = "exportTaskExecutor")
        public TaskExecutor exportTaskExecutor() {
            ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
            exec.setCorePoolSize(4);
            exec.setMaxPoolSize(16);
            exec.setQueueCapacity(100);
            exec.setThreadNamePrefix("faso-export-");
            exec.initialize();
            return exec;
        }
    }
}
