# Runbook — Migration des dashboards legacy vers FASO-ANALYTICS-WORKFLOW

> **Livrable L11 du plan ultraplan §16** — opérationnalise le critère Go Phase 4 §17 :
> *« 3 dashboards migrés sans régression analytique pendant 14 jours en shadow mode »*.

Ce runbook décrit la séquence de cutover **par sub-projet** (un dashboard à la fois) pour
passer d'un tableau de bord legacy (Metabase / SurveyMonkey / KoBo) à un workflow analytique
versionné FASO-ANALYTICS-WORKFLOW conforme à ADR-002 / ADR-006.

---

## 1. Pré-requis (gate d'entrée)

Avant de lancer la séquence, **vérifier impérativement** :

- [ ] Le workflow JSON est valide contre `schemas/workflow-v1.json` (cf. `migrations/README.md` §Quick validation).
- [ ] Le workflow est enregistré côté `analytics-api` (POST `/v1/workflows`) → un `workflowId` + une `versionId` (v1) sont obtenus.
- [ ] Une **simulation < 7 jours** a été exécutée sur cette `versionId` avec un échantillon représentatif (cf. ADR-001 + §7 ultraplan).
- [ ] Le workflow présente une `driftPolicy` **explicite** (pas seulement `defaults`).
- [ ] Pour `isCritical: true` : **2 approbations distinctes** (`actor_subject` Kratos différents) ont été enregistrées via le mécanisme 4-eyes (Q-Policy §13 ultraplan).
- [ ] Aucun drift bloquant détecté lors de la dernière exécution de `DetectSchemaDriftUseCase` sur la source.
- [ ] Le `connectionRef` Vault est résolvable (test `vault read <path>` côté ops).
- [ ] Le legacy dashboard reste **opérationnel** pendant toute la durée du cutover — c'est le baseline de comparaison.

> Si **un seul** de ces points n'est pas vert, **STOP** : corriger d'abord. Le `DeploymentPolicy`
> côté domaine *refuse* le déploiement (HTTP 409 `deployment_refused` + `reasons[]`).

---

## 2. Étape 1 — Création du workflow

```bash
WF_JSON=migrations/production/pagsi-volumes-region-cereale-v1.0.0.json

curl -X POST https://analytics-api.faso-digitalisation.bf/v1/workflows \
  -H "Authorization: Bearer $KRATOS_SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d @"$WF_JSON"
```

Réponse attendue (HTTP `201 Created`) :

```json
{
  "workflowId": "01HV7T8Q9K2R4M5N6P3X8E1FAA",
  "versionId":  "01HV7T8Q9K2R4M5N6P3X8E1FBB",
  "name": "pagsi-volumes-region-cereale",
  "subProject": "VOUCHERS",
  "semver": "1.0.0",
  "isCritical": true,
  "status": "DRAFT"
}
```

**Capturer** `workflowId` et `versionId` ; ils sont utilisés à toutes les étapes suivantes.

---

## 3. Étape 2 — Première simulation sample 1k

```bash
curl -X POST "https://analytics-api.faso-digitalisation.bf/v1/workflows/$WF_ID/versions/$VER_ID/simulate" \
  -H "Authorization: Bearer $KRATOS_SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "strategy": "RANDOM",
    "size": 1000,
    "seed": 42
  }'
```

Réponse attendue (HTTP `202 Accepted` puis sondage GET) :

```json
{
  "simulationId": "01HV7T8Q9K2R4M5N6P3X8E1FCC",
  "status": "RUNNING",
  "startedAt": "2026-05-11T08:42:13Z"
}
```

Après terminaison :

```bash
curl "$API/v1/simulations/$SIM_ID" | jq '.kpis, .rowCount, .durationMs'
```

**Critère de passage** : la simulation se termine en `SUCCEEDED`, retourne au moins
1 KPI calculé, et `durationMs < 7000` (cf. ADR-001 — p95 simulation).

---

## 4. Étape 3 — Comparaison avec le dashboard legacy

