-- ============================================================================
-- FASO-ANALYTICS-WORKFLOW — V1 schema initial
-- Target : YugabyteDB (PostgreSQL-compatible) 2.21+
-- Schema : workflow
-- Auteur : ultraplan v0.1-DRAFT
-- Date   : 2026-05-11
-- ============================================================================

-- Extensions requises (idempotent)
CREATE EXTENSION IF NOT EXISTS pgcrypto;        -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Schéma logique : isolement par sub-project via Keto, pas par schema PG.
CREATE SCHEMA IF NOT EXISTS workflow;
SET search_path TO workflow, public;

-- ----------------------------------------------------------------------------
-- 3.1 Workflows (entité racine)
-- ----------------------------------------------------------------------------
CREATE TABLE workflow.workflows (
    workflow_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_project        TEXT         NOT NULL CHECK (sub_project IN
                       ('VOUCHERS','E_TICKET','ETAT_CIVIL','SOGESY',
                        'HOSPITAL','FASO_KALAN','ALT_MISSION','E_SCHOOL')),
    name               TEXT         NOT NULL CHECK (name ~ '^[a-z][a-z0-9-]{2,63}$'),
    description        TEXT,
    owner_subject      TEXT         NOT NULL,
    parent_workflow_id UUID         REFERENCES workflow.workflows(workflow_id),
    is_critical        BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_subproject_name UNIQUE (sub_project, name)
);

COMMENT ON TABLE  workflow.workflows IS 'Workflow analytique racine (1 par couple sub_project, name)';
COMMENT ON COLUMN workflow.workflows.is_critical IS 'Si TRUE : déploiement 4-eyes obligatoire + shadow imposé';
COMMENT ON COLUMN workflow.workflows.parent_workflow_id IS 'Lignée duplication ; non transitif pour version (cf. workflow_versions)';

CREATE INDEX idx_workflows_sub_project ON workflow.workflows(sub_project) WHERE archived_at IS NULL;
CREATE INDEX idx_workflows_owner       ON workflow.workflows(owner_subject) WHERE archived_at IS NULL;

-- Trigger updated_at
CREATE OR REPLACE FUNCTION workflow.touch_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at := now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_workflows_touch BEFORE UPDATE ON workflow.workflows
    FOR EACH ROW EXECUTE FUNCTION workflow.touch_updated_at();

-- ----------------------------------------------------------------------------
-- 3.2 Versions de workflow (immutables une fois figées)
-- ----------------------------------------------------------------------------
CREATE TABLE workflow.workflow_versions (
    version_id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id        UUID         NOT NULL REFERENCES workflow.workflows(workflow_id) ON DELETE RESTRICT,
    semver_major       INT          NOT NULL CHECK (semver_major >= 0),
    semver_minor       INT          NOT NULL CHECK (semver_minor >= 0),
    semver_patch       INT          NOT NULL CHECK (semver_patch >= 0),
    semver_pre_release TEXT,
    status             TEXT         NOT NULL CHECK (status IN
                       ('DRAFT','SIMULATING','VALIDATED','DEPLOYED','DEPRECATED','ARCHIVED')),
    definition_jsonb   JSONB        NOT NULL,
    schema_snapshot    JSONB        NOT NULL,
    blake3_self        BYTEA        NOT NULL,
    blake3_parent      BYTEA,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deployed_at        TIMESTAMPTZ,
    deprecated_at      TIMESTAMPTZ,
    CONSTRAINT uq_workflow_semver UNIQUE (workflow_id, semver_major, semver_minor, semver_patch, semver_pre_release)
);

COMMENT ON COLUMN workflow.workflow_versions.definition_jsonb IS 'Conforme JSON Schema analytics.faso/v1';
COMMENT ON COLUMN workflow.workflow_versions.schema_snapshot IS 'Schéma source figé à la création (drift baseline)';
COMMENT ON COLUMN workflow.workflow_versions.blake3_self IS 'BLAKE3 keyed via Vault transit (cf. ADR-005)';

CREATE INDEX idx_wfv_workflow_status ON workflow.workflow_versions(workflow_id, status);
CREATE INDEX idx_wfv_deployed
    ON workflow.workflow_versions(workflow_id)
    WHERE status = 'DEPLOYED';
CREATE INDEX idx_wfv_definition_gin ON workflow.workflow_versions USING gin (definition_jsonb);
CREATE INDEX idx_wfv_schema_gin     ON workflow.workflow_versions USING gin (schema_snapshot);

