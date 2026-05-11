# Runbook ops — FASO-ANALYTICS-WORKFLOW

## Incidents type

| Incident | Détection | Action | Délai cible |
|---|---|---|---|
| Drift détecté en production | Métrique `analytics_schema_drift_total > 0` | Notification Slack `#faso-analytics-ops` ; bloquer ré-exécution batch ; ouvrir ticket | < 5 min |
| Simulation timeout récurrent | Alerte sur p95 simulation > 10 × baseline | Vérifier source volumétrie ; basculer engine sur réplica ; isoler tenant | < 15 min |
| KPI critique franchit seuil | Event `KPI_THRESHOLD_BREACHED` | Notification métier responsable ; ne pas rollback automatiquement | Immédiat |
| Corruption suspectée audit chain | `blake3_parent` ne lie pas à `blake3_self` précédent | Geler workflow ; exporter chaîne pour vérification offline ; investigation forensique | < 1 h |
| Échec déploiement BLUE/GREEN | Health check `analytics-engine` post-bascule KO | Rollback automatique ; alerte sévérité 1 | < 60 s |
| Sandbox stuck en RUNNING > timeout | `analytics_sandbox_runtime_seconds > X` | SIGKILL forcé ; ouvrir ticket pour investigation | < 60 s |
| Vault token lease échec renouvellement | Log `vault_token_renew_failed` | Renouvellement manuel ; vérifier policies AppRole | < 10 min |

## Vérification offline de la chaîne d'audit

```bash
podman exec -t analytics-api \
  java -jar /opt/analytics/audit-verify.jar \
  --workflow-id 01HK2EX5... \
  --since 2026-04-01 --until 2026-05-11 \
  --output /tmp/audit-verify.json

# Sortie attendue :
# { "ok": true, "rows": 247, "chain_intact": true }
# ou
# { "ok": false, "break_at_audit_id": "...", "expected": "...", "actual": "..." }
```

## Rollback express

```bash
# 1. Identifier la version à restaurer
curl -X GET .../v1/workflows/{id}/audit | jq '.items[] | select(.action == "DEPLOY")'

# 2. Rollback
curl -X POST .../v1/workflows/{id}/versions/{previous_ver}/rollback \
     -H 'Content-Type: application/json' \
     -d '{"reason": "post-deployment regression on KPI X"}'

# 3. Vérifier
curl .../v1/workflows/{id} | jq '.active_version'
```

## Métriques Prometheus à surveiller

| Métrique | Seuil warning | Seuil critical |
|---|---|---|
| `analytics_simulation_duration_seconds{quantile="0.95"}` | > 5 | > 30 |
| `analytics_batch_execution_duration_seconds{quantile="0.95"}` | > 60 | > 300 |
| `analytics_engine_grpc_errors_total` | > 5/min | > 50/min |
| `analytics_audit_chain_break_total` | > 0 | > 0 (sévérité 1) |
| `analytics_kpi_threshold_breached_total{severity="critical"}` | > 0 | tracking |
| `analytics_sandbox_oom_killed_total` | > 1/h | > 10/h |
| `analytics_vault_lease_renewal_failed_total` | > 0 | > 5/h |
