package bf.faso.analytics.infrastructure.persistence.yb;

import bf.faso.analytics.domain.model.Workflow;
import bf.faso.analytics.domain.model.WorkflowVersion;
import bf.faso.analytics.domain.port.WorkflowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public class R2dbcWorkflowRepository implements WorkflowRepository {

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public R2dbcWorkflowRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Workflow> save(Workflow wf) {
        String sql = """
                INSERT INTO workflow.workflows
                  (workflow_id, sub_project, name, description, owner_subject,
                   parent_workflow_id, is_critical, created_at, updated_at)
                VALUES
                  (:workflow_id, :sub_project, :name, :description, :owner_subject,
                   :parent_workflow_id, :is_critical, :created_at, :updated_at)
                """;
        return databaseClient.sql(sql)
                .bind("workflow_id", wf.workflowId())
                .bind("sub_project", wf.subProject())
                .bind("name", wf.name())
                .bind("description", wf.description() == null ? Parameters.in(R2dbcType.VARCHAR) : Parameters.in(wf.description()))
                .bind("owner_subject", wf.ownerSubject())
                .bind("parent_workflow_id", wf.parentWorkflowId() == null
                        ? Parameters.in(UUID.class) : Parameters.in(wf.parentWorkflowId()))
                .bind("is_critical", wf.isCritical())
                .bind("created_at", wf.createdAt())
                .bind("updated_at", wf.updatedAt())
                .fetch()
                .rowsUpdated()
                .thenReturn(wf);
    }

    @Override
    public Mono<Workflow> findById(UUID workflowId) {
        String sql = """
                SELECT workflow_id, sub_project, name, description, owner_subject,
                       parent_workflow_id, is_critical, created_at, updated_at
                FROM workflow.workflows
                WHERE workflow_id = :workflow_id AND archived_at IS NULL
                """;
        return databaseClient.sql(sql)
                .bind("workflow_id", workflowId)
                .map(this::mapWorkflow)
                .one();
    }

    @Override
    public Flux<Workflow> findAllBySubProject(String subProject, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String sql = """
                SELECT workflow_id, sub_project, name, description, owner_subject,
                       parent_workflow_id, is_critical, created_at, updated_at
                FROM workflow.workflows
                WHERE sub_project = :sub_project AND archived_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """;
        return databaseClient.sql(sql)
                .bind("sub_project", subProject)
                .bind("limit", safeSize)
                .bind("offset", offset)
                .map(this::mapWorkflow)
                .all();
    }

    @Override
    public Mono<Long> count(String subProject) {
        String sql = """
                SELECT COUNT(*) AS c FROM workflow.workflows
                WHERE sub_project = :sub_project AND archived_at IS NULL
                """;
        return databaseClient.sql(sql)
                .bind("sub_project", subProject)
                .map(row -> row.get("c", Long.class))
                .one();
    }

    @Override
    public Mono<WorkflowVersion> saveVersion(WorkflowVersion v) {
        String sql = """
                INSERT INTO workflow.workflow_versions
                  (version_id, workflow_id, semver_major, semver_minor, semver_patch,
                   semver_pre_release, status, definition_jsonb, schema_snapshot,
                   blake3_self, blake3_parent, created_at, deployed_at, deprecated_at)
                VALUES
                  (:version_id, :workflow_id, :semver_major, :semver_minor, :semver_patch,
                   :semver_pre_release, :status, :definition_jsonb::jsonb, :schema_snapshot::jsonb,
                   :blake3_self, :blake3_parent, :created_at, :deployed_at, :deprecated_at)
                """;
        String defStr = toJsonString(v.definitionJsonb());
        String snapStr = toJsonString(v.schemaSnapshot());
        return databaseClient.sql(sql)
                .bind("version_id", v.versionId())
                .bind("workflow_id", v.workflowId())
                .bind("semver_major", v.semverMajor())
                .bind("semver_minor", v.semverMinor())
                .bind("semver_patch", v.semverPatch())
                .bind("semver_pre_release", v.semverPreRelease() == null
                        ? Parameters.in(R2dbcType.VARCHAR) : Parameters.in(v.semverPreRelease()))
                .bind("status", v.status())
                .bind("definition_jsonb", defStr)
                .bind("schema_snapshot", snapStr)
                .bind("blake3_self", v.blake3Self())
                .bind("blake3_parent", v.blake3Parent() == null
                        ? Parameters.in(R2dbcType.VARBINARY) : Parameters.in(v.blake3Parent()))
                .bind("created_at", v.createdAt())
                .bind("deployed_at", v.deployedAt() == null
                        ? Parameters.in(R2dbcType.TIMESTAMP_WITH_TIME_ZONE) : Parameters.in(v.deployedAt()))
                .bind("deprecated_at", v.deprecatedAt() == null
                        ? Parameters.in(R2dbcType.TIMESTAMP_WITH_TIME_ZONE) : Parameters.in(v.deprecatedAt()))
                .fetch()
                .rowsUpdated()
                .thenReturn(v);
    }

    @Override
    public Flux<WorkflowVersion> findVersionsByWorkflow(UUID workflowId) {
        String sql = """
                SELECT version_id, workflow_id, semver_major, semver_minor, semver_patch,
                       semver_pre_release, status, definition_jsonb, schema_snapshot,
                       blake3_self, blake3_parent, created_at, deployed_at, deprecated_at
                FROM workflow.workflow_versions
                WHERE workflow_id = :workflow_id
                ORDER BY created_at DESC
                """;
        return databaseClient.sql(sql)
                .bind("workflow_id", workflowId)
                .map(this::mapVersion)
                .all();
    }

    private Workflow mapWorkflow(Readable row) {
        return new Workflow(
                row.get("workflow_id", UUID.class),
                row.get("sub_project", String.class),
                row.get("name", String.class),
                row.get("description", String.class),
                row.get("owner_subject", String.class),
                row.get("parent_workflow_id", UUID.class),
                Boolean.TRUE.equals(row.get("is_critical", Boolean.class)),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class)
        );
    }

    private WorkflowVersion mapVersion(Readable row) {
        try {
            String defRaw = row.get("definition_jsonb", String.class);
            String snapRaw = row.get("schema_snapshot", String.class);
            JsonNode definition = defRaw == null ? objectMapper.createObjectNode() : objectMapper.readTree(defRaw);
            JsonNode snapshot = snapRaw == null ? objectMapper.createObjectNode() : objectMapper.readTree(snapRaw);
            return new WorkflowVersion(
                    row.get("version_id", UUID.class),
                    row.get("workflow_id", UUID.class),
                    row.get("semver_major", Integer.class),
                    row.get("semver_minor", Integer.class),
                    row.get("semver_patch", Integer.class),
                    row.get("semver_pre_release", String.class),
                    row.get("status", String.class),
                    definition,
                    snapshot,
                    row.get("blake3_self", byte[].class),
                    row.get("blake3_parent", byte[].class),
                    row.get("created_at", Instant.class),
                    row.get("deployed_at", Instant.class),
                    row.get("deprecated_at", Instant.class)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize workflow_versions row", e);
        }
    }

    private String toJsonString(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize JsonNode", e);
        }
    }
}
