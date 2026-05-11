package bf.faso.analytics.application;

import bf.faso.analytics.application.RollbackWorkflowUseCase.AlreadyRolledBack;
import bf.faso.analytics.domain.model.Deployment;
import bf.faso.analytics.domain.model.WorkflowVersion;
import bf.faso.analytics.domain.port.DeploymentRepository;
import bf.faso.analytics.domain.port.DomainEventPublisher;
import bf.faso.analytics.domain.port.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.StopWatch;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RollbackWorkflowUseCaseTest {

    private DeploymentRepository deploymentRepository;
    private WorkflowRepository workflowRepository;
    private DomainEventPublisher eventPublisher;
    private ObjectMapper objectMapper;
    private RollbackWorkflowUseCase useCase;

    @BeforeEach
    void setUp() {
        deploymentRepository = mock(DeploymentRepository.class);
        workflowRepository = mock(WorkflowRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        objectMapper = new ObjectMapper();
        useCase = new RollbackWorkflowUseCase(deploymentRepository, workflowRepository, eventPublisher);

        when(eventPublisher.publish(anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    void test6_rollback_active_deployment_under_one_second_and_marks_status() {
        UUID workflowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        Instant now = Instant.now();

        Deployment active = new Deployment(
                deploymentId,
                versionId,
                Deployment.Strategy.DIRECT,
                now.minusSeconds(60),
                now.minusSeconds(30),
                null,
                null,
                null,
                "user:alice@faso",
                Deployment.Status.COMPLETED
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Mono.just(active));
        when(workflowRepository.findVersionsByWorkflow(versionId))
                .thenReturn(Flux.just(stubVersion(versionId, workflowId)));
        when(deploymentRepository.performRollback(
                any(UUID.class), any(UUID.class), any(UUID.class), anyString()))
                .thenReturn(Mono.empty());

        StopWatch sw = new StopWatch();
        sw.start();
        StepVerifier.create(useCase.execute(deploymentId, "KPI breach", "user:bob@faso"))
                .assertNext(d -> {
                    assertThat(d.status()).isEqualTo(Deployment.Status.ROLLED_BACK);
                    assertThat(d.rolledBackAt()).isNotNull();
                    assertThat(d.rollbackReason()).isEqualTo("KPI breach");
                })
                .verifyComplete();
        sw.stop();

        // Target ultraplan §17 : < 60 s real-world. In the unit test with mocked I/O the
        // expected duration is well under 1 s ; a generous 1 000 ms bound keeps CI green
        // while still catching accidental synchronous loops or sleeps in the use case path.
        assertThat(sw.getTotalTimeMillis()).isLessThan(1_000L);
    }

    @Test
    void test7_rollback_already_rolled_back_deployment_errors() {
        UUID deploymentId = UUID.randomUUID();
        Instant now = Instant.now();

        Deployment alreadyRolledBack = new Deployment(
                deploymentId,
                UUID.randomUUID(),
                Deployment.Strategy.DIRECT,
                now.minusSeconds(120),
                now.minusSeconds(100),
                now.minusSeconds(10),
                "earlier rollback",
                null,
                "user:alice@faso",
                Deployment.Status.ROLLED_BACK
        );

        when(deploymentRepository.findById(deploymentId)).thenReturn(Mono.just(alreadyRolledBack));

        StepVerifier.create(useCase.execute(deploymentId, "second attempt", "user:bob@faso"))
                .expectError(AlreadyRolledBack.class)
                .verify();
    }

    private WorkflowVersion stubVersion(UUID versionId, UUID workflowId) {
        return new WorkflowVersion(
                versionId,
                workflowId,
                1, 0, 0,
                null,
                "DEPLOYED",
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                new byte[]{0x01},
                null,
                Instant.now(),
                Instant.now(),
                null
        );
    }
}
