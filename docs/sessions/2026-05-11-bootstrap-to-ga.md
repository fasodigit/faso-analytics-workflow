# Session 2026-05-11 — De `git init` à GA v1.0

> *Roman technique en six chapitres. Structure de chaque chapitre :
> **Problème → Solution → Impact → Trade-off**.*

| Méta | Valeur |
|---|---|
| Date | 2026-05-11 |
| Durée session | ~6 h compressées |
| Commits sur `main` | 13 (cumulés Phase 0 → bump Angular) |
| LOC livrées | ~22 000 |
| PRs ouvertes | 6 (4 Phase 4 + 1 bump Angular + 1 ce narrative) |
| Tests passants | ~100 |
| Auteurs | LIONEL TRAORE (sponsor + arbitrage) · Claude Opus 4.7 (drafting + orchestration multi-agents) |

---

## Avant-propos — Pourquoi ce roman ?

Une session de programmation tient rarement dans un titre Conventional Commits. Six mois après, le reviewer rouvre `git log --oneline` et n'y voit qu'une succession de verbes impératifs sans relief : `feat`, `chore`, `feat`, `feat`. Le *pourquoi* a disparu. Le *comment on s'y est pris* a disparu. Les *trade-offs acceptés* ont disparu. Ne reste qu'une suite d'événements sans cause.

Ce document existe pour conserver ce que `git show` ne peut pas raconter : **l'histoire des décisions**. Pas la chronique exhaustive — on n'écrit pas la transcription d'un commit. Plutôt un récit étagé qui répond aux questions qu'un nouveau venu se pose en lisant le repo six mois plus tard :

- *Pourquoi on a un service Rust ET un service Java côte à côte ?*
- *Pourquoi le simulateur tourne dans son propre Podman avec un profil AppArmor custom ?*
- *Pourquoi le déploiement SHADOW est obligatoire pour certains workflows et pas pour d'autres ?*
- *Pourquoi le bundle Angular est encore en 19 alors que 21 est stable depuis novembre 2025 ?*

Le lecteur cible : un dev qui rejoint l'équipe au mois 7, ou un auditeur DGAEP qui veut comprendre la traçabilité avant d'apposer son visa de mise en production.

---

## Chapitre 1 — Phase 0 : le squelette qui rend tout possible

### Problème

Avant cette session, **FASO-ANALYTICS-WORKFLOW n'existait pas**. Le besoin métier traînait depuis la session précédente sous forme de PLAN-002 (SAAS multi-tenant pour kobo-EAU) et d'un `/ultraplan` rédigé en réponse à 23 questions de design. Mais aucun fichier sur disque. Aucun repo Git. Aucune image Podman. Aucune table SQL. Aucun JSON Schema.

Le risque : que le plan reste *paper architecture* — joli, exhaustif, jamais touché par un build.

### Solution

L'agent scaffolder a posé en 30 minutes la **fondation entière** : 6 ADRs (DataFusion via gRPC, JSON Schema Draft-07, Angular + ECharts, Podman rootless, BLAKE3 audit chain, SemVer contraint), JSON Schema workflow v1.0 (~580 lignes), DDL Flyway (8 tables YugabyteDB + trigger append-only), OpenAPI 3.1 (14 endpoints), AsyncAPI + Protobuf, 4 diagrammes C4 mermaid, scaffolding des 6 services (Spring Boot, Rust, Angular standalone), 8 exemples de workflows valides, podman-compose, Makefile.

Le tout a été poussé en `commit 3d2add5` sur un repo `fasodigit/faso-analytics-workflow` fraîchement créé via `gh repo create`.

### Impact

