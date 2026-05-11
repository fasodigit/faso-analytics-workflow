# TP3 — KPI, polarité, format (J1 M6)

> **Durée** : 60 minutes.
> **Niveau** : débutant +.
> **Module rattaché** : Jour 1 — Module M6.

---

## Contexte métier

La Direction de la Lutte contre la Maladie (DLM, sub-projet HOSPITAL) veut suivre 3 indicateurs simultanément sur un même dashboard :

1. Le **nombre de cas confirmés** de la semaine (alerte si > seuil).
2. La **couverture vaccinale** régionale moyenne (objectif 95 %).
3. Le **délai moyen de validation** d'un signalement (objectif < 48 h).

## Données fournies

- **Source** : sub-projet HOSPITAL, schéma `hospital_schema`, table `signalements`.
  - Champs : `id`, `region`, `date_observation`, `date_validation`, `cas_confirmes`, `est_vaccine`, `population_region`.
- **Fichier de référence** : `migrations/production/resurep-surveillance-26-v1.0.0.json` *(nom validé avec l'agent M ; le KPI critique de RESUREP sert de base pédagogique, à compléter par 2 KPI dérivés en TP)*.

## Objectif

Définir **3 KPIs** distincts avec des polarités, formats et seuils corrects, puis les afficher sous forme de 3 `KPI_TILE` alignés horizontalement.

## Pas-à-pas suggéré

1. **Source + filter `date_observation BETWEEN date_trunc('week', now()) AND now()`**.
2. **KPI 1 — Cas confirmés** :
   - `expression = SUM(cas_confirmes)`
   - `format = { pattern: "#,##0", decimals: 0 }`
   - `polarity = less_better`
   - `target = 0` (objectif zéro cas), `thresholds = { warningPct: 110, criticalPct: 150 }` *(au-dessus de target × pct)*
   - `critical = true`
3. **KPI 2 — Couverture vaccinale** :
   - Calculer la couverture comme `SUM(est_vaccine) / SUM(population_region) * 100`
   - `format = { pattern: "0.0", unit: "%", decimals: 1 }`
   - `polarity = more_better`
   - `target = 95.0`, `thresholds = { warningPct: 95, criticalPct: 85 }` *(en dessous de target × pct)*
4. **KPI 3 — Délai moyen validation** :
   - `expression = AVG(EXTRACT(EPOCH FROM (date_validation - date_observation)) / 3600.0)`
   - `format = { pattern: "0.0", unit: "h", decimals: 1 }`
   - `polarity = less_better`
   - `target = 48.0`, `thresholds = { warningPct: 100, criticalPct: 150 }`
5. **3 visualisations KPI_TILE** alignées (1 par KPI).
6. **1 output dashboard `dlm_suivi_kpis`**.

## Critères de validation

- [ ] 3 KPIs avec 3 polarités cohérentes (`less_better`, `more_better`, `less_better`).
- [ ] Format `%` correctement appliqué au KPI couverture.
- [ ] Le KPI critique est bien `critical = true`.
- [ ] Les 3 viz KPI_TILE référencent chacune un KPI distinct.
- [ ] Le dashboard affiche les 3 tuiles côte à côte.

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

```json
{
  "apiVersion": "analytics.faso/v1",
  "kind": "AnalyticsWorkflow",
  "metadata": {
    "name": "hospital-suivi-kpis-hebdo",
    "subProject": "HOSPITAL",
    "semver": "1.0.0-draft.1",
    "owner": "user:fasodigitalisation@gmail.com",
    "isCritical": true
  },
  "spec": {
    "source": {
      "type": "yugabyte",
      "connectionRef": "secret/data/analytics/connectors/HOSPITAL/dlm-prod",
      "schema": "hospital_schema",
      "table": "signalements"
    },
    "pipeline": [
      {
        "id": "filter_semaine",
        "kind": "filter",
        "expression": "date_observation BETWEEN date_trunc('week', now()) AND now()"
      }
    ],
    "kpis": [
      {
        "id": "kpi_cas_confirmes",
        "label": "Cas confirmés (semaine)",
        "expression": "SUM(cas_confirmes)",
        "format": { "pattern": "#,##0", "decimals": 0 },
        "polarity": "less_better",
        "target": 0,
        "thresholds": { "warningPct": 110.0, "criticalPct": 150.0 },
        "critical": true
      },
      {
        "id": "kpi_couverture_vaccinale",
        "label": "Couverture vaccinale",
        "expression": "SUM(est_vaccine) / SUM(population_region) * 100",
        "format": { "pattern": "0.0", "unit": "%", "decimals": 1 },
        "polarity": "more_better",
        "target": 95.0,
        "thresholds": { "warningPct": 95.0, "criticalPct": 85.0 }
      },
      {
        "id": "kpi_delai_validation",
        "label": "Délai moyen validation",
        "expression": "AVG(EXTRACT(EPOCH FROM (date_validation - date_observation)) / 3600.0)",
        "format": { "pattern": "0.0", "unit": "h", "decimals": 1 },
        "polarity": "less_better",
        "target": 48.0,
        "thresholds": { "warningPct": 100.0, "criticalPct": 150.0 }
      }
    ],
    "visualizations": [
      { "id": "viz_tile_cas", "type": "KPI_TILE", "encoding": { "kpiRef": "kpi_cas_confirmes" } },
      { "id": "viz_tile_vac", "type": "KPI_TILE", "encoding": { "kpiRef": "kpi_couverture_vaccinale" } },
      { "id": "viz_tile_del", "type": "KPI_TILE", "encoding": { "kpiRef": "kpi_delai_validation" } }
    ],
    "outputs": [
      { "kind": "dashboard", "dashboardCode": "dlm_suivi_kpis", "refreshSec": 900 }
    ]
  }
}
```

</details>
