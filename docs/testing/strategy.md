# Stratégie de tests — FASO-ANALYTICS-WORKFLOW

## 1. Pyramide cible

```
       /\
      /  \      Chaos (5 %)        — Litmus/Chaos Mesh : kill engine pendant sim
     /    \     E2E (10 %)         — Playwright sur parcours §9.2
    /      \    Charge (10 %)      — k6 PAGSI 5 M lignes
   /        \   Intégration (25 %) — Testcontainers YB+DF+RP+Vault+Keto
  /          \  Contrat (10 %)     — Pact REST + Schema Registry Protobuf
 /            \ Mutation (5 %)     — Pitest + cargo-mutants score ≥ 70 %
/______________\ Unitaires (35 %)   — JUnit + cargo test, couv ≥ 85 %
```

## 2. Couverture cible

| Niveau | Outils | Couverture |
|---|---|---|
| Unitaire Java | JUnit 5 + AssertJ + Mockito | ≥ 85 % lignes domain/application |
| Unitaire Rust | `cargo test` + `proptest` | ≥ 85 % engine |
| Mutation | Pitest (Java), `cargo-mutants` (Rust) | Score ≥ 70 % sur le moteur de calcul |
| Intégration | Testcontainers (Yugabyte, Dragonfly, Redpanda, Vault, Keto) | Tous les use cases applicatifs |
| Contrat | Pact (REST), Schema Registry (Protobuf) | 100 % endpoints publics |
| Charge | k6 + scénarios PAGSI 5 M lignes | p95 < 5 s pour 1k échantillon, p95 < 60 s pour 1 M lignes batch |
| Sécurité | OWASP ZAP, semgrep, `cargo-audit`, `dependency-check`, `claude-code-security-review` | Zéro critique avant GA |
| Chaos | Litmus / Chaos Mesh : kill engine pendant simulation, partition Yugabyte, perte Redpanda | Rollback < 60 s, pas de corruption d'audit |
| Anti-régression analytique | Jeux **golden dataset** + assertions exactes sur KPI calculés | 100 % des KPI critiques |

## 3. Jeux golden

3 golden datasets minimum livrés avec le projet (Phase 1) :

| Code | Sub-projet | Lignes | Couverture analytique |
|---|---|---|---|
| `golden_pagsi_q1_2025` | VOUCHERS | 4 200 | Cible Q1 : 800 barrages curés × région + cible volume |
| `golden_resurep_26_w15` | HOSPITAL | 18 700 | Semaine 15 santé animale : cas par maladie × région |
| `golden_agrivoucher_phase1` | VOUCHERS | 950 | Distribution bons par commune × type céréale |

Format Parquet, stocké dans `workflow.golden_datasets.storage_uri` (MinIO bucket `faso-analytics-golden`).

## 4. Tests anti-régression analytique

Pour chaque KPI critique, un test JUnit applicatif :

```java
@Test
void taux_couverture_resurep_w15_must_be_84_2_percent() {
  var workflow = loadWorkflow("resurep_couverture_v1.0.0");
  var sim = simulator.simulate(workflow, GoldenDataset.RESUREP_26_W15);
  assertThat(sim.kpi("kpi_taux_couverture"))
      .isCloseTo(84.2, offset(0.05));   // tolérance ±0.05 %
}
```

Tout PR qui change un calcul de KPI doit soit conserver la valeur exacte, soit fournir une justification explicite avec migration de la baseline du golden test.

## 5. Tests de drift

Pour chaque modification du schéma source d'un golden dataset :

1. Reproduit le drift artificiellement (ajout / suppression / renommage de champ).
2. Vérifie que la détection signale le bon type de drift.
3. Vérifie que le déploiement est bloqué tant que le drift n'est pas résolu (selon `driftPolicy`).

## 6. Tests sandbox

Suite dédiée pour Phase 2-3 :

| Test | But |
|---|---|
| `sandbox_cannot_exfiltrate_dns` | Vérifie que `getent hosts evil.com` échoue depuis sandbox |
| `sandbox_oom_killed_within_2s` | Allouer 4 GiB → SIGKILL au-delà de `--memory=2g` |
| `sandbox_no_ptrace` | `gdb -p $$` doit échouer EPERM |
| `sandbox_no_mount` | `mount -t tmpfs none /tmp/x` doit échouer EACCES |
| `sandbox_max_runtime` | Simulation > timeout → SIGTERM puis SIGKILL à +10 s |

## 7. Critères GA (Phase 4)

- 100 % des tests unitaires + intégration verts en CI 14 jours consécutifs.
- 100 % des KPI critiques avec golden tests passants.
- 0 critique semgrep / dependency-check / cargo-audit.
- Chaos test "rollback en pleine production" validé en exercice piloté.
- Performance : p99 < 8 s pour 1 M lignes batch (10 % marge sur l'SLA p95 < 60 s).
