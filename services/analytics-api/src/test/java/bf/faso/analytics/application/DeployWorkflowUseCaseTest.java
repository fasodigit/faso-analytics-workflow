package bf.faso.analytics.application;

import bf.faso.analytics.application.DeployWorkflowUseCase.DeployCommand;
import bf.faso.analytics.application.DeployWorkflowUseCase.DeploymentRefused;
import bf.faso.analytics.domain.model.Deployment;
import bf.faso.analytics.domain.model.Workflow;
import bf.faso.analytics.domain.port.DeploymentRepository;
import bf.faso.analytics.domain.port.DomainEventPublisher;
import bf.faso.analytics.domain.port.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeployWorkflowUseCaseTest {

    private WorkflowRepository workflowRepository;
    private DeploymentRepository deploymentRepository;
    private DomainEventPublisher eventPublisher;
    private DeployWorkflowUseCase useCase;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        deploymentRepository = mock(DeploymentRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        useCase = new DeployWorkflowUseCase(workflowRepository, deploymentRepository, eventPublisher);

        when(eventPublisher.publish(anyString(), any())).thenReturn(Mono.empty());
        when(deploymentRepository.save(any(Deployment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, Deployment.class)));
        when(deploymentRepository.markVersionDeployed(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    void test1_non_critical_with_one_approval_and_recent_simulation_direct_completes() {
        UUID workflowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Workflow wf = stubWorkflow(workflowId, false);

        when(workflowRepository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(deploymentRepository.countApprovals(versionId)).thenReturn(Mono.just(1));
        when(deploymentRepository.latestSimulation(versionId))
                .thenReturn(Mono.just(Instant.now().minus(Duration.ofHours(1))));

        DeployCommand cmd = new DeployCommand(
                workflowId, versionId, Deployment.Strategy.DIRECT, null, "user:alice@faso");

        StepVerifier.create(useCase.execute(cmd))
                .assertNext(d -> {
                    assertThat(d.status()).isEqualTo(Deployment.Status.COMPLETED);
                    assertThat(d.strategy()).isEqualTo(Deployment.Strategy.DIRECT);
                    assertThat(d.versionId()).isEqualTo(versionId);
                    assertThat(d.completedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void test2_critical_with_only_one_approval_is_refused_four_eyes() {
        UUID workflowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Workflow wf = stubWorkflow(workflowId, true);

        when(workflowRepository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(deploymentRepository.countApprovals(versionId)).thenReturn(Mono.just(1));
        when(deploymentRepository.latestSimulation(versionId))
                .thenReturn(Mono.just(Instant.now().minus(Duration.ofHours(1))));

        DeployCommand cmd = new DeployCommand(
                workflowId, versionId, Deployment.Strategy.SHADOW, 24, "user:alice@faso");

        StepVerifier.create(useCase.execute(cmd))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(DeploymentRefused.class);
                    DeploymentRefused refused = (DeploymentRefused) err;
                    assertThat(refused.reasons()).anyMatch(r -> r.contains("Approvals: 1/2"));
                })
                .verify();
    }

    @Test
    void test3_critical_with_two_approvals_but_direct_strategy_refused_shadow_required() {
        UUID workflowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Workflow wf = stubWorkflow(workflowId, true);

        when(workflowRepository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(deploymentRepository.countApprovals(versionId)).thenReturn(Mono.just(2));
        when(deploymentRepository.latestSimulation(versionId))
                .thenReturn(Mono.just(Instant.now().minus(Duration.ofHours(1))));

        DeployCommand cmd = new DeployCommand(
                workflowId, versionId, Deployment.Strategy.DIRECT, null, "user:alice@faso");

        StepVerifier.create(useCase.execute(cmd))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(DeploymentRefused.class);
                    DeploymentRefused refused = (DeploymentRefused) err;
                    assertThat(refused.reasons())
                            .anyMatch(r -> r.contains("Critical workflow requires SHADOW"));
                })
                .verify();
    }

    @Test
    void test4_critical_with_two_approvals_and_shadow_completes_with_shadow_horizon() {
        UUID workflowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Workflow wf = stubWorkflow(workflowId, true);

        when(workflowRepository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(deploymentRepository.countApprovals(versionId)).thenReturn(Mono.just(2));
        when(deploymentRepository.latestSimulation(versionId))
                .thenReturn(Mono.just(Instant.now().minus(Duration.ofHours(2))));

        DeployCommand cmd = new DeployCommand(
                workflowId, versionId, Deployment.Strategy.SHADOW, 24, "user:alice@faso");

        StepVerifier.create(useCase.execute(cmd))
                .assertNext(d -> {
                    assertThat(d.status()).isEqualTo(Deployment.Status.COMPLETED);
                    assertThat(d.strategy()).isEqualTo(Deployment.Strategy.SHADOW);
                    assertThat(d.shadowEndsAt()).isNotNull();
                    long hoursAhead = Duration.between(d.startedAt(), d.shadowEndsAt()).toHours();
                    assertThat(hoursAhead).isEqualTo(24);
                })
                .verifyComplete();
    }

    @Test
    void test5_simulation_older_than_seven_days_is_refused() {
        UUID workflowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Workflow wf = stubWorkflow(workflowId, false);

        when(workflowRepository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(deploymentRepository.countApprovals(versionId)).thenReturn(Mono.just(1));
        when(deploymentRepository.latestSimulation(versionId))
                .thenReturn(Mono.just(Instant.now().minus(Duration.ofDays(10))));

        DeployCommand cmd = new DeployCommand(
                workflowId, versionId, Deployment.Strategy.DIRECT, null, "user:alice@faso");

        StepVerifier.create(useCase.execute(cmd))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(DeploymentRefused.class);
                    DeploymentRefused refused = (DeploymentRefused) err;
                    assertThat(refused.reasons())
                            .anyMatch(r -> r.contains("Simulation older than 7 days"));
                })
                .verify();
    }

    @Test
    void test_no_simulation_is_refused() {
        UUID workflowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Workflow wf = stubWorkflow(workflowId, false);

        when(workflowRepository.findById(workflowId)).thenReturn(Mono.just(wf));
        when(deploymentRepository.countApprovals(versionId)).thenReturn(Mono.just(1));
        when(deploymentRepository.latestSimulation(versionId)).thenReturn(Mono.empty());

        DeployCommand cmd = new DeployCommand(
                workflowId, versionId, Deployment.Strategy.DIRECT, null, "user:alice@faso");

        StepVerifier.create(useCase.execute(cmd))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(DeploymentRefused.class);
                    DeploymentRefused refused = (DeploymentRefused) err;
                    assertThat(refused.reasons())
                            .anyMatch(r -> r.contains("No simulation found"));
                })
                .verify();
    }

    private Workflow stubWorkflow(UUID id, boolean isCritical) {
        Instant now = Instant.now();
        return new Workflow(
                id,
                "VOUCHERS",
                "demo-deploy-flow",
                "test workflow",
                "user:fasodigitalisation@gmail.com",
                null,
                isCritical,
                now,
                now);
    }
}
