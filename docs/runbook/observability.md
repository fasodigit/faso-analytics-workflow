# Runbook observabilité — FASO-ANALYTICS-WORKFLOW

> Phase 3 task O. Couvre dashboards Grafana, alertes Prometheus, traces OTEL/Jaeger.
> Pour les incidents non liés à l'observabilité, voir `runbook-ops.md`.

## Stack

```
        ┌─────────────────┐    ┌──────────────────┐    ┌─────────────┐
services│ Spring/Rust apps│───▶│  OTEL Collector  │───▶│   Jaeger    │ (traces)
        │  + Micrometer   │    │  (otlp:4317)     │    └─────────────┘
        └────────┬────────┘    │                  │    ┌─────────────┐
                 │             │                  │───▶│ Prometheus  │ (metrics)
                 │ /actuator/  │                  │    │   :9090     │
                 │ prometheus  │                  │    └──────┬──────┘
                 └────────────▶│ scrape direct    │           │
                               └──────────────────┘    ┌──────▼──────┐
                                                       │   Grafana   │
                                                       │   :3000     │
                                                       └─────────────┘
```

- Tracing : OpenTelemetry → OTEL Collector → Jaeger (in-memory dev, Tempo prod).
- Metrics : Spring Boot Micrometer + Rust prometheus-client → Prometheus → Grafana.
- LLM costs : Langfuse (out-of-scope ici ; voir Phase 4 task LLM-OPS).

---

## Dashboards Grafana

Tous les dashboards utilisent les variables `$sub_project`, `$workflow_id`, `$time_range`
et le datasource Prometheus default.

### 1. `analytics-workflow-overview`

> _Screenshot placeholder : `docs/diagrams/grafana-overview.png`_

Questions métier auxquelles ce dashboard répond :

- Combien de workflows sont actuellement DEPLOYED / RUNNING ?
- Quelle est la cadence de simulation (req/s sur la fenêtre 5 min) ?
- Quelle latence simulation observe-t-on (p50/p95/p99) ?
- Quels workflows présentent le plus de drift ?
- Quels workflows ont les TTL de simulation les plus longs ?
- Quelles sont les 20 dernières entrées d'audit ?

Panels (10 au total) :

1. Stat — Workflows DEPLOYED.
2. Stat — Workflows actifs (RUNNING).
3. Stat — Simulations dernière heure.
4. Stat — p95 simulation duration.
5. Timeseries — Taux de simulation (req/s, 5min rate) par workflow.
6. Timeseries — Simulation duration p50/p95/p99.
7. Timeseries — Taux de drift détecté par workflow.
8. Table — Top 10 workflows par drift count (24h).
9. Table — Top 10 par simulation TTL (p95, 1h).
10. Table — Dernières 20 entrées audit_log.

### 2. `analytics-engine-internals`

> _Screenshot placeholder : `docs/diagrams/grafana-engine.png`_

Questions métier :

- Quelle méthode gRPC est la plus appelée (Compile / Validate / Execute) ?
- Quelle est la latence p95 par méthode ?
- Quel est l'usage mémoire DataFusion (heap + native) ?
- Combien de plans en cache dans le plan registry ?
- Quel est le hit ratio DragonflyDB ?

Panels (5 au total) :

1. Timeseries — gRPC RPC rate par méthode.
2. Timeseries — p95 latency par méthode gRPC.
3. Timeseries (stacked) — DataFusion execution memory (heap + native).
4. Stat — Plan registry size.
5. Gauge — Cache hit ratio DragonflyDB.

### 3. `analytics-sandbox-security`

> _Screenshot placeholder : `docs/diagrams/grafana-sandbox.png`_

Questions métier :

- Combien de sandboxes lancées par heure ?
- Quel cold-start p95 observe-t-on ?
- Combien de sandboxes OOM-killées (seuil warning > 1/h, critical > 10/h) ?
- Combien de timeouts SIGKILL ?
- Quels syscalls sont refusés par seccomp ?

Panels (5 au total) :

1. Timeseries (bars) — Sandboxes lancées par heure.
2. Stat — p95 démarrage cold-start.
3. Timeseries — Sandboxes OOM-killed (seuils warning/critical).
4. Timeseries — Sandboxes timeout SIGKILL.
5. Table — Seccomp denials par syscall (depuis `audit.log`).

---

## Alertes Prometheus