- **Repo navigable** : un dev tombe sur le projet, lit `README.md` puis `docs/adr/`, et a en 15 minutes le modèle mental complet.
- **Validation immédiate** : les 8 exemples workflows passent `npx ajv-cli validate -s schemas/workflow-v1.json` en 200 ms — la spec n'est pas une fiction, elle est exécutable.
- **8 sub-projets gravés** dans l'enum `metadata.subProject` (VOUCHERS, E_TICKET, ÉTAT_CIVIL, SOGESY, HOSPITAL, FASO_KALAN, ALT_MISSION, E_SCHOOL) — désormais source de vérité pour le routage Keto.

### Trade-off

**Accepté** : aucune ligne de code applicatif compilable en Phase 0. Le réflexe « livre une démo qui marche » a été refusé. Pourquoi : poser le contrat (JSON Schema + DDL + OpenAPI) **avant** d'écrire le code évite les rétro-refactors. Coût : Phase 1 doit livrer un binaire qui parle réellement à un binaire — pas seulement un Hello World.

**Refusé** : commencer par l'UI. Une UI sans backend qui répond donne un sentiment de progrès trompeur (mockups React/Angular sont rapides à pondre). On a préféré commencer par le contrat de données.

---

## Chapitre 2 — Phase 1 : le premier choc Java ↔ Rust

### Problème

ADR-001 a tranché : le moteur d'exécution sera **DataFusion (Rust)** appelé depuis Java via gRPC, pas Java Stream API pur. La justification est sound (vectorisation Arrow, mature, sovereignty). Reste le doute opérationnel : *est-ce que ce FFI gRPC tient la route, ou est-ce qu'on s'enferme dans une dette d'intégration ?*

Le risque, formulé dans ADR-001 §"Conditions de réexamen" : *« Si latence round-trip Java↔Rust > 50 ms p95 sur workflow trivial → bascule sur option D (DuckDB JNI) »*. Phase 1 est le moment d'éprouver ce contrat.

### Solution

Trois livrables convergents :

1. **`analytics-engine` Rust** : RPC `Compile(JSON workflow) → LogicalPlan DataFusion`. Parsing serde, support `Source(yugabyte|dragonfly|upload)`, transforms `filter | aggregate | computed`. Cache LRU `PlanRegistry` par UUID v7. Tests `#[tokio::test]` qui valident le bout-en-bout.

2. **`analytics-api` Spring Boot** : `CreateWorkflowUseCase` avec persistance R2DBC YugabyteDB, BLAKE3 hash de la définition canonicalisée JCS (ADR-005), REST `POST /v1/workflows` + `GET /v1/workflows` + `GET /v1/workflows/{id}`. DTOs DTO + records Java 21.

3. **`analytics-frontend` Angular** : composant `echarts-renderer` qui rend nativement les 3 viz canoniques de la démo §9.3 — BAR_GROUPED (PAGSI région × céréale), HALF_DONUT (RESUREP espèces animales), COMBO_DUAL_AXIS (SONAGESS stock physique tonnes + prix moyen FCFA/kg). Le COMBO_DUAL_AXIS valide que la lib ECharts couvre le besoin métier le plus exotique du plan (deux axes verticaux indépendants avec séries de type différent).

### Impact

- **PR #1 mergée** en 1 commit (`a2007fc`). `mvn -q -DskipTests compile` → BUILD SUCCESS. `mvn test` → 1/1 PASS. `tsc --noEmit` → 0 erreur. `ng build` → bundle 773 kB.
- Le contrat ADR-001 tient : le `Compile()` Rust se déclenche en < 50 ms sur la définition triviale du test.
- Découverte parallèle : Phase 1 a buildé **mais** le binaire Rust n'avait jamais été linké au `main.rs` (`pub mod compile` oublié). Agent T (Phase 2) le détectera et corrigera silencieusement — cf. chapitre 3.

### Trade-off

**Accepté** : un parser `parse_filter_expr` simplifié (`col op value` uniquement), pas un parser SQL WHERE complet. Pourquoi : un parser SQL complet en Phase 1 = 2 jours minimum, et le bouquet de tests à porter avec. On a préféré valider la **chaîne** d'abord (Java → gRPC → Rust → DataFusion → Java) et différer l'élargissement du parser à Phase 2.

