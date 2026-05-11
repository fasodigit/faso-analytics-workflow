package bf.faso.analytics.application;

import bf.faso.analytics.domain.model.Workflow;
import bf.faso.analytics.domain.model.WorkflowVersion;
import bf.faso.analytics.domain.port.AuditChainPort;
import bf.faso.analytics.domain.port.AuthorizationPort;
import bf.faso.analytics.domain.port.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateWorkflowUseCaseTest {

    private WorkflowRepository repository;
    private AuditChainPort auditChainPort;
    private AuthorizationPort authorizationPort;
    private ObjectMapper objectMapper;
    private CreateWorkflowUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(WorkflowRepository.class);
        auditChainPort = mock(AuditChainPort.class);
        authorizationPort = mock(AuthorizationPort.class);
        objectMapper = new ObjectMapper();
        useCase = new CreateWorkflowUseCase(repository, auditChainPort, authorizationPort, objectMapper);

        when(repository.save(any(Workflow.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, Workflow.class)));
        when(repository.saveVersion(any(WorkflowVersion.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, WorkflowVersion.class)));
    }

    @Test
    void execute_persists_workflow_and_initial_draft_version() throws Exception {
        JsonNode def = objectMapper.readTree("""
                { "apiVersion": "analytics.faso/v1", "kind": "AnalyticsWorkflow" }
                """);
        CreateWorkflowCommand cmd = new CreateWorkflowCommand(
                "VOUCHERS",
                "demo-create-workflow",
                "Test",
                "user:fasodigitalisation@gmail.com",
                false,
                def);

        StepVerifier.create(useCase.execute(cmd))
                .assertNext(wf -> {
                    assertThat(wf.subProject()).isEqualTo("VOUCHERS");
                    assertThat(wf.name()).isEqualTo("demo-create-workflow");
                    assertThat(wf.workflowId()).isNotNull();
                })
                .verifyComplete();

        ArgumentCaptor<Workflow> wfCaptor = ArgumentCaptor.forClass(Workflow.class);
        ArgumentCaptor<WorkflowVersion> versionCaptor = ArgumentCaptor.forClass(WorkflowVersion.class);
        verify(repository, times(1)).save(wfCaptor.capture());
        verify(repository, times(1)).saveVersion(versionCaptor.capture());

        WorkflowVersion saved = versionCaptor.getValue();
        assertThat(saved.semverMajor()).isEqualTo(1);
        assertThat(saved.semverMinor()).isEqualTo(0);
        assertThat(saved.semverPatch()).isEqualTo(0);
        assertThat(saved.semverPreRelease()).isEqualTo("draft.1");
        assertThat(saved.status()).isEqualTo("DRAFT");
        assertThat(saved.workflowId()).isEqualTo(wfCaptor.getValue().workflowId());
        assertThat(saved.blake3Self()).isNotNull();
        assertThat(saved.blake3Self().length).isEqualTo(32);
        assertThat(saved.blake3Parent()).isNull();
    }
}
