# TP5 — Schema drift, détection et résolution interactive (J2 M11)

> **Durée** : 60 minutes.
> **Niveau** : intermédiaire.
> **Module rattaché** : Jour 2 — Module M11.

---

## Contexte métier

L'équipe HOSPITAL a modifié son schéma de table `signalements` :

- Ajout d'un champ `id_certificat` (UUID, nouveau).
- Renommage de `region` en `nom_region`.
- Changement de type sur `cas_confirmes` : `INTEGER` → `BIGINT`.

Le workflow `resurep-26-cas-especes` déployé tire sur cette table. Sans intervention, il va **casser** au prochain rafraîchissement.

## Données fournies

- **Workflow existant** : `migrations/production/resurep-surveillance-26-v1.0.0.json` *(nom validé avec l'agent M ; à re-vérifier en cas de renommage)*.
- **Sample drift** : `analytics-api detect-drift --workflow resurep-26-cas-especes` retourne un rapport JSON.

## Objectif

1. Lancer la détection de drift.
2. Lire le rapport JSON.
3. Résoudre chaque entrée interactivement.
4. Re-simuler le workflow modifié.
5. Confirmer que la simulation passe sans erreur.

## Pas-à-pas suggéré

1. Ouvrir un terminal pré-prod, lancer :
   ```bash
   analytics-cli drift detect --workflow resurep-26-cas-especes --sub-project HOSPITAL
   ```
2. Lire le rapport (extrait attendu) :
   ```json
   {
     "workflowId": "resurep-26-cas-especes",
     "detectedAt": "2026-05-11T14:00:00Z",
     "driftEntries": [
       {
         "kind": "NEW_FIELD",
         "field": "id_certificat",
         "fieldType": "UUID",
         "policyAction": "REQUIRE_MAPPING",
         "suggestedResolution": null
       },
       {
         "kind": "RENAMED_FIELD",
         "oldField": "region",
         "newField": "nom_region",
         "levenshteinDistance": 4,
         "policyAction": "SUGGEST",
         "suggestedResolution": "MAP_TO_NEW_NAME"
       },
       {
         "kind": "TYPE_CHANGE",
         "field": "cas_confirmes",
         "oldType": "INTEGER",
         "newType": "BIGINT",
         "policyAction": "REQUIRE_CAST",
         "suggestedResolution": "CAST_AS_INTEGER"
       }
     ]
   }
   ```
3. Ouvrir l'UI de résolution interactive : Menu Workflow → onglet « Drift ».
4. Pour chaque entrée, choisir une action :
   - `id_certificat` (NEW_FIELD) → **Ignore** (ce champ n'est pas utilisé dans le workflow).
   - `region → nom_region` (RENAMED_FIELD) → **Map** : confirmer que le champ utilisé partout devient `nom_region`.
   - `cas_confirmes` (TYPE_CHANGE) → **Cast** : `CAST(cas_confirmes AS INTEGER)` (les valeurs restent dans la plage INT32 pour ce sous-projet).
5. Cliquer « Appliquer & Re-simuler ».
6. Lire le résultat : `Simulation OK — 0 erreur — 234 lignes traitées`.
7. Sauvegarder la version `1.0.1-drift.1`.

## Critères de validation

- [ ] Les 3 entrées de drift sont résolues (Ignore, Map, Cast).
- [ ] La re-simulation passe sans erreur.
- [ ] Le semver est bumpé en `1.0.1-drift.1`.
- [ ] Le mapping `region → nom_region` est appliqué dans toutes les transformations (filter, aggregate, viz).
- [ ] L'événement d'audit `DRIFT_RESOLVED` est visible dans la chaîne BLAKE3.

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

**Fichier de résolution** (envoyé en POST à `analytics-api`) :

```json
{
  "workflowId": "resurep-26-cas-especes",
  "resolutions": [
    {
      "driftKind": "NEW_FIELD",
      "field": "id_certificat",
      "action": "IGNORE",
      "reason": "Champ non utilisé par ce workflow"
    },
    {
      "driftKind": "RENAMED_FIELD",
      "oldField": "region",
      "newField": "nom_region",
      "action": "MAP_TO_NEW_NAME",
      "applyToTransformations": ["filter_validated", "agg_par_espece", "viz_half_donut_especes"]
    },
    {
      "driftKind": "TYPE_CHANGE",
      "field": "cas_confirmes",
      "action": "CAST",
      "castExpression": "CAST(cas_confirmes AS INTEGER)",
      "reason": "Plage de valeurs reste dans INT32 pour HOSPITAL"
    }
  ],
  "newSemver": "1.0.1-drift.1"
}
```

**Résultat** :

```
✓ Resolution appliquée
✓ Re-simulation lancée (sample STRATIFIED 5000)
✓ Simulation OK — 0 erreur — 5000 lignes traitées
✓ Audit event DRIFT_RESOLVED enregistré (BLAKE3 chain hash: a3f8c9...)
```

</details>
