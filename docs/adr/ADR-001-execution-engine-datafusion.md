# ADR-001 — Moteur d'exécution : Apache DataFusion (Rust) appelé via gRPC

| Field | Value |
|---|---|
| **Status** | Proposed — default per ultraplan v0.1-DRAFT |
| **Decision date** | 2026-05-11 |
| **Stakeholders** | Lead architect, plateforme FASO DIGITALISATION |
| **Supersedes** | — |

## Contexte

Le module FASO-ANALYTICS-WORKFLOW exécute des chaînes `source → transformation → agrégation → KPI` sur des volumétries pouvant atteindre **5 millions de lignes par exécution batch** (Q1 du plan) et **10 000 lignes en simulation** (Q1). Les transformations incluent filter, join, group-by, pivot, window, outlier detection — opérations classiques BI.

La stack backend est ancrée Java 21 (Spring Boot, Netty, Virtual Threads, ZGC). Le choix du moteur de calcul doit concilier :

1. Performance vectorisée sur agrégations massives (>100 k lignes).
2. Souveraineté : exécution on-premise, pas de cloud externe.
3. Cohérence opérationnelle avec le reste de la plateforme.
4. Maintenabilité long terme (LTS, communauté active).

## Options évaluées

| Option | Pour | Contre |
|---|---|---|
| **A. Java pur (Stream API + VT)** | Cohérence stack, simplicité ops, pas de FFI, pas de service additionnel | Performance < DataFusion sur agrégations > 100k lignes ; pas de vectorisation Arrow ; coût mémoire GC élevé sur tables larges |
| **B. LakeSail (Spark Connect, Rust)** | Compatible Spark SQL, scale large, Rust moderne | Pré-1.0 (instabilité API), BYOC AWS-only (rupture sovereignty), surdimensionné pour v1 |
| **C. Apache DataFusion (Rust) via gRPC** | Vectorisé Arrow, mature (>5 ans), embeddable, on-prem, sovereignty OK, communauté Apache active | Ajoute un service Rust + FFI gRPC (latence, complexité ops) |
| **D. DuckDB embedded via JNI** | Très rapide single-node, SQL natif | JNI fragile, interactions avec GC Java imprévisibles, single-process (pas de bulkhead) |

## Décision

**Option C — Apache DataFusion exposé via service gRPC Tonic.**

Le service `analytics-engine` est un binaire Rust hébergé dans son propre container Podman rootless. Il expose 3 RPC unaires/streaming :

```protobuf
service AnalyticsEngine {
  rpc Compile(WorkflowDefinition) returns (ExecutionPlan);
  rpc Validate(WorkflowDefinition) returns (ValidationReport);
  rpc Execute(ExecutionPlanRef) returns (stream RecordBatchChunk);
}
```

Format d'échange : **Apache Arrow IPC** pour les `RecordBatch`, transporté en gRPC streams. Le client Java consomme via **Arrow Java** (compatible Java 21 + foreign memory API + ZGC).

Pour la simulation, l'orchestration est identique sauf que `SourceHandle` pointe vers un échantillon matérialisé dans DragonflyDB (clé `simulation:{simId}:sample`).

## Justification

1. **Vectorisation Arrow** : DataFusion + Arrow exécute SUM/AVG/MIN/MAX sur 1 M lignes ~5-10x plus vite qu'une approche row-based Java Stream sur les mêmes benchmarks publics (TPC-H Q1, Q5, Q6).
2. **Sovereignty** : Apache DataFusion est ASF top-level project, license Apache 2.0, exécution 100 % on-prem, pas de phone-home. LakeSail (option B) impose BYOC AWS pour son volet managed, ce qui casse la souveraineté.
3. **Maintenance** : projet Apache, ≥ 30 contributeurs actifs, release cycle stable, LTS-compatible.
4. **FFI à coût raisonnable** : gRPC streams avec Arrow IPC évitent le serialize/deserialize coûteux que JNI imposerait (option D). Le `RecordBatch` Arrow est zero-copy entre le buffer Rust et le buffer Java côté lecteur Arrow Java.

## Conséquences

### Positives

- Latence agrégation 1 M lignes : p95 cible **< 5 s** (vs ~30 s estimé pour Java Stream pur).
- Capacité simulation 10 k lignes : p95 **< 500 ms** (largement sous l'SLA p95 < 5 s du Q3).
- Le moteur reste réutilisable hors workflow (futurs cas d'usage analytiques transverses).
- Le service Rust est isolé : un crash de DataFusion ne tue pas l'API Java.

### Négatives / risques

- **Risque R-FFI** : la latence du round-trip Java↔Rust via gRPC sur Arrow IPC doit être benchmarkée en Phase 1. Si le coût d'invocation dépasse 50 ms p95 pour un workflow trivial, considérer l'option D (DuckDB JNI) comme fallback.
- **Complexité opérationnelle +1** : un binaire Rust de plus à compiler, tester, conteneuriser, déployer. Mitigé par CI multi-stage (Containerfile.rust déjà standardisé sur la plateforme).
- **Compétences** : l'équipe doit savoir lire/écrire du Rust idiomatique. Plan de formation à inclure en Phase 0.

## Conditions de réexamen

L'ADR sera revisité si l'un des seuils suivants est franchi :

1. Latence round-trip Java↔Rust > 50 ms p95 sur workflow trivial → bascule sur option D.
2. DataFusion publie un breaking change majeur incompatible avec Arrow Java → pinning de version + fork OU bascule option D.
3. Volumétrie cible dépasse 50 M lignes par exécution → considérer option B (LakeSail/Spark) pour la partie batch distribuée.

## Référence

- Apache DataFusion : <https://datafusion.apache.org>
- Apache Arrow : <https://arrow.apache.org>
- Tonic gRPC (Rust) : <https://github.com/hyperium/tonic>
- Arrow Java : <https://arrow.apache.org/docs/java/>
