# TP7 — Cas pratique RESUREP-26 (J2 cas pratique 2)

> **Durée** : 60 minutes.
> **Niveau** : intermédiaire.
> **Module rattaché** : Jour 2 — Cas pratique 2.

---

## Contexte métier

La Direction Générale des Services Vétérinaires (DGSV) du Burkina Faso utilise depuis 2024 le formulaire SurveyMonkey « RESUREP-26 » pour collecter chaque semaine les signalements de cas suspects de maladies animales sur le terrain. Le service vétérinaire central a besoin d'un dashboard hebdomadaire de la **répartition des cas confirmés par espèce animale**, avec alerte si le total hebdomadaire dépasse un seuil critique.

## Données fournies

- **Source** : SurveyMonkey webhook `survey:resurep-26`.
- **Workflow de référence** : `migrations/production/resurep-surveillance-26-v1.0.0.json` *(nom validé avec l'agent M ; à re-vérifier en cas de renommage)*.
- **Schéma attendu après mapping SurveyMonkey** :
  - `id_submission` (UUID)
  - `region` (VARCHAR)
  - `espece_animale` (VARCHAR : BOVIN, OVIN, CAPRIN, VOLAILLE, EQUIN, PORCIN, AUTRE)
  - `cas_confirmes` (INTEGER)
  - `date_observation` (DATE)
  - `validation_status_id` (VARCHAR : draft, validated, rejected)

## Objectif

Construire un workflow avec :
- Source `surveymonkey`.
- 2 filters : `validation_status_id = 'validated'` ET `date_observation BETWEEN début_semaine ET maintenant`.
- 1 aggregate par `espece_animale`.
- 1 KPI critique (less_better) avec alerte temps réel.
- 1 viz HALF_DONUT.
- 2 outputs : dashboard + email weekly summary.

## Pas-à-pas suggéré

1. **Glisser source SurveyMonkey** :
   - `type = surveymonkey`
   - `connectionRef = secret/data/analytics/connectors/HOSPITAL/resurep-26`
   - `surveyId = survey:resurep-26`
   - `mappingRef = mappings/resurep-26-mapping.yaml`
2. **Filter 1** : `validation_status_id = 'validated'`.
3. **Filter 2** : `date_observation BETWEEN date_trunc('week', now()) AND now()`.
4. **Aggregate** : `groupBy = ["espece_animale"]`, `SUM(cas_confirmes)`.
5. **KPI** critique :
   - `label = Cas confirmés cette semaine`
   - `polarity = less_better`
   - `thresholds = { warningPct: 110, criticalPct: 150 }`
   - `critical = true`
6. **Viz HALF_DONUT** :
   - `encoding.category = espece_animale`, `encoding.value = cas_confirmes`
   - Palette divergente (rouge si élevé).
7. **Outputs** :
   - Dashboard `resurep_especes`, refresh 300 s.
   - Email weekly : `to = [dgsv-alertes@gov.bf]`, `template = resurep-weekly-summary`, `cron = 0 0 8 ? * MON`, `timezone = Africa/Ouagadougou`.
8. **driftPolicy** stricte : `REQUIRE_MAPPING`, `BLOCK`, `REQUIRE_CAST`, `SUGGEST`.

## Critères de validation

- [ ] La source est bien de type `surveymonkey`.
- [ ] Les 2 filters s'enchaînent dans l'ordre logique (validation puis période).
- [ ] La polarité du KPI est `less_better`.
- [ ] Le KPI est `critical = true`.
- [ ] La viz est HALF_DONUT (pas DONUT).
- [ ] Les 2 outputs sont définis (dashboard + email).
- [ ] Le cron envoie chaque lundi 8h fuseau Ouagadougou.

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

Voir `schemas/examples/05-half-donut-resurep-especes.json` pour la référence.

```json
{
  "apiVersion": "analytics.faso/v1",
  "kind": "AnalyticsWorkflow",
  "metadata": {
    "name": "resurep-26-cas-especes",
    "subProject": "HOSPITAL",
    "semver": "1.0.0-draft.1",
    "isCritical": true,
    "owner": "user:fasodigitalisation@gmail.com",
    "labels": { "thematique": "sante-animale" }
  },
  "spec": {
    "source": {
      "type": "surveymonkey",
      "connectionRef": "secret/data/analytics/connectors/HOSPITAL/resurep-26",
      "surveyId": "survey:resurep-26",
      "mappingRef": "mappings/resurep-26-mapping.yaml"
    },
    "pipeline": [
      { "id": "filter_validated", "kind": "filter", "expression": "validation_status_id = 'validated'" },
      { "id": "filter_periode", "kind": "filter", "expression": "date_observation BETWEEN date_trunc('week', now()) AND now()" },
      {
        "id": "agg_espece",
        "kind": "aggregate",
        "groupBy": ["espece_animale"],
        "aggregations": [{ "alias": "cas_confirmes", "function": "SUM", "field": "cas_confirmes" }]
      }
    ],
    "kpis": [
      {
        "id": "kpi_cas_total_semaine",
        "label": "Cas confirmés cette semaine",
        "expression": "SUM(cas_confirmes)",
        "format": { "pattern": "#,##0", "decimals": 0 },
        "polarity": "less_better",
        "thresholds": { "warningPct": 110.0, "criticalPct": 150.0 },
        "critical": true
      }
    ],
    "visualizations": [
      {
        "id": "viz_half_donut_especes",
        "type": "HALF_DONUT",
        "title": "Cas confirmés par espèce animale",
        "subtitle": "Semaine épidémiologique courante",
        "encoding": { "category": "espece_animale", "value": "cas_confirmes" },
        "style": { "palette": "diverging_rdylgn", "showLegend": true, "showLabels": true }
      }
    ],
    "outputs": [
      { "kind": "dashboard", "dashboardCode": "resurep_especes", "refreshSec": 300 },
      {
        "kind": "email",
        "to": ["dgsv-alertes@gov.bf"],
        "template": "resurep-weekly-summary",
        "schedule": { "cron": "0 0 8 ? * MON", "timezone": "Africa/Ouagadougou", "trigger": "cron" }
      }
    ],
    "driftPolicy": {
      "onNewField": "REQUIRE_MAPPING",
      "onRemovedField": "BLOCK",
      "onTypeChange": "REQUIRE_CAST",
      "onRenamed": "SUGGEST"
    }
  }
}
```

</details>
