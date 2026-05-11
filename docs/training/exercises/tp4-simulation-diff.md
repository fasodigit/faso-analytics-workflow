# TP4 — Simulation et diff vs DEPLOYED (J2 M9)

> **Durée** : 60 minutes.
> **Niveau** : intermédiaire.
> **Module rattaché** : Jour 2 — Module M9.

---

## Contexte métier

Tu disposes d'un workflow déployé (`pagsi-volumes-region` version `1.0.0`) qui produit le tableau des volumes mensuels distribués. Le métier demande une variante : ajouter une colonne « variation vs mois précédent » en pourcentage. Tu vas créer une **version 1.1.0-draft.1**, lancer une simulation, et comparer au diff avec la version déployée.

## Données fournies

- **Workflow existant** : `migrations/production/pagsi-volumes-region-cereale-v1.0.0.json` *(nom validé avec l'agent M ; à re-vérifier en cas de renommage)*.
- **Données** : sample STRATIFIED de 5 000 lignes pré-générées sur le sub-projet VOUCHERS.

## Objectif

1. Cloner la version 1.0.0 en version 1.1.0-draft.1.
2. Ajouter une transformation `window` pour calculer la variation mois N - mois N-1.
3. Lancer une simulation `STRATIFIED` taille 5 000.
4. Lire et interpréter le diff vs la version `DEPLOYED`.
5. Conclure : la variante est-elle prête pour déploiement SHADOW ?

## Pas-à-pas suggéré

1. Depuis la console : ouvrir le workflow `pagsi-volumes-region`, bouton « Cloner pour version mineure ».
2. Le `semver` est automatiquement bumpé en `1.1.0-draft.1`.
3. Ajouter une transformation `window` après l'aggregate :
   - `partitionBy = ["region"]`
   - `orderBy = ["mois"]`
   - `expression = (volume_tonnes - LAG(volume_tonnes, 1) OVER w) / LAG(volume_tonnes, 1) OVER w * 100`
   - `alias = variation_pct`
4. Ajouter un KPI `kpi_variation_moyenne` avec `polarity = neutral` (on ne juge pas si la variation positive ou négative est « bonne »).
5. Lancer simulation : bouton « Simuler », sample `STRATIFIED`, taille 5000.
6. Attendre le rapport (≤ 30 s).
7. Ouvrir l'onglet « Diff vs DEPLOYED » :
   - Vérifier que les 13 régions et 12 mois sont représentés.
   - Vérifier que les valeurs de `volume_tonnes` sont **identiques** (tolerance 0.01 %).
   - La colonne `variation_pct` n'existait pas dans 1.0.0 → marquée « NOUVEAU » en bleu.
8. Conclusion : valide pour déploiement SHADOW. Refus DIRECT car ajout de colonne.

## Critères de validation

- [ ] Le workflow cloné est en semver `1.1.0-draft.1`.
- [ ] La transformation `window` utilise `LAG` correctement.
- [ ] La simulation s'achève en < 30 s sur 5 000 lignes.
- [ ] Le diff montre 0 différence sur les valeurs anciennes.
- [ ] La colonne `variation_pct` est bien identifiée comme « NOUVEAU ».

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

```json
{
  "metadata": {
    "name": "pagsi-volumes-region",
    "semver": "1.1.0-draft.1",
    "subProject": "VOUCHERS"
  },
  "spec": {
    "pipeline": [
      { "id": "filter_periode", "kind": "filter", "expression": "campagne = '2025-2026'" },
      {
        "id": "agg_region_mois",
        "kind": "aggregate",
        "groupBy": ["region", "mois"],
        "aggregations": [{ "alias": "volume_tonnes", "function": "SUM", "field": "volume_kg" }]
      },
      {
        "id": "window_variation",
        "kind": "window",
        "partitionBy": ["region"],
        "orderBy": ["mois"],
        "alias": "variation_pct",
        "expression": "(volume_tonnes - LAG(volume_tonnes, 1) OVER w) / LAG(volume_tonnes, 1) OVER w * 100"
      }
    ],
    "kpis": [
      { "id": "kpi_variation_moy", "label": "Variation moyenne", "expression": "AVG(variation_pct)",
        "format": { "pattern": "0.0", "unit": "%", "decimals": 1 }, "polarity": "neutral" }
    ]
  }
}
```

**Lecture du diff** :

```
Colonne          Anciennes valeurs   Nouvelles valeurs   Statut
region           13 distinct         13 distinct         IDENTIQUE
mois             12 distinct         12 distinct         IDENTIQUE
volume_tonnes    [moyennes égales]   [moyennes égales]   IDENTIQUE (tol 0.01%)
variation_pct    [absent]            [11 valeurs / mois] NOUVEAU
```

Conclusion : workflow rétro-compatible. Sûr pour SHADOW.

</details>
