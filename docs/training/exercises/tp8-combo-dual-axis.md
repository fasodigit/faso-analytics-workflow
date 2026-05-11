# TP8 — COMBO_DUAL_AXIS (bonus avancé)

> **Durée** : 45 minutes.
> **Niveau** : avancé.
> **Module rattaché** : bonus (à proposer aux référents avancés post-J2).

---

## Contexte métier

La Société Nationale de Gestion du Stock de Sécurité (SONAGESS, sub-projet VOUCHERS) souhaite visualiser sur un même graphique :
- Le **stock physique de céréales** (en tonnes, axe gauche, barres).
- Le **prix moyen au marché** (en FCFA/kg, axe droit, courbe).

Cela permet aux décideurs de voir simultanément l'évolution du stock et du prix, et d'identifier les corrélations.

## Données fournies

- **Source** : sub-projet VOUCHERS, table `sonagess_stock_journalier`.
- **Workflow de référence** : `migrations/production/sonagess-stock-prix-v1.0.0.json` *(TODO : nom à fournir si présent ; fallback sur `schemas/examples/06-combo-dual-axis-sonagess.json` en attendant. À ce jour, l'agent M a livré PAGSI / RESUREP / AgriVoucher — SONAGESS pas dans le set initial)*.
- **Référence existante** : `schemas/examples/06-combo-dual-axis-sonagess.json`.
- **Schéma table** :
  - `date_releve` (DATE)
  - `cereale` (VARCHAR : MAIS, MIL, SORGHO, RIZ)
  - `stock_tonnes` (DOUBLE)
  - `prix_fcfa_kg` (DOUBLE)
  - `marche` (VARCHAR : ouaga, bobo, etc.)

## Objectif

Créer une visualisation `COMBO_DUAL_AXIS` pour la céréale MAIS sur les 30 derniers jours, avec :
- Axe gauche : `stock_tonnes` (BAR, SUM, unité `t`).
- Axe droit : `prix_fcfa_kg` (LINE, AVG, unité `FCFA/kg`).
- 2 KPIs : stock total + prix moyen.

## Pas-à-pas suggéré

1. **Source YugabyteDB** sur la table `sonagess_stock_journalier`.
2. **Filter** : `cereale = 'MAIS' AND date_releve >= date_sub(now(), interval 30 day)`.
3. **Aggregate** : `groupBy = ["date_releve"]`, agrégations :
   - `SUM(stock_tonnes) AS stock_tonnes_jour`
   - `AVG(prix_fcfa_kg) AS prix_moyen_jour`
4. **2 KPIs** :
   - `kpi_stock_actuel` : SUM(stock_tonnes), polarité `more_better`, format `t`.
   - `kpi_prix_moyen` : AVG(prix_fcfa_kg), polarité `less_better`, format `FCFA/kg`.
5. **Visualisation COMBO_DUAL_AXIS** :
   - `encoding.xField = date_releve`
   - `encoding.leftAxis` :
     - `seriesType = BAR`
     - `field = stock_tonnes_jour`
     - `aggregation = SUM`
     - `scale = LINEAR`
     - `format = { pattern: "#,##0", unit: "t", decimals: 0 }`
   - `encoding.rightAxis` :
     - `seriesType = LINE`
     - `field = prix_moyen_jour`
     - `aggregation = AVG`
     - `scale = LINEAR`
     - `format = { pattern: "#,##0", unit: "FCFA/kg", decimals: 0 }`
6. **Output dashboard** `sonagess_stock_prix`, refresh 3600 s.

## Critères de validation

- [ ] La viz est de type `COMBO_DUAL_AXIS`.
- [ ] Les 2 axes (left + right) sont présents et correctement configurés.
- [ ] `seriesType` est différent entre les 2 axes (BAR + LINE).
- [ ] Les 2 unités sont distinctes (`t` et `FCFA/kg`).
- [ ] Validation Schema OK : si tu retires `leftAxis` ou `rightAxis`, le schéma refuse.

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

Voir `schemas/examples/06-combo-dual-axis-sonagess.json` pour la référence.

```json
{
  "apiVersion": "analytics.faso/v1",
  "kind": "AnalyticsWorkflow",
  "metadata": {
    "name": "sonagess-stock-prix-mais-30j",
    "subProject": "VOUCHERS",
    "semver": "1.0.0-draft.1",
    "owner": "user:fasodigitalisation@gmail.com"
  },
  "spec": {
    "source": {
      "type": "yugabyte",
      "connectionRef": "secret/data/analytics/connectors/VOUCHERS/sonagess-prod",
      "schema": "voucher_schema",
      "table": "sonagess_stock_journalier"
    },
    "pipeline": [
      { "id": "filter_mais_30j", "kind": "filter",
        "expression": "cereale = 'MAIS' AND date_releve >= date_sub(now(), interval 30 day)" },
      {
        "id": "agg_par_jour",
        "kind": "aggregate",
        "groupBy": ["date_releve"],
        "aggregations": [
          { "alias": "stock_tonnes_jour", "function": "SUM", "field": "stock_tonnes" },
          { "alias": "prix_moyen_jour", "function": "AVG", "field": "prix_fcfa_kg" }
        ]
      }
    ],
    "kpis": [
      { "id": "kpi_stock_actuel", "label": "Stock actuel MAIS",
        "expression": "SUM(stock_tonnes_jour)", "polarity": "more_better",
        "format": { "pattern": "#,##0", "unit": "t", "decimals": 0 } },
      { "id": "kpi_prix_moyen", "label": "Prix moyen MAIS",
        "expression": "AVG(prix_moyen_jour)", "polarity": "less_better",
        "format": { "pattern": "#,##0", "unit": "FCFA/kg", "decimals": 0 } }
    ],
    "visualizations": [
      {
        "id": "viz_combo_stock_prix",
        "type": "COMBO_DUAL_AXIS",
        "title": "MAIS — Stock (t) et Prix (FCFA/kg)",
        "subtitle": "30 derniers jours",
        "encoding": {
          "xField": "date_releve",
          "leftAxis": {
            "seriesType": "BAR", "field": "stock_tonnes_jour", "aggregation": "SUM",
            "scale": "LINEAR", "format": { "pattern": "#,##0", "unit": "t", "decimals": 0 }
          },
          "rightAxis": {
            "seriesType": "LINE", "field": "prix_moyen_jour", "aggregation": "AVG",
            "scale": "LINEAR", "format": { "pattern": "#,##0", "unit": "FCFA/kg", "decimals": 0 }
          }
        },
        "style": { "showLegend": true }
      }
    ],
    "outputs": [
      { "kind": "dashboard", "dashboardCode": "sonagess_stock_prix", "refreshSec": 3600 }
    ]
  }
}
```

</details>
