# TP2 — Sources et transformations (J1 M5)

> **Durée** : 60 minutes.
> **Niveau** : débutant +.
> **Module rattaché** : Jour 1 — Module M5.

---

## Contexte métier

La Caisse d'Assurance Maladie Élémentaire (CAMEG, sub-projet SOGESY) souhaite croiser la table des **commandes** d'un dépôt avec la table des **médicaments référencés**, puis filtrer les commandes en rupture de stock pour produire un état hebdomadaire.

## Données fournies

- **Source 1** : sub-projet SOGESY, table `sogesy_schema.commandes`.
  - Champs : `id_commande`, `id_medicament`, `quantite_commandee`, `quantite_recue`, `date_commande`, `depot_id`, `statut`.
- **Source 2** : sub-projet SOGESY, table `sogesy_schema.referentiel_medicaments`.
  - Champs : `id_medicament`, `nom_medicament`, `classe_atc`, `unite`, `prix_unitaire_fcfa`.
- **Fichier de référence** : `migrations/production/sogesy-commandes-en-rupture-v1.0.0.json` *(TODO : nom à fournir si présent ; fallback sur sample mocké en attendant. À ce jour, l'agent M a livré PAGSI / RESUREP / AgriVoucher — SOGESY n'est pas dans le set initial)*.

## Objectif

Construire un workflow qui :

1. **Joint** les 2 sources sur `id_medicament` (LEFT JOIN).
2. **Filtre** les commandes en rupture : `quantite_recue < quantite_commandee × 0.5`.
3. **Agrège** par `classe_atc` : somme du manque (`quantite_commandee - quantite_recue`).
4. **Ajoute** une colonne calculée : valeur perdue en FCFA = `manque × prix_unitaire_fcfa`.
5. **Produit** un KPI « Valeur perdue totale » et une viz BAR_HORIZONTAL top 10 classes ATC.

## Pas-à-pas suggéré

1. **Glisser source 1** : `commandes` (yugabyte, sub-projet SOGESY, schema `sogesy_schema`, table `commandes`).
2. **Glisser source 2** : `referentiel_medicaments` (idem, table `referentiel_medicaments`).
3. **Glisser transformation `join`** :
   - Type : `LEFT`
   - `leftKey = id_medicament`, `rightKey = id_medicament`
4. **Glisser transformation `filter`** :
   - `expression = quantite_recue < quantite_commandee * 0.5`
5. **Glisser transformation `computed`** :
   - `alias = manque`, `expression = quantite_commandee - quantite_recue`, `type = DOUBLE`
6. **Glisser transformation `computed`** (2e) :
   - `alias = valeur_perdue_fcfa`, `expression = manque * prix_unitaire_fcfa`, `type = DOUBLE`
7. **Glisser transformation `aggregate`** :
   - `groupBy = ["classe_atc"]`
   - `aggregations = [{ alias: "valeur_perdue_classe", function: "SUM", field: "valeur_perdue_fcfa" }]`
8. **Glisser KPI** :
   - `expression = SUM(valeur_perdue_classe)`, format FCFA, polarité `less_better`.
9. **Glisser viz BAR_HORIZONTAL** :
   - `xField = valeur_perdue_classe`, `yField = classe_atc`, top 10.
10. **Sauvegarder + valider**.

## Critères de validation

- [ ] Le `join` a un `leftKey` et un `rightKey` définis.
- [ ] Les 2 `computed` sont dans le bon ordre (manque avant valeur_perdue).
- [ ] La polarité du KPI est `less_better` (perte = mauvais).
- [ ] La viz BAR_HORIZONTAL utilise un tri descendant.
- [ ] L'unité `FCFA` est définie dans le `format`.

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

```json
{
  "apiVersion": "analytics.faso/v1",
  "kind": "AnalyticsWorkflow",
  "metadata": {
    "name": "sogesy-commandes-en-rupture",
    "subProject": "SOGESY",
    "semver": "1.0.0-draft.1",
    "owner": "user:fasodigitalisation@gmail.com",
    "isCritical": true,
    "description": "TP2 — Top 10 classes ATC en rupture, valeur perdue FCFA"
  },
  "spec": {
    "source": {
      "type": "yugabyte",
      "connectionRef": "secret/data/analytics/connectors/SOGESY/cameg-prod",
      "schema": "sogesy_schema",
      "table": "commandes"
    },
    "secondarySources": [
      {
        "id": "ref_medicaments",
        "type": "yugabyte",
        "connectionRef": "secret/data/analytics/connectors/SOGESY/cameg-prod",
        "schema": "sogesy_schema",
        "table": "referentiel_medicaments"
      }
    ],
    "pipeline": [
      {
        "id": "join_medicaments",
        "kind": "join",
        "type": "LEFT",
        "rightSourceRef": "ref_medicaments",
        "leftKey": "id_medicament",
        "rightKey": "id_medicament"
      },
      {
        "id": "filter_rupture",
        "kind": "filter",
        "expression": "quantite_recue < quantite_commandee * 0.5"
      },
      {
        "id": "manque",
        "kind": "computed",
        "alias": "manque",
        "expression": "quantite_commandee - quantite_recue",
        "type": "DOUBLE"
      },
      {
        "id": "valeur_perdue",
        "kind": "computed",
        "alias": "valeur_perdue_fcfa",
        "expression": "manque * prix_unitaire_fcfa",
        "type": "DOUBLE"
      },
      {
        "id": "agg_classe_atc",
        "kind": "aggregate",
        "groupBy": ["classe_atc"],
        "aggregations": [
          { "alias": "valeur_perdue_classe", "function": "SUM", "field": "valeur_perdue_fcfa" }
        ]
      }
    ],
    "kpis": [
      {
        "id": "kpi_valeur_perdue",
        "label": "Valeur totale perdue",
        "expression": "SUM(valeur_perdue_classe)",
        "format": { "pattern": "#,##0", "unit": "FCFA", "decimals": 0 },
        "polarity": "less_better",
        "critical": true
      }
    ],
    "visualizations": [
      {
        "id": "viz_bar_horizontal_top10",
        "type": "BAR_HORIZONTAL",
        "title": "Top 10 classes ATC en rupture",
        "encoding": { "xField": "valeur_perdue_classe", "yField": "classe_atc" },
        "style": { "showLegend": false, "showLabels": true, "topN": 10, "sort": "desc" }
      }
    ],
    "outputs": [
      { "kind": "dashboard", "dashboardCode": "sogesy_ruptures", "refreshSec": 3600 }
    ]
  }
}
```

</details>