Les 7 incidents type du runbook §12 (plan ultraplan) sont câblés dans
`infra/prometheus/alerts.yml`. Chaque alerte pointe vers une section action.

### drift-detected

**Alerte** : `AnalyticsSchemaDriftDetected` — `analytics_schema_drift_total > 0` pendant 5 min.

**Action** :

1. Vérifier `Top 10 workflows par drift count` dans le dashboard overview.
2. Notification Slack `#faso-analytics-ops`.
3. Bloquer la ré-exécution batch via :
   ```bash
   curl -X POST .../v1/workflows/{id}/freeze \
        -H 'Content-Type: application/json' \
        -d '{"reason": "drift detected, manual investigation pending"}'
   ```
4. Ouvrir un ticket avec lien Grafana + traceId Jaeger.

**Délai cible** : < 5 min.

### simulation-timeout

**Alerte** : `AnalyticsSimulationTimeoutHigh` — p95 simulation > 30 s pendant 15 min.

**Action** :

1. Vérifier `analytics-engine-internals` : DataFusion memory + plan registry.
2. Identifier le sub_project saturé.
3. Basculer engine sur réplica :
   ```bash
   kubectl scale -n faso-analytics deploy/analytics-engine --replicas=+1
   ```
4. Si le tenant saturé est identifié, l'isoler (quotas K8s ResourceQuota).

**Délai cible** : < 15 min.

### kpi-threshold-breached

**Alerte** : `AnalyticsKpiThresholdBreached` — `increase(...{severity="critical"}[1h]) > 0`.

**Action** :

1. Notification métier (responsable du sub_project).
2. **Ne PAS rollback automatiquement** — décision métier obligatoire.
3. Logger la décision dans l'audit_log via :
   ```bash
   curl -X POST .../v1/workflows/{id}/decisions \
        -d '{"action": "ACCEPT_KPI_BREACH", "actor": "...", "rationale": "..."}'
   ```

**Délai cible** : Immédiat (notification), décision sous 24 h.

### audit-chain-break

**Alerte** : `AnalyticsAuditChainBreak` — `analytics_audit_chain_break_total > 0`.
**Sévérité 1 — incident critique**.

**Action** :

1. **Geler le workflow** immédiatement :
   ```bash
   curl -X POST .../v1/workflows/{id}/freeze -d '{"reason": "audit_chain_break"}'
   ```
2. Exporter la chaîne pour vérification offline (voir section ci-dessous).
3. Démarrer la procédure forensique : préserver les logs, désactiver les writes
   sur la partition tenant, notifier la cellule sécurité.
4. Inspecter `blake3_parent` vs `blake3_self` autour de l'`audit_id` cassé.

**Délai cible** : < 1 h.

### deployment-rollback-fail

**Alerte** : `AnalyticsDeploymentRollbackFail` — `increase(...[5m]) > 0`.
**Sévérité 1**.

**Action** :

1. Vérifier les logs `analytics-engine` post-bascule (health check KO).
2. Rollback manuel via Helm :
   ```bash
   helm rollback analytics-engine $(helm history analytics-engine -o json | jq '.[].revision' | tail -2 | head -1)
   ```
3. Si le rollback manuel échoue : couper le trafic via gateway et passer en
   maintenance.

**Délai cible** : < 60 s.

### sandbox-oom

**Alerte** : `AnalyticsSandboxOomHigh` — `increase(...[1h]) > 10`.

**Action** :

1. Vérifier le dashboard `analytics-sandbox-security`.
2. Identifier le sub_project / workflow_id qui sature.
3. Inspecter les limites cgroup :
   ```bash
   podman inspect <sandbox-id> | jq '.[0].HostConfig.Memory'
   ```
4. Ajuster les quotas dans `analytics-sandbox-supervisor` config (ou bloquer le
   workflow incriminé temporairement).

**Délai cible** : < 30 min.

### vault-lease-renewal-fail

**Alerte** : `AnalyticsVaultLeaseRenewalFail` — `increase(...[5m]) > 5`.

**Action** :

1. Vérifier la connectivité Vault depuis le pod analytics-api :
   ```bash
   kubectl exec -n faso-analytics deploy/analytics-api -- curl -s http://vault:8200/v1/sys/health
   ```
2. Vérifier les policies AppRole et la rotation du SecretID.
3. Renouvellement manuel temporaire :
   ```bash
   vault token renew <token>
   ```

