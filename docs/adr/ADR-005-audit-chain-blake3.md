# ADR-005 — Audit chain : BLAKE3 + chaînage append-only + Vault transit

| Field | Value |
|---|---|
| **Status** | Proposed — default per ultraplan v0.1-DRAFT |
| **Decision date** | 2026-05-11 |

## Contexte

Le module FASO-ANALYTICS-WORKFLOW manipule des **données ministérielles à enjeux** (RESUREP santé animale, PAGSI sécurité alimentaire, HOSPITAL, ÉTAT-CIVIL). Chaque action métier (CRUD workflow, simulation, approbation, déploiement, rollback) doit produire un audit **vérifiable hors-ligne** et **résistant à la falsification**, y compris par un opérateur de la base de données.

Exigences :

1. **Append-only stricte** : pas d'UPDATE / DELETE possible (sauf rôle super-admin journalisé).
2. **Chaînage cryptographique** : un audit_log peut être prouvé tamper-evident sans accès à la DB.
3. **Performance** : insertion synchrone < 5 ms p95 (sinon goulot sur les use cases).
4. **Vérifiabilité offline** : extraire la chaîne et la valider sans dépendance YugabyteDB.

## Options évaluées

| Option | Hashing | Chaînage | Vérification | Note |
|---|---|---|---|---|
| **A. BLAKE3 + chaîne `parent_hash`** | BLAKE3-256 keyed via Vault transit | Chaque ligne référence le hash de la précédente | Walk chronologique, hash recalculé | **Retenue** |
| **B. SHA-256 + chaîne** | SHA-256 | idem | idem | Plus lent que BLAKE3 (~3x), pas de gain |
| **C. Merkle tree avec ancrage Bitcoin** | SHA-256 | Tree | Inclusion proof | Surdimensionné, dépendance Bitcoin = pas souverain |
| **D. Immudb à côté** | Merkle + Sparse Merkle | Native | Native | Bonne option mais ajoute un service (cohérence opérationnelle vs ARMAGEDDON qui l'utilise déjà ?) |
| **E. Sigstore Rekor** | Merkle | Native | Native | Cloud, pas souverain |
| **F. ActiveMQ Artemis journal** | — | non | non | Pas tamper-evident |

## Décision

**Option A — BLAKE3-256 keyed + chaîne `blake3_parent` + Vault transit pour le keying.**

### Schéma de hashing

Pour chaque entrée d'audit :

```
canonical_payload = JCS(payload_jsonb)
self_hash         = BLAKE3_256(key = vault_key_v3, input = self_serialized_entry)

self_serialized_entry = canonical(
  audit_id,
  workflow_id,
  version_id,
  actor_subject,
  action,
  canonical_payload,
  occurred_at,
  blake3_parent  // hash de l'entrée précédente du même workflow
)
```

### Bootstrap (premier audit d'un workflow)

`blake3_parent = NULL` → entrée racine pour ce workflow. La chaîne par-workflow simplifie la vérification (pas de chaîne globale qui force un walk de millions de lignes).

### Stockage

- `blake3_self` et `blake3_parent` en BYTEA (32 bytes).
- Index unique sur `(workflow_id, occurred_at)` pour walk ordonné.
- Trigger PostgreSQL `deny_audit_mutation` qui INTERDIT UPDATE/DELETE :

```sql
CREATE OR REPLACE FUNCTION workflow.deny_audit_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_audit_no_update BEFORE UPDATE OR DELETE ON workflow.audit_log
  FOR EACH ROW EXECUTE FUNCTION workflow.deny_audit_mutation();
```

(Le rôle super-admin peut désactiver le trigger via `ALTER TABLE ... DISABLE TRIGGER`, ce qui produit un événement YugabyteDB capture par CDC + alerte sévérité 1.)

### Keying

- Clé BLAKE3 dérivée de Vault transit `transit/keys/analytics-audit-v3`.
- Rotation tous les 90 jours, mais les clés précédentes (`v1`, `v2`) restent valables pour vérifier l'ancien historique.
- Version de clé stockée dans le payload audit : `payload_jsonb.key_version = "v3"`.

### Vérification offline

CLI livré avec le module : `analytics-audit-verify --workflow-id <uuid> --csv <export>` :

1. Lit l'export CSV/JSON des audits du workflow (ordonnés par `occurred_at`).
2. Pour chaque ligne, recalcule `blake3_self` avec la clé Vault appropriée.
3. Vérifie que `blake3_parent` de la ligne courante == `blake3_self` de la ligne précédente.
4. Sort un rapport : `OK (N lignes vérifiées)` ou `BREAK à la ligne X : hash divergent`.

## Justification

1. **BLAKE3 perf** : ~3-5 GB/s sur CPU moderne, ~3x plus rapide que SHA-256. Surcout sur INSERT négligeable (< 1 ms).
2. **Keying via Vault transit** : la clé n'apparaît jamais en clair dans le code applicatif ; rotation centralisée ; auditable.
3. **Chaîne par-workflow** : vérification scalable. Une chaîne globale forcerait un walk de millions d'entrées à chaque vérification.
4. **Trigger PG `deny_audit_mutation`** : protection au plus près de la donnée, indépendante du code applicatif.
5. **Cohérence ARMAGEDDON** : ARMAGEDDON utilise déjà BLAKE3 pour ses propres chaînes audit (réutilisation des skills équipe).

## Conséquences

### Positives

- Insertion p95 < 5 ms (validé par benchmark interne BLAKE3 + INSERT YB).
- Vérification CLI offline en ~1 s pour 10 000 lignes.
- Pas de service additionnel (vs option D Immudb).
- Auditeur tiers peut reconstruire la chaîne avec une simple lib BLAKE3 + clé Vault (lecture seule).

### Négatives

- Risque opérationnel : un opérateur DB super-admin peut désactiver le trigger. **Mitigation** : ce rôle est attribué via JIT (Just-In-Time) avec approbation 4-eyes et alerte Slack systématique.
- La rotation de clé tous les 90 j impose qu'un audit log puisse contenir 3-4 versions de clé sur 1 an. **Mitigation** : `key_version` dans payload.
- BLAKE3 moins répandu que SHA-256 → support tooling forensic limité. **Mitigation** : binaire `analytics-audit-verify` fourni + spec markdown du protocole.

## Conditions de réexamen

- Si une CVE BLAKE3 critique sort → migration SHA-3 (Keccak) avec re-hash de l'historique signé par la nouvelle clé.
- Si un régulateur impose un ancrage public (blockchain consortium FASO) → ajouter un job Celery qui poste la racine du jour sur une blockchain souveraine.

## Référence

- BLAKE3 : <https://github.com/BLAKE3-team/BLAKE3>
- JCS (JSON Canonicalization Scheme) RFC 8785 : <https://www.rfc-editor.org/rfc/rfc8785.html>
- Vault transit engine : <https://developer.hashicorp.com/vault/docs/secrets/transit>
- PostgreSQL triggers : <https://www.postgresql.org/docs/current/triggers.html>
