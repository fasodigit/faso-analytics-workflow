# TP6 — Déploiement SHADOW et rollback (J2 M12)

> **Durée** : 60 minutes.
> **Niveau** : intermédiaire+.
> **Module rattaché** : Jour 2 — Module M12.

---

## Contexte métier

Tu disposes d'une variante (`pagsi-volumes-region 1.1.0-draft.1`) issue du TP4. Tu vas la déployer en stratégie SHADOW pour la mesurer pendant la session (5 min en démo, 24-72 h en réel), puis tu déclenches un rollback pour t'entraîner à la procédure d'urgence.

## Données fournies

- **Workflow source** : `migrations/production/pagsi-volumes-region-cereale-v1.1.0-draft.1.json` *(variante du TP4 ; le fichier `-v1.0.0.json` livré par l'agent M sert de base ; le `-v1.1.0-draft.1.json` est créé en TP par cloning)*.
- **Workflow déployé actif** : `pagsi-volumes-region-cereale 1.0.0` (référence).

## Objectif

1. Déployer la 1.1.0-draft.1 en SHADOW.
2. Faire valider par un 2e utilisateur (4-eyes).
3. Lire le tableau de mesure SHADOW pendant 5 min.
4. Déclencher un rollback : la version 1.0.0 redevient la référence active.
5. Lire l'événement d'audit BLAKE3.

## Pas-à-pas suggéré

### Étape 1 — Demander un déploiement SHADOW

1. Ouvrir le workflow `pagsi-volumes-region 1.1.0-draft.1`.
2. Cliquer « Déployer ».
3. Sélectionner stratégie **SHADOW**.
4. Renseigner :
   - `duration = 24h` (ou 5 min pour la démo).
   - `comparisonRef = pagsi-volumes-region 1.0.0`.
   - `metricsToCompare = ["volume_tonnes", "variation_pct"]`.
5. Soumettre.

### Étape 2 — Validation 4-eyes

6. Le workflow passe en statut `PENDING_APPROVAL`.
7. Un 2e utilisateur (binôme en formation) :
   - Reçoit notification dans la console.
   - Ouvre la demande.
   - Compare visuellement diff vs DEPLOYED.
   - Approuve si OK.
8. Le workflow passe en statut `SHADOW_RUNNING`.

### Étape 3 — Lire le tableau de mesure

9. Onglet « SHADOW Metrics » du workflow :
   - Latence moyenne nouvelle version vs ancienne : `120ms` vs `115ms` → tolérable (< 10 %).
   - Différence de résultat sur `volume_tonnes` : 0.00 % (identique).
   - Différence sur `variation_pct` : N/A (champ nouveau).
10. Conclusion attendue (en démo) : OK pour promouvoir en `DEPLOYED`, mais on choisit de rollback pour exercice.

### Étape 4 — Rollback

11. Cliquer « Rollback » sur la démo SHADOW.
12. Confirmer (warning : cette action est immédiate et auditée).
13. Le workflow `1.1.0-draft.1` repasse en statut `SIMULATED` (annulation propre).
14. La version `1.0.0` reste **active** sans interruption.
15. Temps total < 30 s en SHADOW.

### Étape 5 — Audit BLAKE3

16. Onglet « Audit » :
    ```
    14:23:05Z  WORKFLOW_DEPLOY_REQUESTED  user:fasodigitalisation@gmail.com  strategy=SHADOW
    14:23:42Z  WORKFLOW_APPROVED          user:partenaire@gov.bf
    14:23:43Z  WORKFLOW_SHADOW_STARTED    -
    14:28:50Z  WORKFLOW_ROLLBACK_REQUESTED user:fasodigitalisation@gmail.com
    14:28:51Z  WORKFLOW_ROLLBACK_COMPLETED -  durationMs=890
    ```

## Critères de validation

- [ ] Le déploiement SHADOW a bien nécessité une approbation 4-eyes.
- [ ] Tu ne t'es **pas** auto-approuvé (refus système si tentative).
- [ ] Le tableau SHADOW Metrics a affiché ≥ 2 indicateurs comparés.
- [ ] Le rollback s'est exécuté en < 30 s (SLO SHADOW).
- [ ] La chaîne d'audit contient les 5 événements attendus.

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

**Requête de déploiement** (envoyée par l'UI à `analytics-api`) :

```json
{
  "workflowId": "pagsi-volumes-region",
  "semver": "1.1.0-draft.1",
  "strategy": "SHADOW",
  "shadowSpec": {
    "duration": "PT24H",
    "comparisonRef": { "workflowId": "pagsi-volumes-region", "semver": "1.0.0" },
    "metricsToCompare": ["volume_tonnes", "variation_pct"]
  },
  "fourEyesRequired": false
}
```

**Réponse** :

```json
{
  "status": "PENDING_APPROVAL",
  "approvalUrl": "https://analytics-pp.gov.bf/approvals/abc-123",
  "expiresAt": "2026-05-11T20:00:00Z"
}
```

**Audit chain (extrait)** :

```
[
  { "ts": "...", "action": "WORKFLOW_DEPLOY_REQUESTED",   "hash": "blake3:11..." },
  { "ts": "...", "action": "WORKFLOW_APPROVED",           "hash": "blake3:22...", "prev": "blake3:11..." },
  { "ts": "...", "action": "WORKFLOW_SHADOW_STARTED",     "hash": "blake3:33...", "prev": "blake3:22..." },
  { "ts": "...", "action": "WORKFLOW_ROLLBACK_REQUESTED", "hash": "blake3:44...", "prev": "blake3:33..." },
  { "ts": "...", "action": "WORKFLOW_ROLLBACK_COMPLETED", "hash": "blake3:55...", "prev": "blake3:44..." }
]
```

</details>