**Délai cible** : < 10 min.

---

## Comment ajouter une nouvelle métrique

3 étapes pour qu'une métrique custom apparaisse dans Prometheus + Grafana.

### 1. Déclarer le `@Bean` Micrometer

Dans `services/analytics-api/src/main/java/bf/faso/analytics/infrastructure/observability/MetricsConfig.java` :

```java
@Bean
public Counter monNouveauCompteur(MeterRegistry registry) {
    return Counter.builder("analytics_mon_nouveau_total")
        .description("Description courte")
        .tag("kind", "default")
        .register(registry);
}
```

Conventions FASO :

- Préfixe : `analytics_`.
- Suffixe : `_total` pour les Counters, `_seconds` pour les Timer, `_bytes` pour les Gauges octets.
- Labels : `sub_project`, `workflow_id`, `tenant_id` quand pertinent.

### 2. Vérifier l'exposition Prometheus

Une fois redéployé, vérifier `GET /actuator/prometheus` :

```bash
curl -s http://analytics-api:8080/actuator/prometheus | grep mon_nouveau_total
```

Prometheus scrape automatiquement le job `analytics-api` (cf. `infra/prometheus/prometheus.yml`).

### 3. Ajouter au dashboard Grafana

Éditer le JSON correspondant dans `infra/grafana/dashboards/` :

- Ajouter un nouveau panel (timeseries / stat / table).
- Renseigner `targets[].expr` avec la PromQL appropriée.
- Si filtrage par `$sub_project` : `{ sub_project=~"$sub_project" }`.
- Re-provisionner Grafana (file watcher détecte le diff JSON).

---

## Comment lire la chaîne d'audit hors-ligne

Cf. ADR-005 (BLAKE3 + Vault transit) et la CLI `audit-verify` documentée dans
`runbook-ops.md` section "Vérification offline de la chaîne d'audit" :

```bash
podman exec -t analytics-api \
  java -jar /opt/analytics/audit-verify.jar \
  --workflow-id 01HK2EX5... \
  --since 2026-04-01 --until 2026-05-11 \
  --output /tmp/audit-verify.json
```

Si une rupture est détectée, la CLI sort un JSON :

```json
{ "ok": false, "break_at_audit_id": "01J...", "expected": "blake3:...", "actual": "blake3:..." }
```

Procéder ensuite à la section [`audit-chain-break`](#audit-chain-break) ci-dessus.

---

## Configuration Prometheus / OTEL / Jaeger

| Service | Endpoint | Config |
|---|---|---|
| Prometheus | `:9090` | `infra/prometheus/prometheus.yml` |
| Recording rules | — | `infra/prometheus/recording_rules.yml` |
| Alerts | — | `infra/prometheus/alerts.yml` |
| OTEL Collector | `:4317` (gRPC) / `:4318` (HTTP) | `infra/otel/otel-collector-config.yaml` |
| Jaeger UI | `:16686` | `infra/jaeger/jaeger-config.yaml` |
| Grafana | `:3000` | `infra/grafana/datasources/datasources.yml` + `infra/grafana/dashboards/*.json` |

### Variables d'environnement

| Variable | Service | Description |
|---|---|---|
| `ENV` | OTEL Collector, Prometheus | `dev` / `staging` / `prod` (resource attribute) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Apps | `http://otel-collector:4317` (override `application.yml`) |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | analytics-api | Override sampling (0.1 dev, 0.01 prod) |

### Sampling

Dev : 10 % (`management.tracing.sampling.probability=0.1`).
Prod (Phase 4) : 1 % en steady-state, 100 % sur erreurs (head-based + tail-based).

---

## Limitations connues

- Dashboards JSON authored mais **pas importés** dans une Grafana en cours d'exécution.
  À l'install : Helm chart Grafana avec provisioning ou `grafana-cli admin import`.
- Jaeger storage = `memory` (50 k traces, dev only). Phase 4 = Cassandra / Elasticsearch.
- Langfuse (LLM costs) hors scope, voir Phase 4 task LLM-OPS.
- Aucune Alertmanager config (routes Slack / PagerDuty) — task Phase 4 task ALERT-ROUTING.
- Les métriques exposées par `analytics-engine` (Rust) / `analytics-sandbox` doivent
  être instrumentées dans leur code source ; les dashboards les **référencent** mais
  ce wiring est out-of-scope du task O.
