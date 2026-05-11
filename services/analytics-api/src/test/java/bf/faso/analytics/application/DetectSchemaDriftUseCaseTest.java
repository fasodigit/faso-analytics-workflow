package bf.faso.analytics.application;

import bf.faso.analytics.domain.model.Workflow;
import bf.faso.analytics.domain.model.WorkflowVersion;
import bf.faso.analytics.domain.port.DomainEventPublisher;
import bf.faso.analytics.domain.port.SourceSchemaPort;
import bf.faso.analytics.domain.port.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectSchemaDriftUseCaseTest {

    private WorkflowRepository repository;
    private SourceSchemaPort sourceSchemaPort;
    private DomainEventPublisher eventPublisher;
    private ObjectMapper objectMapper;
    private DetectSchemaDriftUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(WorkflowRepository.class);
        sourceSchemaPort = mock(SourceSchemaPort.class);
        eventPublisher = mock(DomainEventPublisher.class);
        objectMapper = new ObjectMapper();
        useCase = new DetectSchemaDriftUseCase(repository, sourceSchemaPort, eventPublisher);

        when(eventPublisher.publish(anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    void reports_drift_and_blocks_when_defaults_and_removed_field() throws Exception {
        UUID workflowId = UUID.randomUUID();
        JsonNode snapshot = objectMapper.readTree("""
                { "a": {"type":"int"}, "b": {"type":"string"} }
                """);
        JsonNode current = objectMapper.readTree("""
                { "a": {"type":"int"}, "c": {"type":"string"} }
                """);
        JsonNode definition = objectMapper.readTree("""
                {
                  "apiVersion": "analytics.faso/v1",
                  "kind": "AnalyticsWorkflow",
                  "spec": {
                    "source": {
                      "type": "yugabyte",
                      "connectionRef": "secret/data/analytics/connectors/VOUCHERS/test",
                      "schema": "vouch",
                      "table": "rows"
                    }
                  }
                }
                """);

        Workflow wf = stubWorkflow(workflowId);
        WorkflowVersion version = stubVersion(workflowId, "DRAFT", definition, snapshot);

        when(repository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(repository.findVersionsByWorkflow(workflowId)).thenReturn(Flux.just(version));
        when(sourceSchemaPort.getCurrentSchema(eq("secret/data/analytics/connectors/VOUCHERS/test"), eq("vouch.rows")))
                .thenReturn(Mono.just(current));

        StepVerifier.create(useCase.execute(workflowId))
                .assertNext(report -> {
                    assertThat(report.workflowId()).isEqualTo(workflowId);
                    assertThat(report.versionId()).isEqualTo(version.versionId());
                    assertThat(report.drift().removedFields()).containsExactly("b");
                    assertThat(report.drift().newFields()).containsExactly("c");
                    assertThat(report.decision().blocked()).isTrue();
                    assertThat(report.error()).isNull();
                })
                .verifyComplete();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, times(1)).publish(topic.capture(), any());
        assertThat(topic.getValue()).contains("SCHEMA_DRIFT_DETECTED");
    }

    @Test
    void returns_introspection_unavailable_when_source_port_fails() throws Exception {
        UUID workflowId = UUID.randomUUID();
        JsonNode snapshot = objectMapper.readTree("""
                { "a": {"type":"int"} }
                """);
        JsonNode definition = objectMapper.readTree("""
                {
                  "spec": {
                    "source": {
                      "type": "yugabyte",
                      "connectionRef": "vault/x",
                      "schema": "s", "table": "t"
                    }
                  }
                }
                """);

        Workflow wf = stubWorkflow(workflowId);
        WorkflowVersion version = stubVersion(workflowId, "DRAFT", definition, snapshot);

        when(repository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(repository.findVersionsByWorkflow(workflowId)).thenReturn(Flux.just(version));
        when(sourceSchemaPort.getCurrentSchema(anyString(), anyString()))
                .thenReturn(Mono.error(new UnsupportedOperationException("not wired")));

        StepVerifier.create(useCase.execute(workflowId))
                .assertNext(report -> {
                    assertThat(report.error()).isEqualTo("introspection_unavailable");
                    assertThat(report.drift().isEmpty()).isTrue();
                    assertThat(report.decision().blocked()).isFalse();
                })
                .verifyComplete();

        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    void empty_drift_does_not_emit_event() throws Exception {
        UUID workflowId = UUID.randomUUID();
        JsonNode snapshot = objectMapper.readTree("""
                { "a": {"type":"int"} }
                """);
        JsonNode current = objectMapper.readTree("""
                { "a": {"type":"int"} }
                """);
        JsonNode definition = objectMapper.readTree("""
                {
                  "spec": {
                    "source": {
                      "type": "yugabyte",
                      "connectionRef": "vault/x",
                      "schema": "s", "table": "t"
                    }
                  }
                }
                """);

        Workflow wf = stubWorkflow(workflowId);
        WorkflowVersion version = stubVersion(workflowId, "DRAFT", definition, snapshot);

        when(repository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(repository.findVersionsByWorkflow(workflowId)).thenReturn(Flux.just(version));
        when(sourceSchemaPort.getCurrentSchema(anyString(), anyString())).thenReturn(Mono.just(current));

        StepVerifier.create(useCase.execute(workflowId))
                .assertNext(report -> {
                    assertThat(report.drift().isEmpty()).isTrue();
                    assertThat(report.decision().blocked()).isFalse();
                })
                .verifyComplete();

        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    void returns_not_found_when_workflow_missing() {
        UUID workflowId = UUID.randomUUID();
        when(repository.findById(workflowId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(workflowId))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(DetectSchemaDriftUseCase.WorkflowNotFoundException.class);
                    assertThat(((DetectSchemaDriftUseCase.WorkflowNotFoundException) err).workflowId())
                            .isEqualTo(workflowId);
                })
                .verify();
    }

    private Workflow stubWorkflow(UUID workflowId) {
        Instant now = Instant.now();
        return new Workflow(
                workflowId,
                "VOUCHERS",
                "demo-drift-workflow",
                "test",
                "user:fasodigitalisation@gmail.com",
                null,
                false,
                now,
                now);
    }

    private WorkflowVersion stubVersion(UUID workflowId,
                                        String status,
                                        JsonNode definition,
                                        JsonNode snapshot) {
        return new WorkflowVersion(
                UUID.randomUUID(),
                workflowId,
                1, 0, 0,
                "draft.1",
                status,
                definition,
                snapshot,
                new byte[]{0x01},
                null,
                Instant.now(),
                null,
                null
        );
    }
}
