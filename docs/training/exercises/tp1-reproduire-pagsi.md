# TP1 — Reproduire le workflow PAGSI (J1 M4)

> **Durée** : 60 minutes.
> **Niveau** : débutant.
> **Module rattaché** : Jour 1 — Module M4.

---

## Contexte métier

La Direction Générale Études Aménagement Prévision (DGEAP) suit chaque mois les volumes de céréales distribués dans le cadre du programme PAGSI (Plan d'Appui à la Gestion de la Sécurité des Intrants). Le tableau de bord cible présente les volumes par région pour la campagne en cours, sous forme de barres verticales.

## Données fournies

- **Source** : sub-projet VOUCHERS, schéma `voucher_schema`, table `distribution`.
- **Fichier de référence** : `migrations/production/pagsi-volumes-region-cereale-v1.0.0.json` *(nom validé avec l'agent M ; à re-vérifier en cas de renommage)*.
- **Sample** : 10 000 lignes représentatives sont disponibles en pré-prod, accessibles via le `connectionRef` `secret/data/analytics/connectors/VOUCHERS/pagsi-prod`.
- **Schéma de la table** :
  - `id` (UUID)
  - `region` (VARCHAR)
  - `province` (VARCHAR)
  - `commune` (VARCHAR)
  - `type_cereale` (VARCHAR : MAIS, MIL, SORGHO, RIZ, etc.)
  - `volume_kg` (DOUBLE)
  - `campagne` (VARCHAR : '2024-2025', '2025-2026')
  - `date_distribution` (DATE)

## Objectif

Reproduire à l'identique le workflow existant `01-bar-vertical-pagsi-region.json` :
- 1 source YugabyteDB.
- 1 transformation `filter` : campagne 2025-2026.
- 1 transformation `aggregate` : groupBy `region`, SUM `volume_kg`.
- 1 transformation `computed` : conversion kg → tonnes.
- 1 KPI : volume total distribué.
- 1 visualisation BAR_VERTICAL.
- 1 output dashboard.

## Pas-à-pas suggéré

1. **Ouvrir** l'interface analytics-frontend, cliquer sur « Nouveau Workflow ».
2. **Configurer la métadonnée** : `name = pagsi-volumes-region`, `subProject = VOUCHERS`, `semver = 1.0.0-draft.1`, `owner` = ton email.
3. **Glisser une source YugabyteDB** sur le canvas, panneau de droite :
   - `type = yugabyte`
   - `connectionRef = secret/data/analytics/connectors/VOUCHERS/pagsi-prod`
   - `schema = voucher_schema`, `table = distribution`
4. **Glisser une transformation `filter`**, la connecter à la source :
   - `id = filter_periode`, `label = Campagne 2025-2026`
   - `expression = campagne = '2025-2026'`
5. **Glisser une transformation `aggregate`**, la connecter au filter :
   - `id = agg_region`, `label = Volumes par région`
   - `groupBy = ["region"]`
   - `aggregations = [{ alias: "volume_total_kg", function: "SUM", field: "volume_kg" }]`
6. **Glisser une transformation `computed`**, la connecter à aggregate :
   - `id = computed_tonnes`, `label = Conversion kg → tonnes`
   - `alias = volume_tonnes`, `expression = volume_total_kg / 1000.0`, `type = DOUBLE`
7. **Glisser un KPI** :
   - `id = kpi_volume_total`, `label = Volume total distribué`
   - `expression = SUM(volume_tonnes)`
   - `format = { pattern: "#,##0.0", unit: "t", decimals: 1 }`
   - `polarity = more_better`
8. **Glisser une visualisation BAR_VERTICAL** :
   - `id = viz_bar_vertical_region`
   - `title = Volumes distribués par région`
   - `subtitle = Campagne 2025-2026 — tonnes`
   - `encoding = { xField: "region", yField: "volume_tonnes" }`
9. **Glisser un output dashboard** :
   - `kind = dashboard`, `dashboardCode = pagsi_region`, `refreshSec = 1800`
10. **Sauvegarder + valider** : Ctrl+S puis cliquer « Valider Schéma ». Le bandeau bas doit afficher « JSON Schema OK ».

## Critères de validation

- [ ] Le workflow passe la validation `workflow-v1.json` sans erreur.
- [ ] Le DAG comporte au moins 6 nœuds (source + 3 transformations + KPI + viz + output).
- [ ] La polarité du KPI est `more_better`.
- [ ] L'unité affichée est `t` (tonnes), pas `kg`.
- [ ] Le `refreshSec` est ≥ 60 (recommandé 1800 = 30 min).

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

```json
{
  "apiVersion": "analytics.faso/v1",
  "kind": "AnalyticsWorkflow",
  "metadata": {
    "name": "pagsi-volumes-region",
    "subProject": "VOUCHERS",
    "semver": "1.0.0-draft.1",
    "owner": "user:fasodigitalisation@gmail.com",
    "isCritical": false,
    "description": "TP1 — Volumes PAGSI distribués par région, campagne 2025-2026"
  },
  "spec": {
    "source": {
      "type": "yugabyte",
      "connectionRef": "secret/data/analytics/connectors/VOUCHERS/pagsi-prod",
      "schema": "voucher_schema",
      "table": "distribution"
    },
    "pipeline": [
      {
        "id": "filter_periode",
        "kind": "filter",
        "label": "Campagne 2025-2026",
        "expression": "campagne = '2025-2026'"
      },
      {
        "id": "agg_region",
        "kind": "aggregate",
        "label": "Volumes par région",
        "groupBy": ["region"],
        "aggregations": [
          { "alias": "volume_total_kg", "function": "SUM", "field": "volume_kg" }
        ]
      },
      {
        "id": "computed_tonnes",
        "kind": "computed",
        "label": "Conversion kg → tonnes",
        "alias": "volume_tonnes",
        "expression": "volume_total_kg / 1000.0",
        "type": "DOUBLE"
      }
    ],
    "kpis": [
      {
        "id": "kpi_volume_total",
        "label": "Volume total distribué",
        "expression": "SUM(volume_tonnes)",
        "format": { "pattern": "#,##0.0", "unit": "t", "decimals": 1 },
        "polarity": "more_better"
      }
    ],
    "visualizations": [
      {
        "id": "viz_bar_vertical_region",
        "type": "BAR_VERTICAL",
        "title": "Volumes distribués par région",
        "subtitle": "Campagne 2025-2026 — tonnes",
        "encoding": {
          "xField": "region",
          "yField": "volume_tonnes"
        },
        "style": { "palette": "categorical_set1", "showLegend": false, "showLabels": true }
      }
    ],
    "outputs": [
      { "kind": "dashboard", "dashboardCode": "pagsi_region", "refreshSec": 1800 }
    ]
  }
}
```

</details>