Check-list manuelle, validée par **2 analystes métier référents** du sub-projet
(le terrain « valide à l'œil » comme indiqué §17 ultraplan) :

| # | Vérification | Statut |
|---|---|---|
| 1 | Volume total / cas confirmés : écart < 0.5 % vs legacy sur même fenêtre | ☐ |
| 2 | Top 5 régions (ou communes / espèces) identiques entre legacy et nouveau | ☐ |
| 3 | Cardinalité des dimensions (nb régions, nb céréales, nb espèces) identique | ☐ |
| 4 | KPI critiques (cf. `kpi.critical=true`) affichent la même valeur ± arrondi | ☐ |
| 5 | Polarité couleurs (rouge/vert) cohérente avec interprétation métier attendue | ☐ |

Si **≥ 1 écart non explicable**, ouvrir un ticket bloquant — **ne PAS passer à l'étape 4**.

---

## 5. Étape 4 — Déploiement SHADOW 14 jours

```bash
curl -X POST "$API/v1/workflows/$WF_ID/versions/$VER_ID/deploy" \
  -H "Authorization: Bearer $KRATOS_SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "strategy": "SHADOW",
    "shadow_duration_hours": 336,
    "actor_subject": "user:cabinet-dgaep@gov.bf"
  }'
```

Réponse attendue (HTTP `201`) :

```json
{
  "deploymentId": "01HV7T8Q9K2R4M5N6P3X8E1FDD",
  "strategy": "SHADOW",
  "status": "RUNNING",
  "startedAt": "2026-05-11T09:15:00Z",
  "shadowEndsAt": "2026-05-25T09:15:00Z"
}
```

**Pendant les 14 jours** :

- Les deux versions (legacy + nouveau) **tournent en parallèle**.
- Le nouveau dashboard est **non visible aux utilisateurs finaux** (caché derrière `feature.flag.analytics.shadow=true`).
- Le collecteur shadow enregistre les exécutions des deux côtés et expose les écarts
  via la métrique Prometheus `analytics_shadow_kpi_divergence_pct{workflow,kpi}`.
- Les emails / PDFs programmés sont **dry-run** (générés mais non envoyés ; archive MinIO `faso-analytics-shadow-outputs/`).

**Si erreur HTTP `409 deployment_refused`** :

```json
{
  "error": "deployment_refused",
  "reasons": [
    "Approvals: 1/2",
    "Critical workflow requires SHADOW strategy first"
  ]
}
```

→ corriger les `reasons[]` listées et ré-essayer. Voir `DeploymentPolicy.evaluate()`.

---

## 6. Étape 5 — Critères Go promotion DIRECT (gate de sortie SHADOW)

À J+14, **avant** de promouvoir, vérifier **tous** les critères suivants :

| Critère | Source / commande de vérification | Valeur attendue |
|---|---|---|
| ✅ Aucun KPI critique en breach pendant 14 j | Prometheus : `sum(increase(analytics_kpi_threshold_breached_total{workflow="$WF",severity="critical"}[14d]))` | `0` |
| ✅ Aucun drift bloquant détecté | Prometheus : `sum(increase(analytics_schema_drift_total{workflow="$WF",action="BLOCK"}[14d]))` | `0` |
| ✅ Aucun rollback non planifié | `GET /v1/workflows/$WF_ID/deployments` filter `status=ROLLED_BACK AND reason NOT LIKE 'planned%'` | liste vide |
| ✅ Divergence KPI shadow vs legacy < 1 % p95 | Prometheus : `histogram_quantile(0.95, analytics_shadow_kpi_divergence_pct_bucket{workflow="$WF"})` | `< 1.0` |
| ✅ Validation visuelle finale par 2 analystes métier référents | Wiki interne — fiche `cutover-checklist-$WF.md` signée | 2 ✓ |
| ✅ Audit chain intacte | `audit-verify --workflow-id $WF_ID --since J-14` | `{"ok": true, "chain_intact": true}` |
| ✅ Engine analytics sans OOM / sandbox kill | Prometheus : `analytics_sandbox_oom_killed_total[14d]` | `0` |

**Si ≥ 1 critère ROUGE → décision** :
- Si écart KPI fonctionnel : ouvrir bug, fix workflow JSON, ré-incrémenter semver (`1.0.1`),
  ré-créer une version, recommencer SHADOW depuis l'Étape 1.
- Si problème infra : escalation ops, ne pas promouvoir.

---

## 7. Étape 6 — Promotion DIRECT

```bash
curl -X POST "$API/v1/workflows/$WF_ID/versions/$VER_ID/deploy" \
  -H "Authorization: Bearer $KRATOS_SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "strategy": "DIRECT",
    "actor_subject": "user:directeur-sonagess@gov.bf"
  }'
```

Cet appel :

1. Vérifie à nouveau le `DeploymentPolicy` (4-eyes, simulation, SHADOW déjà passé).
2. Bascule le routage : tous les consommateurs (`dashboardCode`) pointent vers la nouvelle
   version compilée.
3. Marque l'ancien legacy dashboard comme `DEPRECATED` côté registry (header
   `X-Faso-Analytics-Deprecated: true` + bannière UI).
