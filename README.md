# FASO-ANALYTICS-WORKFLOW

Moteur de workflows analytiques dynamique transversal pour la plateforme **FASO DIGITALISATION**.

## Vision

Permettre à un analyste métier de définir, dupliquer, simuler et déployer une chaîne complète **source → transformations → KPI → visualisations → publications** sans recoder à chaque évolution de formulaire — sur les 8 sous-projets cibles : VOUCHERS/AgriVoucher, E-TICKET, ÉTAT-CIVIL, SOGESY, HOSPITAL, FASO-KALAN, ALT-MISSION, E-SCHOOL.

## Principes fondateurs

1. **Schema-drift safe by design** — tout changement de schéma source est explicite, traçable, bloquant tant qu'il n'est pas adressé.
2. **Simulate-before-deploy** — aucun workflow ne passe en production sans avoir été simulé sur un échantillon validé et comparé à la version précédente.
3. **Sovereignty-first** — pas de runtime hors OVHcloud bare-metal ; cohérence ARMAGEDDON / KAYA / faso-mem / Ory / SPIFFE/SPIRE / Vault.
4. **Audit-immuable** — chaîne BLAKE3 append-only sur toute action métier.

## Statut actuel

| Phase | Statut | Sortie attendue |
|---|---|---|
| **0 — Cadrage** | en cours | ADR signés, plan figé, JSON Schema v1.0 publié |
| 1 — Fondations | non démarrée | Workflow trivial bout-en-bout (1 source YB, 1 filtre, 1 KPI) |
| 2 — Cœur fonctionnel | non démarrée | Parcours analyste sur PAGSI réel < 15 min |
| 3 — Industrialisation | non démarrée | SHADOW + BLUE/GREEN + rollback < 60 s |
| 4 — Migration & GA | non démarrée | 3 dashboards de référence migrés |

## Architecture (extrait — voir `docs/diagrams/`)

6 containers Podman rootless :

| Container | Tech | Rôle |
|---|---|---|
| `analytics-api` | Java 21 / Spring Boot / Netty | REST + STOMP, point d'entrée |
| `analytics-engine` | Rust / DataFusion / Tonic gRPC | Exécution transformations & agrégations |
| `analytics-simulator` | Rust + Podman-in-Podman | Sandbox éphémères de simulation |
| `analytics-scheduler` | Java 21 / Spring Boot | Planification batch (Quartz) |
| `analytics-cdc-consumer` | Java 21 / Redpanda client | Déclencheurs temps réel |
| `analytics-frontend` | Angular + Nginx | Constructeur visuel + viewer |

Tous mTLS SPIFFE/SPIRE. Secrets Vault (lease 15 min). Autorisations Keto. Audit BLAKE3 append-only sur YugabyteDB + Redpanda.

## Livrables du plan

- L1 — ADR-001 à ADR-006 (`docs/adr/`)
- L2 — JSON Schema workflow v1.0 (`schemas/workflow-v1.json`)
- L3 — Diagrammes C4 (`docs/diagrams/`)
- L4 — Diagrammes de séquence (`docs/diagrams/sequence-*.mmd`)
- L5 — DDL YugabyteDB + migrations Flyway (`services/analytics-api/src/main/resources/db/migration/`)
- L6 — OpenAPI 3.1 + AsyncAPI (`services/analytics-api/src/main/resources/openapi/`, `docs/asyncapi/`)
- L7 — Squelette Spring Boot hexagonal (`services/analytics-api/`)
- L8 — Squelette Angular + 3 visualisations clés (`services/analytics-frontend/`)
- L9 — Stratégie de tests (`docs/testing/strategy.md`)
- L10 — Runbook ops (`docs/runbook/`)
- L11 — Plan de migration dashboards existants (`docs/runbook/migration-dashboards.md`)

## Démarrage rapide

```bash
# Bootstrap stack (à compléter quand Phase 1 livrera les images)
make bootstrap
make up
make smoke
```

## Conformité

- Pas de runtime hors OVHcloud bare-metal.
- Toutes images : distroless, rootless UID 65532, multi-stage, thread-safe, stub-free.
- Three-tier truth source : YugabyteDB (vérité), DragonflyDB (cache TTL), Redpanda (stream).
- Hash-tag par agrégat business : `{workflow:<id>}` côté Dragonfly.
- Pas de Lua EVAL ; MULTI/EXEC + Redis Functions au besoin.
- Java 21 Virtual Threads + ZGC, Netty event loops dimensionnés (Little's Law).

## Licence

À définir (probablement AGPL-3.0 ou MPL-2.0 vu sovereignty-grade).

## Cadrage

Voir `docs/PLAN-ULTRAPLAN.md` pour le plan complet (référence : version 0.1-DRAFT, 2026-05-11).
