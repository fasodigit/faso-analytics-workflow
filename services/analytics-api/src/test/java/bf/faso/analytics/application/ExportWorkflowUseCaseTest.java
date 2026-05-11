package bf.faso.analytics.application;

import bf.faso.analytics.domain.port.ExportPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

class ExportWorkflowUseCaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ExportPort succeedingPdf;
    private ExportPort succeedingExcel;
    private ExportPort failingMetabase;

    @BeforeEach
    void setUp() {
        succeedingPdf = new ExportPort() {
            @Override public String kind() { return "pdf"; }
            @Override
            public Mono<ExportResult> render(ExportRequest req) {
                return Mono.just(new ExportResult(
                        UUID.randomUUID(), ExportResult.SUCCEEDED,
                        "file:///tmp/faso-analytics/exports/export-stub.pdf",
                        Instant.now(), Instant.now(), null));
            }
        };
        succeedingExcel = new ExportPort() {
            @Override public String kind() { return "excel"; }
            @Override
            public Mono<ExportResult> render(ExportRequest req) {
                return Mono.just(new ExportResult(
                        UUID.randomUUID(), ExportResult.SUCCEEDED,
                        "file:///tmp/faso-analytics/exports/export-stub.xlsx",
                        Instant.now(), Instant.now(), null));
            }
        };
        failingMetabase = new ExportPort() {
            @Override public String kind() { return "metabase"; }
            @Override
            public Mono<ExportResult> render(ExportRequest req) {
                return Mono.just(new ExportResult(
                        UUID.randomUUID(), ExportResult.FAILED, null,
                        Instant.now(), Instant.now(),
                        "metabase_not_configured: set METABASE_URL"));
            }
        };
    }

    private ExportWorkflowUseCase newUseCase(ExportPort... adapters) {
        // Mimic Spring's bean map keyed by bean name; the use case re-keys by kind().
        java.util.Map<String, ExportPort> beans = new java.util.HashMap<>();
        int i = 0;
        for (ExportPort p : adapters) {
            beans.put("adapter-" + (i++), p);
        }
        return new ExportWorkflowUseCase(beans, new SyncTaskExecutor());
    }

    @Test
    void execute_returns_QUEUED_then_async_render_marks_SUCCEEDED() throws Exception {
        ExportWorkflowUseCase useCase = newUseCase(succeedingPdf, succeedingExcel);
        JsonNode def = objectMapper.readTree("""
                { "metadata": { "name": "test-workflow", "subProject": "VOUCHERS", "semver": "1.0.0" } }
                """);

        ExportRequest req = new ExportRequest(
                UUID.randomUUID(), null, "pdf",
                Map.of(), "2025-Q4", List.of(), def);

        // With SyncTaskExecutor, the render runs inline → state should be SUCCEEDED immediately.
        ExportResult initial = useCase.execute(req).block();
        assertThat(initial).isNotNull();
        assertThat(initial.jobId()).isNotNull();
        // The returned object is the QUEUED snapshot taken before dispatch.
        assertThat(initial.status()).isEqualTo(ExportResult.QUEUED);

        // After the synchronous dispatch completes, getJob should reflect SUCCEEDED.
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            ExportResult polled = useCase.getJob(initial.jobId()).block();
            assertThat(polled).isNotNull();
            assertThat(polled.status()).isEqualTo(ExportResult.SUCCEEDED);
            assertThat(polled.resultUri()).startsWith("file://");
            assertThat(polled.finishedAt()).isNotNull();
        });
    }

    @Test
    void execute_rejects_unsupported_format_with_IllegalArgumentException() {
        ExportWorkflowUseCase useCase = newUseCase(succeedingPdf);
        ExportRequest req = new ExportRequest(
                UUID.randomUUID(), null, "rtf",
                Map.of(), null, List.of(), null);

        StepVerifier.create(useCase.execute(req))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(IllegalArgumentException.class);
                    assertThat(err.getMessage()).contains("unsupported export format").contains("rtf");
                })
                .verify();
    }

    @Test
    void execute_records_FAILED_status_when_adapter_returns_failure() {
        ExportWorkflowUseCase useCase = newUseCase(failingMetabase);
        ExportRequest req = new ExportRequest(
                UUID.randomUUID(), null, "metabase",
                Map.of(), null, List.of(), null);

        ExportResult initial = useCase.execute(req).block();
        assertThat(initial).isNotNull();
        UUID jobId = initial.jobId();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            ExportResult polled = useCase.getJob(jobId).block();
            assertThat(polled).isNotNull();
            assertThat(polled.status()).isEqualTo(ExportResult.FAILED);
            assertThat(polled.errorMessage()).contains("metabase_not_configured");
        });
    }

    @Test
    void execute_throws_when_no_adapter_registered_for_format() {
        // Only register PDF; metabase request should fail with IllegalStateException.
        ExportWorkflowUseCase useCase = newUseCase(succeedingPdf);
        ExportRequest req = new ExportRequest(
                UUID.randomUUID(), null, "metabase",
                Map.of(), null, List.of(), null);

        StepVerifier.create(useCase.execute(req))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(IllegalStateException.class);
                    assertThat(err.getMessage()).contains("no ExportPort adapter");
                })
                .verify();
    }

    @Test
    void getJob_returns_empty_for_unknown_jobId() {
        ExportWorkflowUseCase useCase = newUseCase(succeedingPdf);
        UUID unknown = UUID.randomUUID();
        StepVerifier.create(useCase.getJob(unknown)).verifyComplete();
    }

    @Test
    void execute_then_getJob_round_trips_QUEUED_state() {
        ExportWorkflowUseCase useCase = new ExportWorkflowUseCase(
                Map.of("PdfStub", new ExportPort() {
                    @Override public String kind() { return "pdf"; }
                    @Override
                    public Mono<ExportResult> render(ExportRequest req) {
                        // Never completes — simulates a long-running render so we can observe QUEUED.
                        return Mono.never();
                    }
                }),
                runnable -> { /* no-op executor — render never dispatched, stays QUEUED */ });

        ExportRequest req = new ExportRequest(
                UUID.randomUUID(), null, "pdf",
                Map.of(), null, List.of(), null);
        ExportResult initial = useCase.execute(req).block();
        assertThat(initial).isNotNull();

        ExportResult polled = useCase.getJob(initial.jobId()).block();
        if (polled == null) {
            fail("expected job to be tracked after execute()");
        }
        assertThat(polled.status()).isEqualTo(ExportResult.QUEUED);
        assertThat(polled.resultUri()).isNull();
        assertThat(polled.requestedAt()).isNotNull();
    }
}