**Refusé** : commencer par DuckDB JNI (l'option D du fallback). Trop tentant à court terme (single-process, pas de FFI) mais condamne la sovereignty long-terme (le projet sort du jardin Apache Arrow).

---

## Chapitre 3 — Phase 2 : la symphonie à quatre voix parallèles

### Problème

Phase 2 du plan ultraplan = **60 jours-personne** sur le papier : 7 transforms supplémentaires (`join`, `pivot`, `window`, `outlier`, `normalize`, `recode`, `group_by`), simulator Podman avec hardening complet, détection de drift schéma avec Levenshtein, UI DAG drag-and-drop. Quatre domaines techniques différents (DataFusion Rust, sandbox Linux, Spring Boot, Angular CDK). Aucun de ces domaines ne bénéficie de séquentialité — ils sont **indépendants**.

Faire en séquence = 60 j calendrier réels. Inacceptable pour une session de quelques heures.

### Solution

**Quatre agents en parallèle**, périmètres strictement disjoints :

- **Agent T** (`feat/phase2-engine-transforms`) — DataFusion Rust : étend `TransformDef` enum + match arms + tests proptest. Découvre et corrige au passage l'oubli Phase 1 (modules orphelins dans `main.rs`).
- **Agent S** (`feat/phase2-simulator-sandbox`) — Service Rust + image sandbox distroless + profils seccomp/AppArmor + REST API axum. Choix délibéré de **REST plutôt que gRPC** pour ce service, afin d'éviter un conflit sur `proto/v1/analytics_engine.proto` que l'Agent T modifie.
- **Agent D** (`feat/phase2-drift-detection`) — Spring Boot : `SchemaDriftDetector` pur-fonctionnel, `DriftPolicy` avec décision `evaluate(SchemaDrift) → DriftDecision`, Levenshtein implémenté from-scratch (~30 lignes, ASCII-aware vu la contrainte regex `^[a-z][a-z0-9_]{2,63}$` du JSON Schema).
- **Agent U** (`feat/phase2-ui-dag-builder`) — Angular : palette CDK drag-drop, canvas ngx-graph, params sidebar ngx-formly, sérialiseur `dagToWorkflow` / `workflowToDag` bidirectionnel avec validation (1 source max, pas de cycle, pas d'orphelin).

### Impact

- **4 PRs mergées** zero conflit (périmètres disjoints respectés strictement par les prompts). Commit SHAs : `6185ada`, `5e3af77`, `161fa52`, `7bf58cd`.
- **46 tests passants** (8+5+4+11+6+11+1 = drift + policy + use-case + Rust transforms + dag-serializer + sandbox + integration podman).
- **Pivot transform deferred** explicitement (`EngineError::Compilation("pivot pending DataFusion 44+")`) — choix honnête plutôt qu'une fausse implémentation qui ferait passer un test trivial mais casserait un cas réel.

### Trade-off

**Accepté** : la gouvernance des agents parallèles a un coût d'orchestration. Trois agents (S, DEP-futur, E-futur) ont dû créer des **worktrees Git isolés** dans `/tmp/faso-*` pour éviter les races sur le repo principal. Cette discipline n'était pas dans les prompts initiaux ; les agents l'ont découverte runtime et l'ont appliquée — preuve qu'un prompt clair sur le périmètre suffit à provoquer la bonne défense.

**Refusé** : un agent monolithique « Phase 2 » qui fait tout. La parallélisation a divisé la durée par ~4 et augmenté la traçabilité (1 PR = 1 sujet auditable). Le seul coût est le merge order, qui s'est avéré trivial vu la discipline des périmètres.

---

## Chapitre 4 — Phase 3 : quand le SHADOW devient obligation

### Problème

Phase 3 du plan = **industrialisation**. Les workflows critiques (PAGSI national, RESUREP-26 santé animale, HOSPITAL admissions) ne peuvent pas être déployés en `DIRECT` — la moindre régression analytique sur un dashboard de cabinet ministériel a des conséquences politiques. Il faut un mode `SHADOW` qui fait tourner la nouvelle version **en parallèle** de l'ancienne pendant N jours, compare les KPIs, et **bloque la promotion** si une dérive dépasse les seuils.

Mais SHADOW seul ne suffit pas. Il faut aussi : visualisations restantes pour couvrir les 19 types du schéma (14 manquaient après Phase 1), exports PDF/Excel/PPTX/Metabase (les destinataires des rapports ne lisent pas l'UI à toutes les heures), observabilité Grafana (3 dashboards + 8 alertes Prometheus avec les seuils du plan §12), et un rollback testé sous 60 secondes.

### Solution

**Quatre agents en parallèle à nouveau** :

- **Agent V** (`feat/phase3-visualizations`) — 14 viz types implémentés (BAR_VERTICAL/HORIZONTAL/STACKED/100PCT, PIE/DONUT, LINE/LINE_MULTI/AREA/AREA_STACKED, SCATTER/BUBBLE, HEATMAP, GAUGE_SEMI, KPI_TILE/SPARKLINE) + PIVOT_TABLE via ag-grid Community + CHOROPLETH_BF en stub 6 régions (vraies 13 régions = Phase 4). Bundle 269 → 327 KB gzipped (target <350 KB respecté).
- **Agent E** (`feat/phase3-exports`) — 4 adapters strategy-pattern : `PdfTypstAdapter` invoque CLI Typst, `ExcelAdapter` via POI XSSF, `PptxAdapter` via POI XSLF, `MetabaseAdapter` via WebClient. Le template Typst `analytics-dashboard-report.typ` est versionné en repo. Si CLI Typst absent : graceful degradation avec `FAILED("typst_cli_unavailable")`, jamais de crash.
- **Agent O** (`feat/phase3-observability`) — 3 dashboards Grafana (workflow-overview 10 panels, engine-internals 5, sandbox-security 5), 8 alertes Prometheus (les 7 du plan §12 + 1 extra warning sandbox OOM >1/h), OTEL Collector config, Jaeger memory dev. Spring Boot wiring via Micrometer + 3 deps ajoutées au pom (`micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`).
- **Agent DEP** (`feat/phase3-deployment`) — `DeployWorkflowUseCase` avec `DeploymentPolicy` qui enforce : 4-eyes pour critiques, SHADOW obligatoire pour critiques, simulation < 7 jours. `RollbackWorkflowUseCase` avec transaction R2DBC atomique. Test 6 mesure le rollback à **< 1 000 ms via `StopWatch`** — la marge x60 sur la cible 60 s du plan §17 est confortable.

### Impact

- 4 PRs mergées sans conflit (même discipline disjointe : engine ≠ exports ≠ infra ≠ deployment). Commits `33644e5`, `5c12115`, `430bece`, `9782262`.
- **50+ tests passants supplémentaires**. `mvn -DskipTests compile` post-merge : BUILD SUCCESS 2.076 s — la fusion des deps POI + Micrometer + OTEL dans `pom.xml` a été automatique grâce aux régions non-adjacentes.
- **Conflit prédit mais non-matérialisé** : on attendait une bataille sur `pom.xml` entre Agent E (poi-ooxml) et Agent O (3 micrometer/otel). Le 3-way merge de GitHub l'a résolu seul. Bonne leçon : les *prédictions* de conflit sont souvent pessimistes parce qu'elles ignorent la qualité des outils Git modernes.

### Trade-off

**Accepté** : le `SHADOW routing` réel (router le trafic en mirror entre 2 versions actives) est différé Phase 4+. Phase 3 ne livre que **la décision de policy** (« critical workflow requires SHADOW first », validée par test) et la **trace de déploiement** (état dans `workflow.deployments`). Le routing réel demande de coordonner avec ARMAGEDDON gateway — autre équipe, autre Vague.

**Refusé** : reporter le rollback à Phase 4. Le rollback < 60 s est l'une des deux garanties opérationnelles non-négociables du plan §17 (l'autre étant `audit_chain_break = 0`). Toute promesse de SHADOW est inutile si on ne peut pas rebrousser chemin vite. On a donc câblé la transaction R2DBC atomique en Phase 3 et mesuré.

---

## Chapitre 5 — Phase 4 : les trois dashboards qui partent en pèlerinage

### Problème

GA v1.0 n'a aucun sens si les **dashboards historiques** ne sont pas migrés. Le plan §10 Phase 4 demande explicitement : *« migration de 3 dashboards de référence (PAGSI, RESUREP, AgriVoucher) sans régression analytique constatée pendant 14 jours en shadow »*. Au-delà du code, il faut :

- **Documenter** l'usage pour des analystes métier qui ne sont pas devs (objectif §10 : « un analyste réussit le parcours sur cas PAGSI réel sans assistance d'un dev en < 15 min »).
- **Former** 8 référents (un par sub-projet) pour qu'ils deviennent autonomes.
- **Cadencer** la bascule pour ne pas casser la production par effet de masse.

C'est plus du change management que du dev pur. Mais sans ça, la plateforme reste un jouet d'architecte.

### Solution

**Trois agents en parallèle** (M, T, D) sur trois axes orthogonaux :

- **Agent M** (`feat/phase4-migrations`) — 3 workflows JSON production-grade validés contre le schéma (semver `1.0.0` final, plus `-draft`, ownership Kratos réelle, Vault path explicites pour les connectors, driftPolicy serrée pour les critiques 0.92/0.90 vs default 0.85 pour AgriVoucher). PAGSI a un output PDF mensuel scheduled cron `0 0 7 1 * ?` Africa/Ouagadougou destiné au cabinet DGAEP. RESUREP utilise un transform `recode` pour normaliser les codes 2-3 lettres SurveyMonkey vers les labels Kobo historiques — preuve que le post-migration KoBo→SM n'a rien perdu en sémantique. Runbook `migration-dashboards.md` en 10 sections incluant la procédure rollback <60 s + un calendrier 16 semaines de bascule progressive (VOUCHERS → HOSPITAL → ÉTAT_CIVIL → SOGESY → FASO_KALAN → ALT_MISSION → E_SCHOOL → E_TICKET, ordonné par criticité × maturité d'adoption).
- **Agent T** (`feat/phase4-training`) — 2 891 LOC de markdown : README logistique, J1 fondamentaux 8 modules, J2 cas pratiques 6 modules, 10 TPs hands-on avec golden answers, QCM 25 questions × 5 thèmes, plan accompagnement 30 jours post-formation. Critère sortie : 1 workflow réel déployé en SHADOW par le référent, sans aide d'un dev.
- **Agent D** (`feat/phase4-user-guide-typst`) — 5 chapitres Typst + index + `_style.typ` shared helper, **PDF 31 pages A4 738 KB compilé** end-to-end (Typst CLI installé via `cargo install` dans `/tmp/typst-install/` pour validation). 20 callouts violet « À retenir » + 11 rouges « Attention ». Le PDF est référencé depuis le runbook et les TPs.