4. Émet un événement `workflow_audit` chaîné BLAKE3 → audit chain.

Réponse attendue (HTTP `201`, `status: COMPLETED` en < 60 s) :

```json
{
  "deploymentId": "01HV7T8Q9K2R4M5N6P3X8E1FEE",
  "strategy": "DIRECT",
  "status": "COMPLETED",
  "completedAt": "2026-05-25T09:45:00Z",
  "deprecatedVersionId": null
}
```

---

## 8. Étape 7 — Bascule des consommateurs

Liste minimale (à adapter par sub-projet) :

- [ ] Mettre à jour les liens dans les rapports mensuels DGAEP / DGSV / DGAEP.
- [ ] Envoyer un email d'annonce interne (template `migration-cutover-announcement.html`) aux mailing-lists métier.
- [ ] Mettre à jour le portail interne `https://portail.faso-digitalisation.bf/dashboards`.
- [ ] Désactiver le scheduler legacy (Metabase pulse / SurveyMonkey scheduled email).
- [ ] Archiver l'export final du legacy dashboard sur MinIO `faso-analytics-legacy-archive/`
      avec horodatage + signature BLAKE3 (preuve forensique en cas de litige métier).
- [ ] Communication portail public (si dashboard exposé externe : DGAEP, agrivoucher.gov.bf).

---

## 9. Procédure de rollback < 60 s

En cas de **régression détectée après promotion DIRECT** :

```bash
curl -X POST "$API/v1/workflows/$WF_ID/versions/$VER_ID/rollback" \
  -H "Authorization: Bearer $KRATOS_SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "deploymentId": "01HV7T8Q9K2R4M5N6P3X8E1FEE",
    "reason": "Régression KPI volume_total détectée : -12 % vs J-1 sans changement réel terrain",
    "actor_subject": "user:cabinet-dgaep@gov.bf"
  }'
```

Cibles SLA :

- Réponse `200 OK` en **< 60 s** (P95 vérifié Phase 3, cf. tests `RollbackWorkflowUseCaseTest`).
- L'ancien deployment passe à `ROLLED_BACK` avec la `reason` mémorisée.
- Le legacy dashboard reprend immédiatement la main (status `ACTIVE`).
- Un événement `workflow_audit` `action=ROLLBACK` est émis, chaîné BLAKE3.

Cf. `docs/runbook/runbook-ops.md` §Rollback express pour la procédure de vérification.

---

## 10. Calendrier de bascule recommandé

