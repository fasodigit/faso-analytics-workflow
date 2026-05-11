# Migrations — Dashboards de référence → FASO-ANALYTICS-WORKFLOW

Ce dossier contient les **définitions de workflow JSON production-grade** pour la migration des
tableaux de bord historiques (Metabase / SurveyMonkey / KoBo) vers la plateforme
FASO-ANALYTICS-WORKFLOW. Il s'agit du livrable **L11** du plan ultraplan §16 et du critère Go
Phase 4 §17 : *« 3 dashboards migrés sans régression analytique pendant 14 jours en shadow mode »*.

## Approche

1. **Référence demo** : `schemas/examples/*.json` (semver `1.0.0-draft.1`, labels demo,
   `isCritical=false` par défaut) — gardés tels quels pour démos & onboarding.
2. **Production-grade** : `migrations/production/*.json` (semver `1.0.0` final, refs Vault
   réelles, `driftPolicy` explicite stricte, owners Kratos d'audit, cibles via `cible_form`,
   sorties PDF/email scheduled).
3. **Procédure cutover** : voir `docs/runbook/migration-dashboards.md` pour la séquence
   complète (création → simulation → SHADOW 14 j → promotion DIRECT → bascule consommateurs).

Le fenêtrage **14 jours en SHADOW** est imposé techniquement par `DeploymentPolicy.defaults(isCritical=true)`
(cf. `services/analytics-api/src/main/java/bf/faso/analytics/domain/policy/DeploymentPolicy.java`)
qui exige `Strategy.SHADOW` avant toute promotion `DIRECT`/`BLUE_GREEN` pour les workflows
critiques + simulation < 7 j + 2 approbations distinctes (4-eyes).

## Index des 3 workflows migrés (Phase 4 task M)

| # | Workflow | Sub-projet | Source legacy | isCritical | Owner subject Kratos |
|---|---|---|---|---|---|
| 1 | `pagsi-volumes-region-cereale-v1.0.0.json` | `VOUCHERS` | Metabase card 124 | true | `user:cabinet-dgaep@gov.bf` |
| 2 | `resurep-surveillance-26-v1.0.0.json` | `HOSPITAL` | SurveyMonkey survey `resurep-26` (ex-KoBo) | true | `user:vet.bankui@dgsv.gov.bf` |
| 3 | `agrivoucher-distribution-commune-cereale-v1.0.0.json` | `VOUCHERS` | Metabase card 158 | false | `user:agrivoucher-direction@gov.bf` |

---

### 1. PAGSI — Volumes distribués par Région × Type de céréale

| Champ | Valeur |
|---|---|
| Migration source | Metabase, card 124 (`label: pagsi-volumes-region-x-cereale`) |
| Workflow JSON | [`production/pagsi-volumes-region-cereale-v1.0.0.json`](./production/pagsi-volumes-region-cereale-v1.0.0.json) |
| Equivalent legacy | KPI « Volume total distribué » + barres groupées Région × Céréale |
| Différences | (a) Source pointe sur **read-replica Yugabyte** au lieu du primary OLTP (`pagsi-prod-readreplica`) pour ne pas pénaliser les transactions de distribution ; (b) **drift detection** activée et stricte (similarité 0.92, BLOCK sur removed/typeChange) ; (c) audit BLAKE3 par hop (cf. ADR-005) ; (d) **polarity-aware coloring** : KPI `kpi_volume_total` en `polarity: more_better`, déficit affiché en rouge ; (e) cible portée par formulaire `cible_form` au lieu d'une valeur statique ; (f) **export PDF mensuel automatique** vers cabinet DGAEP + direction SONAGESS (cron `0 0 7 1 * ?` = 7h le 1er du mois) ; (g) KPI bénéficiaires uniques (`COUNT_DISTINCT`) ajouté pour la fiche cabinet. |
| 14-day shadow window | **REQUIS** (workflow `isCritical: true` → `DeploymentPolicy` impose SHADOW avant DIRECT/BLUE_GREEN). Paramètre attendu : `shadow_duration_hours = 336` (= 14 × 24). |
| Owner & approvals | Owner : `user:cabinet-dgaep@gov.bf`. **4-eyes** = 2 approbations distinctes parmi : Directeur SONAGESS, Directeur PAGSI, Cabinet DGAEP. |
| Sub-projet | `VOUCHERS` |
| Validation schema | `npx ajv-cli validate --strict=false --spec=draft7 -s schemas/workflow-v1.json -d migrations/production/pagsi-volumes-region-cereale-v1.0.0.json` |

---

### 2. RESUREP-26 — Surveillance épidémiologique animale