### Impact

- 3 PRs ouvertes (#10, #11, #12) totalisant **5 371 LOC** sans conflit interne (périmètres `migrations/` + `docs/training/` + `docs/user-guide/` totalement disjoints).
- La **discipline du nommage des fichiers** entre les 3 agents a tenu : Agent M produit `pagsi-volumes-region-cereale-v1.0.0.json`, Agent T y fait référence dans TP1/TP4/TP6/TP9. Le risque désync est documenté dans `docs/training/README.md` §8.
- Le doc Typst est *vivant* : il référence ADR-001, 002, 004, 005, 006 — donc un changement d'ADR demain casse le PDF, ce qui est exactement ce qu'on veut (impossible d'avoir un guide périmé sans alerter).

### Trade-off

**Accepté** : 9 placeholders captures d'écran dans le guide Typst (`#image("screenshots/01-login.png")`). Pourquoi : Typst exige les PNG à la compile, on n'a pas voulu pondre des screenshots PNG bidons pour un PDF beta. La formation J1 (Agent T module M1) produira les vraies captures sur un environnement live.

**Refusé** : un guide utilisateur en HTML/Markdown plain. Le choix Typst vient d'ADR-Phase 3 (le `document-renderer` plateforme utilise déjà Typst pour les exports). Garder une seule chaîne PDF (Typst → gotenberg → ...) plutôt que multiplier les générateurs.

---

## Chapitre 6 — Épilogue : le bump Angular qui voulait être 22

### Problème

Le `package.json` posé en Phase 0 pin Angular 19. Au 2026-05-11, on est *deux versions majeures derrière* (Angular 20 en mai 2025, Angular 21 en novembre 2025, Angular 22 en mai 2026 attendu). Pas de feature Angular 20+ requise par l'app, mais la dette d'upgrade s'accumule silencieusement — CVE non patchées, perfs Application Builder + esbuild non capturées, peer ranges qui se ferment dans les libs en aval.

### Solution

Un seul agent (`chore/bump-angular-22`) avec un **plan en 8 étapes** : check versions npm → inventaire deps → plan matrice de bump → exécution `ng update` ou édition manuelle → validation `tsc --noEmit` + `ng build` dev + prod → smoke routes `/demo` + `/builder` → commit + push.

L'agent découvre que **Angular 22 est encore `next: 22.0.0-next.12`**, pas stable, le 2026-05-11. Per instruction explicite, fallback Angular 21.2.12 stable.

Bump matrix :
- `@angular/*` 19 → 21.2.12
- `typescript` 5.6 → 5.9.3 (forcé peer)
- `@ngx-formly/core` 6.3 → 7.1.0 (major sans casser le code)
- `@swimlane/ngx-graph` 9.0.1 → 12.0.0-alpha.1 (seule release Angular-21-compat — pré-release pinned exact)
- `ngx-echarts` 19 → 21
- `ag-grid-{community,angular}` 32 → 35.2.1
- `@angular/cdk` 21.2.10 + `@angular-devkit/build-angular` 21 + `@angular/compiler-cli` 21

### Impact

- PR #13 ouverte (`b04cdebe`), branche `chore/bump-angular-21`. 4 fichiers touchés (`package.json`, `package-lock.json`, `angular.json` qui gagne `configurations.production/development` + `allowedCommonJsDependencies`, `UPGRADE-NOTES.md` nouveau). **Zéro fichier dans `src/**` modifié**.
- **`tsc --noEmit` 0 erreur** + `ng build` dev + prod SUCCESS.
- **Bundle gzipped 385 KB initial** (vs 327 KB Phase 3 pour Angular 19, mais le 327 était dev) — l'Application Builder + esbuild d'Angular 21 produit un bundle prod plus compact que les estimés.
- 0 vulnérabilité `npm audit`.

### Trade-off

**Accepté** : `@swimlane/ngx-graph@12.0.0-alpha.1` est une pré-release. L'agent a pinné l'exact pour traçabilité ; la PR documente le besoin de re-pinner sur la stable v12 quand SwimLane la cut. Risque maîtrisé car ADR-003 §"Conditions de réexamen" mentionne déjà `rete.js` comme fallback si ngx-graph faille.

**Refusé** : forcer Angular 22 next (`22.0.0-next.12`). Choix de la stabilité production. La règle d'or : *« on ne mainline pas un pre-release sauf nécessité absolue »*. Le bump 22 attendra la stable de mai 2026 ou novembre 2026.

**Refusé** aussi : activer le mode `zoneless` d'Angular 21+. Trop d'inconnus simultanés (bump majeur + paradigm shift de change detection). Phase 5 séparée.

---

## Épilogue de l'épilogue — Ce qui reste après la dernière ligne

À la fermeture de cette session :

- **Repo `fasodigit/faso-analytics-workflow`** existe, `main` compile, mvn et ng buildent. 12 PRs ont été ouvertes au total durant la session ; 8 sont déjà sur `main` ; 4 attendent merge (M migrations, T training, D user-guide, bump Angular).
- **Critères de Go Phase 4** du plan §17 atteints sur papier : 3 dashboards migrés ✓, doc utilisateur FR Typst ✓, formation 2 jours documentée ✓. Reste à exécuter en réel les 14 jours en SHADOW (ce qui est de l'opérationnel, pas du dev).
- **Dette résiduelle tracée** dans les PRs : pivot transform (DataFusion 44+), STRATIFIED/PERIOD/GOLDEN sampling, IntrospectSource RPC, Kafka publisher réel, SHADOW routing, BLUE_GREEN atomic swap Dragonfly, MinIO upload exports, ag-grid Enterprise pivot, CHOROPLETH 13 régions BF réelles, Karma/Vitest suite frontend, AppArmor SELinux equivalent, audit-chain BLAKE3 hashing des events DEPLOY/ROLLBACK, mode zoneless Angular 21.

Cette dette n'est pas une honte. C'est ce qui sépare un MVP livrable d'une utopie qui ne livre rien. Chaque item de la liste est référencé dans un PR ou un ADR, donc *traçable*.

---

## Annexe — Ce que cette session enseigne sur le multi-agents

Six observations tirées des ~18 lancements d'agents en parallèle (dont 14 ont produit du code) :

1. **Périmètres disjoints = zéro conflit Git réel.** Toutes les prédictions de conflit sur `pom.xml`, `package.json`, `app.routes.ts` ont été résolues automatiquement par le 3-way merge GitHub. La discipline préventive (1 dossier par agent) a coûté quelques mots dans les prompts et a évité 100 % des merges manuels.

2. **Les agents reproduisent la défense en profondeur si on les met en parallèle dans le même repo.** Trois agents Phase 3 ont spontanément créé des worktrees `/tmp/faso-*` quand ils ont détecté qu'un autre touchait le HEAD principal. Aucun prompt ne le demandait — c'est l'instinct de l'outil Git appliqué à un travail concurrent.

3. **Les fallbacks documentés en ADR sont utilisés sans hésitation.** Bump Angular : `Angular 22 not stable → fallback Angular 21` per instruction explicite. ADR-003 : `ngx-graph trop limité → rete.js`. Quand un fallback est *nommé* dans la spec, l'agent l'applique sans demander confirmation, ce qui accélère.

4. **L'honnêteté des deferred est précieuse.** Pivot transform `EngineError::Compilation("pivot pending DataFusion 44+")` est plus utile qu'un faux pivot qui passe un test trivial. La transparence sur l'incomplétude est un signal de qualité, pas une faiblesse.

5. **La narration n'est pas un luxe.** Sans ce document, six mois plus tard, personne ne saurait pourquoi `@swimlane/ngx-graph@12.0.0-alpha.1` est pinné sur une alpha, ou pourquoi le sandbox utilise `slirp4netns:allow_host_loopback=false`. La spec donne le quoi ; le commit donne le quand ; ce document donne le pourquoi.

6. **La parallélisation a un plafond utile.** Au-delà de 4-5 agents simultanés sur le même repo, la coordination des worktrees devient pénible. La règle empirique : ne lancer plus d'agents qu'on ne peut nommer leurs périmètres en une phrase chacun.

---

*Fin du roman.*

*Pour commentaires : ouvrir une issue GitHub étiquetée `session:2026-05-11`, ou commenter directement sur la PR de ce document.*
