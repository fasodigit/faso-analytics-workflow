package bf.faso.analytics.infrastructure.persistence.yb;

import bf.faso.analytics.domain.model.Deployment;
import bf.faso.analytics.domain.port.DeploymentRepository;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public class R2dbcDeploymentRepository implements DeploymentRepository {

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public R2dbcDeploymentRepository(DatabaseClient databaseClient,
                                     ReactiveTransactionManager transactionManager) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    @Override
    public Mono<Deployment> save(Deployment d) {
        String sql = """
                INSERT INTO workflow.deployments
                  (deployment_id, version_id, strategy, started_at, completed_at,
                   rolled_back_at, rollback_reason, shadow_ends_at, actor_subject)
                VALUES
                  (:deployment_id, :version_id, :strategy, :started_at, :completed_at,
                   :rolled_back_at, :rollback_reason, :shadow_ends_at, :actor_subject)
                """;
        return databaseClient.sql(sql)
                .bind("deployment_id", d.deploymentId())
                .bind("version_id", d.versionId())
                .bind("strategy", d.strategy().name())
                .bind("started_at", d.startedAt())
                .bind("completed_at", d.completedAt() == null
                        ? Parameters.in(R2dbcType.TIMESTAMP_WITH_TIME_ZONE) : Parameters.in(d.completedAt()))
                .bind("rolled_back_at", d.rolledBackAt() == null
                        ? Parameters.in(R2dbcType.TIMESTAMP_WITH_TIME_ZONE) : Parameters.in(d.rolledBackAt()))
                .bind("rollback_reason", d.rollbackReason() == null
                        ? Parameters.in(R2dbcType.VARCHAR) : Parameters.in(d.rollbackReason()))
                .bind("shadow_ends_at", d.shadowEndsAt() == null
                        ? Parameters.in(R2dbcType.TIMESTAMP_WITH_TIME_ZONE) : Parameters.in(d.shadowEndsAt()))
                .bind("actor_subject", d.actorSubject())
                .fetch()
                .rowsUpdated()
                .thenReturn(d);
    }

    @Override
    public Mono<Deployment> findById(UUID deploymentId) {
        String sql = """
                SELECT deployment_id, version_id, strategy, started_at, completed_at,
                       rolled_back_at, rollback_reason, shadow_ends_at, actor_subject
                FROM workflow.deployments
                WHERE deployment_id = :id
                """;
        return databaseClient.sql(sql)
                .bind("id", deploymentId)
                .map(this::mapDeployment)
                .one();
    }

    @Override
    public Flux<Deployment> findByVersionId(UUID versionId) {
        String sql = """
                SELECT deployment_id, version_id, strategy, started_at, completed_at,
                       rolled_back_at, rollback_reason, shadow_ends_at, actor_subject
                FROM workflow.deployments
                WHERE version_id = :vid
                ORDER BY started_at DESC
                """;
        return databaseClient.sql(sql)
                .bind("vid", versionId)
                .map(this::mapDeployment)
                .all();
    }

    @Override
    public Flux<Deployment> findByWorkflowId(UUID workflowId) {
        String sql = """
                SELECT d.deployment_id, d.version_id, d.strategy, d.started_at, d.completed_at,
                       d.rolled_back_at, d.rollback_reason, d.shadow_ends_at, d.actor_subject
                FROM workflow.deployments d
                JOIN workflow.workflow_versions v ON v.version_id = d.version_id
                WHERE v.workflow_id = :wfid
                ORDER BY d.started_at DESC
                """;
        return databaseClient.sql(sql)
                .bind("wfid", workflowId)
                .map(this::mapDeployment)
                .all();
    }

    @Override
    public Mono<Deployment> findActiveByWorkflow(UUID workflowId) {
        String sql = """
                SELECT d.deployment_id, d.version_id, d.strategy, d.started_at, d.completed_at,
                       d.rolled_back_at, d.rollback_reason, d.shadow_ends_at, d.actor_subject
                FROM workflow.deployments d
                JOIN workflow.workflow_versions v ON v.version_id = d.version_id
                WHERE v.workflow_id = :wfid
                  AND d.rolled_back_at IS NULL
                  AND d.completed_at IS NOT NULL
                ORDER BY d.completed_at DESC
                LIMIT 1
                """;
        return databaseClient.sql(sql)
                .bind("wfid", workflowId)
                .map(this::mapDeployment)
                .one();
    }

    @Override
    public Mono<Integer> countApprovals(UUID versionId) {
        String sql = """
                SELECT COUNT(DISTINCT approver_subject)::int AS c
                FROM workflow.approvals
                WHERE version_id = :vid
                """;
        return databaseClient.sql(sql)
                .bind("vid", versionId)
                .map(row -> {
                    Integer c = row.get("c", Integer.class);
                    return c == null ? 0 : c;
                })
                .one()
                .defaultIfEmpty(0);
    }

    @Override
    public Mono<Instant> latestSimulation(UUID versionId) {
        String sql = """
                SELECT MAX(finished_at) AS last_sim
                FROM workflow.simulations
                WHERE version_id = :vid AND status = 'SUCCEEDED'
                """;
        return databaseClient.sql(sql)
                .bind("vid", versionId)
                .map(row -> row.get("last_sim", Instant.class))
                .one();
    }

    @Override
    public Mono<Void> markVersionDeployed(UUID versionId, UUID workflowId) {
        // DIRECT/BLUE_GREEN strategies : deprecate previously DEPLOYED versions of the same
        // workflow, then promote the target version. Ordering matters to maintain the invariant
        // that v_active_versions returns at most one row per workflow.
        Mono<Long> deprecatePrev = databaseClient.sql("""
                UPDATE workflow.workflow_versions
                   SET status = 'DEPRECATED', deprecated_at = now()
                 WHERE workflow_id = :wfid AND status = 'DEPLOYED' AND version_id <> :vid
                """)
                .bind("wfid", workflowId)
                .bind("vid", versionId)
                .fetch()
                .rowsUpdated();
        Mono<Long> promote = databaseClient.sql("""
                UPDATE workflow.workflow_versions
                   SET status = 'DEPLOYED', deployed_at = now()
                 WHERE version_id = :vid
                """)
                .bind("vid", versionId)
                .fetch()
                .rowsUpdated();
        return deprecatePrev.then(promote).then().as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> performRollback(UUID deploymentId,
                                      UUID versionId,
                                      UUID workflowId,
                                      String reason) {
        // Atomic rollback transaction (target < 60 s wall-clock — ultraplan §17).
        // 1. mark deployment as rolled back
        // 2. demote the failed version to DEPRECATED
        // 3. restore the most recent DEPRECATED version (deployed BEFORE failing one) to DEPLOYED
        // All inside ONE R2DBC transaction so partial state is impossible.
        Mono<Long> markDeploy = databaseClient.sql("""
                UPDATE workflow.deployments
                   SET rolled_back_at = now(), rollback_reason = :reason
                 WHERE deployment_id = :id AND rolled_back_at IS NULL
                """)
                .bind("id", deploymentId)
                .bind("reason", reason == null ? "" : reason)
                .fetch()
                .rowsUpdated();
        Mono<Long> demote = databaseClient.sql("""
                UPDATE workflow.workflow_versions
                   SET status = 'DEPRECATED', deprecated_at = now()
                 WHERE version_id = :vid
                """)
                .bind("vid", versionId)
                .fetch()
                .rowsUpdated();
        Mono<Long> restorePrev = databaseClient.sql("""
                UPDATE workflow.workflow_versions
                   SET status = 'DEPLOYED', deprecated_at = NULL
                 WHERE version_id = (
                    SELECT version_id FROM workflow.workflow_versions
                     WHERE workflow_id = :wfid
                       AND status = 'DEPRECATED'
                       AND deployed_at IS NOT NULL
                       AND version_id <> :vid
                     ORDER BY deployed_at DESC
                     LIMIT 1
                 )
                """)
                .bind("wfid", workflowId)
                .bind("vid", versionId)
                .fetch()
                .rowsUpdated();
        return markDeploy.then(demote).then(restorePrev).then().as(transactionalOperator::transactional);
    }

    private Deployment mapDeployment(Readable row) {
        Instant completedAt = row.get("completed_at", Instant.class);
        Instant rolledBackAt = row.get("rolled_back_at", Instant.class);
        Deployment.Status status;
        if (rolledBackAt != null) {
            status = Deployment.Status.ROLLED_BACK;
        } else if (completedAt != null) {
            status = Deployment.Status.COMPLETED;
        } else {
            status = Deployment.Status.IN_PROGRESS;
        }
        return new Deployment(
                row.get("deployment_id", UUID.class),
                row.get("version_id", UUID.class),
                Deployment.Strategy.valueOf(row.get("strategy", String.class)),
                row.get("started_at", Instant.class),
                completedAt,
                rolledBackAt,
                row.get("rollback_reason", String.class),
                row.get("shadow_ends_at", Instant.class),
                row.get("actor_subject", String.class),
                status
        );
    }
}