-- ----------------------------------------------------------------------------
-- 3.3 Simulations
-- ----------------------------------------------------------------------------
CREATE TABLE workflow.simulations (
    simulation_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id         UUID         NOT NULL REFERENCES workflow.workflow_versions(version_id),
    requested_by       TEXT         NOT NULL,
    sample_strategy    TEXT         NOT NULL CHECK (sample_strategy IN
                       ('RANDOM','STRATIFIED','FIRST_N','LAST_N','PERIOD','GOLDEN')),
    sample_size        INT          NOT NULL CHECK (sample_size BETWEEN 1 AND 10000),
    sample_seed        BIGINT,
    status             TEXT         NOT NULL CHECK (status IN
                       ('QUEUED','RUNNING','SUCCEEDED','FAILED','TIMEOUT')),
    started_at         TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ,
    duration_ms        BIGINT,
    sandbox_id         TEXT,
    seccomp_hash       BYTEA,
    apparmor_hash      BYTEA,
    result_uri         TEXT,
    diff_vs_previous   JSONB,
    error_payload      JSONB,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sim_version_created ON workflow.simulations(version_id, created_at DESC);
CREATE INDEX idx_sim_status          ON workflow.simulations(status) WHERE status IN ('QUEUED','RUNNING');

-- ----------------------------------------------------------------------------
-- 3.4 Approbations (4-eyes pour critiques)
-- ----------------------------------------------------------------------------
CREATE TABLE workflow.approvals (
    approval_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id         UUID         NOT NULL REFERENCES workflow.workflow_versions(version_id),
    approver_subject   TEXT         NOT NULL,
    approved_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    comment            TEXT,
    CONSTRAINT uq_approval UNIQUE (version_id, approver_subject)
);

-- Garde-fou : un approbateur ne peut signer une version qu'il a éditée
-- (vérification applicative car PG ne peut pas voir l'auteur "actuel" du SQL)

-- ----------------------------------------------------------------------------
-- 3.5 Déploiements
-- ----------------------------------------------------------------------------
CREATE TABLE workflow.deployments (
    deployment_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id         UUID         NOT NULL REFERENCES workflow.workflow_versions(version_id),
    strategy           TEXT         NOT NULL CHECK (strategy IN ('DIRECT','SHADOW','BLUE_GREEN')),
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    rolled_back_at     TIMESTAMPTZ,
    rollback_reason    TEXT,
    shadow_ends_at     TIMESTAMPTZ,
    actor_subject      TEXT         NOT NULL
);

CREATE INDEX idx_deploy_version ON workflow.deployments(version_id, started_at DESC);

-- ----------------------------------------------------------------------------
-- 3.6 Audit (append-only, BLAKE3 chaîné — cf. ADR-005)
-- ----------------------------------------------------------------------------
CREATE TABLE workflow.audit_log (
    audit_id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id        UUID         REFERENCES workflow.workflows(workflow_id),
    version_id         UUID         REFERENCES workflow.workflow_versions(version_id),
    actor_subject      TEXT         NOT NULL,
    action             TEXT         NOT NULL CHECK (action IN
                       ('CREATE','UPDATE','DUPLICATE','SIMULATE','APPROVE',
                        'DEPLOY','ROLLBACK','SCHEMA_DRIFT_DETECTED',
                        'KPI_THRESHOLD_BREACHED','ARCHIVE','DEPRECATE')),
    payload_jsonb      JSONB        NOT NULL,
    blake3_self        BYTEA        NOT NULL,
    blake3_parent      BYTEA,
    key_version        TEXT         NOT NULL DEFAULT 'v1',
    occurred_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_workflow_time ON workflow.audit_log(workflow_id, occurred_at DESC);
CREATE INDEX idx_audit_action        ON workflow.audit_log(action, occurred_at DESC);

-- Trigger d'append-only : empêcher UPDATE/DELETE sur audit_log
CREATE OR REPLACE FUNCTION workflow.deny_audit_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'workflow.audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update
    BEFORE UPDATE OR DELETE ON workflow.audit_log
    FOR EACH ROW EXECUTE FUNCTION workflow.deny_audit_mutation();

-- ----------------------------------------------------------------------------
-- 3.7 Golden datasets (jeux figés pour simulation reproductible)
-- ----------------------------------------------------------------------------
CREATE TABLE workflow.golden_datasets (
    golden_id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_project        TEXT         NOT NULL,
    name               TEXT         NOT NULL,
    description        TEXT,
    schema_jsonb       JSONB        NOT NULL,
    rows_count         INT          NOT NULL CHECK (rows_count > 0),
    storage_uri        TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_golden UNIQUE (sub_project, name)
);

-- ----------------------------------------------------------------------------
-- 3.8 Connecteurs (références Vault, jamais de secret en clair)
-- ----------------------------------------------------------------------------
CREATE TABLE workflow.connectors (
    connector_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_project        TEXT         NOT NULL,
    name               TEXT         NOT NULL,
    kind               TEXT         NOT NULL CHECK (kind IN
                       ('yugabyte','kobo','surveymonkey','dragonfly','redpanda','metabase','upload')),
    vault_path         TEXT         NOT NULL,
    config_jsonb       JSONB        NOT NULL DEFAULT '{}',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rotated_at         TIMESTAMPTZ,
    CONSTRAINT uq_connector UNIQUE (sub_project, name)
);

-- ----------------------------------------------------------------------------
-- 3.9 Vue : version DEPLOYED courante par workflow (une seule à la fois)
-- ----------------------------------------------------------------------------
CREATE VIEW workflow.v_active_versions AS
SELECT DISTINCT ON (workflow_id)
       workflow_id, version_id, semver_major, semver_minor, semver_patch,
       deployed_at
FROM workflow.workflow_versions
WHERE status = 'DEPLOYED'
ORDER BY workflow_id, deployed_at DESC;

-- ============================================================================
-- Permissions (par convention plateforme : rôles `analytics_*` créés hors migration)
-- ============================================================================

-- Lecture seule pour reporting
-- GRANT USAGE ON SCHEMA workflow TO analytics_reader;
-- GRANT SELECT ON ALL TABLES IN SCHEMA workflow TO analytics_reader;

-- Application service (analytics-api) : INSERT/SELECT/UPDATE sauf audit_log et workflow_versions DEPLOYED
-- GRANT USAGE ON SCHEMA workflow TO analytics_app;
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA workflow TO analytics_app;
-- REVOKE UPDATE ON workflow.audit_log FROM analytics_app;
-- (audit_log INSERT only, jamais UPDATE — déjà bloqué par trigger)