**Cadence cible** : 1 sub-projet par 2 semaines (1 SHADOW + 1 promotion + 1 vague d'observation).

| Vague | Semaines | Sub-projet | Dashboards | Justification |
|---|---|---|---|---|
| 1 | S1-S2 | `VOUCHERS` | PAGSI + AgriVoucher | **Priorité 1** : sécurité alimentaire 2026, analystes déjà formés, sources Yugabyte stables, modèles testés Phase 2 (démo 2.1) |
| 2 | S3-S4 | `HOSPITAL` | RESUREP-26 | Critique santé animale + conversion KoBo→SurveyMonkey à valider en production (transform `recode` neuve) |
| 3 | S5-S6 | `ETAT_CIVIL` | Volumétrie actes (naissance/décès) | Volumétrie élevée, adoption moyenne — gestion charge OLTP en read-replica à valider |
| 4 | S7-S8 | `SOGESY` | Compteurs intégration | Faible criticité métier, sert de banc d'essai pour Redpanda/Dragonfly sources |
| 5 | S9-S10 | `FASO_KALAN` | Suivi appels apprenants | Source mixte (KoBo + uploads CSV), bon cas de test multi-source |
| 6 | S11-S12 | `ALT_MISSION` | Suivi maintenance véhicules | Faible volume, peu critique — buffer si une vague précédente glisse |
| 7 | S13-S14 | `E_SCHOOL` | Effectifs / présence | Saisonnier (rentrée), bascule planifiée hors période scolaire |
| 8 | S15-S16 | `E_TICKET` | Volumétrie billetterie | Dernier en raison du volume Redpanda streaming — tester en dernier sur retours d'expérience accumulés |

**Pourquoi cet ordre ?**

1. **Criticité métier** d'abord (VOUCHERS = sécurité alimentaire, HOSPITAL = santé animale).
2. **Maturité d'adoption** : analystes VOUCHERS / HOSPITAL ont la culture dashboards la plus
   mature (Metabase depuis 2024), réduit le risque d'incompréhension métier en SHADOW.
3. **Risque technique croissant** : les sources Yugabyte read-replica (VOUCHERS, HOSPITAL) sont
   les plus stables → on **garde** les sources Redpanda/Dragonfly streaming (E_TICKET) pour la
   fin, quand l'engine et les opérationnels auront 14 semaines de retours.
4. **Saisonnalité** : E_SCHOOL bascule hors rentrée pour éviter une bascule au pic de charge.
5. **Tampon** : ALT_MISSION en milieu de calendrier sert de **buffer** si une vague précédente
   dépasse les 2 semaines (faible criticité = on peut décaler sans pénalité).

> **Total 16 semaines = ~4 mois** pour migrer les 8 sub-projets cibles, en cohérence avec
> l'estimation §16 ultraplan « ~22 semaines·personne ».

---

## 11. Annexes

### A. Commandes utiles

```bash
# Lister tous les déploiements d'un workflow
curl "$API/v1/workflows/$WF_ID/deployments" | jq

# Voir l'audit chain
curl "$API/v1/workflows/$WF_ID/audit" | jq '.items[] | {ts, action, actor}'

# Vérifier offline la chaîne d'audit
podman exec -t analytics-api java -jar /opt/analytics/audit-verify.jar \
  --workflow-id $WF_ID --since 2026-05-11
```

### B. Liens

- Plan ultraplan : `docs/PLAN-ULTRAPLAN.md`
- Runbook ops généraliste : `docs/runbook/runbook-ops.md`
- Runbook observabilité : `docs/runbook/observability.md`
- ADR-002 (format workflow) : `docs/adr/ADR-002-workflow-format-json-schema-draft07.md`
- ADR-005 (audit BLAKE3) : `docs/adr/ADR-005-audit-chain-blake3.md`
- ADR-006 (semver contraint) : `docs/adr/ADR-006-semver-contrained.md`
- Workflows migrés : `migrations/production/`
- Index des migrations : `migrations/README.md`

### C. Contacts d'escalade

| Incident | Premier contact | Escalade |
|---|---|---|
| Régression KPI critique post-DIRECT | Analyste métier référent | Direction sub-projet + ops |
| Rollback nécessaire | Ops on-call analytics | CTO délégué |
| Source Vault inaccessible | SRE Vault | Sécurité infra |
| Approbation 4-eyes bloquée | Owner workflow | Cabinet sub-projet |