| Champ | Valeur |
|---|---|
| Migration source | SurveyMonkey survey `resurep-26` (auparavant KoBo, conversion notée au §15 de l'ultraplan) |
| Workflow JSON | [`production/resurep-surveillance-26-v1.0.0.json`](./production/resurep-surveillance-26-v1.0.0.json) |
| Equivalent legacy | Demi-cercle « Cas confirmés par espèce » + alerte hebdomadaire DGSV |
| Différences | (a) **Source change** : `kobo` → `surveymonkey` (le réseau RESUREP a basculé sur SurveyMonkey en 2026) ; (b) **transform `recode_especes`** ajouté pour normaliser les codes 2-3 lettres (`BV`/`OV`/`CP`/`VOL`/`EQ`/`AUT`) en libellés canoniques (`Bovins`/`Ovins`/...) compatibles avec les anciens dashboards KoBo ; (c) `default: "Autres"` pour les codes inconnus → pas de perte ligne ; (d) **drift policy stricte** (BLOCK sur removed AND typeChange) ; (e) **KPI critique** `kpi_cas_total_semaine` avec `polarity: less_better` + thresholds 110/150 % → alerting Prometheus `analytics_kpi_threshold_breached_total{severity=critical}` ; (f) sortie email lundi 8h vers DGSV (Direction Générale des Services Vétérinaires) + copie coordination RESUREP ; (g) KPI complémentaire `kpi_foyers_actifs` (foyers infectieux distincts). |
| 14-day shadow window | **REQUIS** (`isCritical: true`). Pendant le SHADOW, comparaison parallèle avec l'ancien tableau SurveyMonkey hebdomadaire pour s'assurer du zéro divergence sur `cas_confirmes`. |
| Owner & approvals | Owner : `user:vet.bankui@dgsv.gov.bf`. **4-eyes** = 2 approbations distinctes parmi : Vétérinaire en chef DGSV, Coordination RESUREP, Directeur DGSV. |
| Sub-projet | `HOSPITAL` |
| Validation schema | `npx ajv-cli validate --strict=false --spec=draft7 -s schemas/workflow-v1.json -d migrations/production/resurep-surveillance-26-v1.0.0.json` |

---

### 3. AgriVoucher — Distribution par Commune × Type de céréale

| Champ | Valeur |
|---|---|
| Migration source | Metabase, card 158 (`label: agrivoucher-distribution-commune`) |
| Workflow JSON | [`production/agrivoucher-distribution-commune-cereale-v1.0.0.json`](./production/agrivoucher-distribution-commune-cereale-v1.0.0.json) |
| Equivalent legacy | Barres groupées Commune × Céréale + tableau communes (pas de carte BF dans le legacy) |
| Différences | (a) Source pointe sur **read-replica** dédié (`AgriVoucher/distrib-prod-readreplica`) ; (b) **deux visualisations** : `BAR_GROUPED` Commune × Céréale **ET** `CHOROPLETH_BF` au niveau commune (nouveauté vs legacy qui n'avait pas de carte) ; (c) KPI complémentaire `kpi_communes_couvertes` (couverture nationale) ; (d) **driftPolicy default** car workflow `isCritical=false` (acceptation REQUIRE_CAST sur typeChange, moins strict que PAGSI) ; (e) audit & polarity activés comme tous les workflows ; (f) **pas d'export PDF automatique** — consultation à la demande via dashboard. |
| 14-day shadow window | **REQUIS quand-même** (politique GA Phase 4 §17 : tous les dashboards migrés passent par SHADOW avant promotion, indépendamment du flag `isCritical`, pour le critère « 0 régression analytique pendant 14 j »). |
| Owner & approvals | Owner : `user:agrivoucher-direction@gov.bf`. **4-eyes** = 2 approbations distinctes parmi : Direction AgriVoucher, Cabinet DGAEP. |
| Sub-projet | `VOUCHERS` |
| Validation schema | `npx ajv-cli validate --strict=false --spec=draft7 -s schemas/workflow-v1.json -d migrations/production/agrivoucher-distribution-commune-cereale-v1.0.0.json` |

---

## Quick validation

```bash
cd /home/lyna/Documents/faso-analytics-workflow
for f in migrations/production/*.json; do
  echo "=== $f ==="
  npx ajv-cli validate -s schemas/workflow-v1.json -d "$f" && echo "  ✓ valid"
done
```

Si ajv-cli rejette le schéma en mode `strict` (warnings non-bloquants liés au draft-07 + format
`email`), activer la compatibilité :

```bash
for f in migrations/production/*.json; do
  echo "=== $f ==="
  npx ajv-cli validate --strict=false --spec=draft7 \
    -s schemas/workflow-v1.json -d "$f" && echo "  ✓ valid"
done
```

Alternative Python (pas de dépendance Node) :

```bash
python3 -c "
import json, jsonschema
from pathlib import Path
schema = json.loads(Path('schemas/workflow-v1.json').read_text())
for f in sorted(Path('migrations/production').glob('*.json')):
    jsonschema.validate(json.loads(f.read_text()), schema)
    print(f'OK: {f.name}')
"
```

## Couverture par sub-projet

| Sub-projet | Migré Phase 4 (M) | À venir (M+1 / M+2) | Vague de bascule recommandée |
|---|---|---|---|
| `VOUCHERS` | PAGSI + AgriVoucher | — | **Vague 1** (S1-S2) |
| `HOSPITAL` | RESUREP-26 | Vaccination couverture | **Vague 2** (S3-S4) |
| `ETAT_CIVIL` | — | Volumétrie actes (statuts) | Vague 3 (S5-S6) |
| `SOGESY` | — | Compteurs intégration | Vague 4 (S7-S8) |
| `FASO_KALAN` | — | Suivi appels apprenants | Vague 5 (S9-S10) |
| `ALT_MISSION` | — | Suivi maintenance véhicules | Vague 6 (S11-S12) |
| `E_SCHOOL` | — | Effectifs / présence | Vague 7 (S13-S14) |
| `E_TICKET` | — | Volumétrie billetterie | Vague 8 (S15-S16) |

Justification de l'ordre : critère **criticité** (VOUCHERS = sécurité alimentaire 2026, le plus
prioritaire) + **maturité d'adoption** (VOUCHERS et HOSPITAL ont déjà des analystes formés sur
les dashboards historiques). Cf. `docs/runbook/migration-dashboards.md` §10 pour le détail.

## Lien avec la chaîne d'audit

Chaque création / simulation / déploiement / rollback de ces workflows émet un événement
`workflow_audit` chaîné BLAKE3 (cf. ADR-005). Vérification offline possible via :

```bash
podman exec -t analytics-api java -jar /opt/analytics/audit-verify.jar \
  --workflow-name pagsi-volumes-region-cereale --since 2026-05-11
```
