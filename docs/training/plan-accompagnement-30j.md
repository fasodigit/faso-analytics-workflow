# Plan d'accompagnement 30 jours — Référents FASO-ANALYTICS-WORKFLOW

> **Démarrage** : J+3 après la formation (3 jours de récupération + révision).
> **Durée totale** : 30 jours calendaires.
> **Objectif final** : **1 workflow réel déployé en SHADOW** par le référent, sans aide d'un développeur.
> **Référence ultraplan** : §10 Phase 4 — Livrable T (suite formation).

---

## 1. Vue d'ensemble

Le plan d'accompagnement est structuré en **4 semaines** avec une intensité décroissante :

| Semaine | Fréquence checkpoint | Durée par checkpoint | Total temps / référent |
|---|---|---|---|
| Semaine 1 (J+3 à J+9) | Quotidien (5 jours ouvrés) | 30 min | 2 h 30 |
| Semaine 2 (J+10 à J+16) | 2× / semaine (mar + jeu) | 45 min | 1 h 30 |
| Semaine 3 (J+17 à J+23) | 2× / semaine (mar + jeu) | 45 min | 1 h 30 |
| Semaine 4 (J+24 à J+30) | 1× / semaine + rétrospective | 60 min + 90 min rétro | 2 h 30 |

**Total** ≈ 8 heures de coaching individuel par référent × 8 référents = **64 heures coach** sur le mois.

---

## 2. Semaine 1 (J+3 à J+9) — Consolidation vocabulaire et reproduction

### Objectif
Le référent reproduit en autonomie 5 workflows d'exemple parmi les 8 fournis (`schemas/examples/`).

### Format
Checkpoint quotidien de 30 min via visio (Jitsi sovereign) ou présentiel.

### Jour J+3
- Tour de table du J+0 (formation) + ressenti.
- Reprise du QCM : les 25 questions, les 3 plus discutées.
- Validation des accès Kratos + Keto à pré-prod.
- Vérification : devoir maison J1 validé.
- Annonce du planning des checkpoints 1-5.

### Jour J+4
- Reproduction du workflow `02-bar-horizontal-top10-communes.json` en autonomie.
- Le référent partage son écran, l'instructeur observe sans intervenir.
- Debrief 10 min : difficultés, raccourcis utilisés, erreurs commises.

### Jour J+5
- Reproduction du workflow `04-donut-distribution-statuts.json`.
- Focus sur le **format** des KPI (pattern, unit, decimals).
- Tour des palettes (`categorical_set1`, `diverging_rdylgn`, etc.).

### Jour J+6
- Reproduction du workflow `07-line-multi-evolution.json`.
- Focus sur les **transformations temporelles** (`date_trunc`, `EXTRACT`, fenêtres).

### Jour J+7
- Reproduction du workflow `08-choropleth-bf-coverage.json`.
- Focus sur la **carte choroplèthe** et le mapping `region → région_normalisée_INSD`.

### Jour J+8 (vendredi)
- Reproduction du workflow `05-half-donut-resurep-especes.json` (le plus complexe).
- Focus sur les **outputs multiples** (dashboard + email + cron).
- Premier exercice de simulation `STRATIFIED` indépendante.

### Jour J+9 (lundi suivant — bonus si besoin)
- Rattrapage individuel si l'un des workflows reproduits n'a pas validé le schéma.
- Sinon, début de la planification du **workflow réel** à migrer (annonce semaine 2).

### Livrables attendus en fin de semaine 1
- 5 workflows reproduits avec validation Schema OK.
- 1 sujet de **workflow réel** identifié dans le sub-projet du référent.
- 1 sprint board mis en place (Kanban interne).

---

## 3. Semaine 2 (J+10 à J+16) — Construction d'un workflow réel

### Objectif
Le référent **conçoit et construit** son premier workflow original, basé sur un cas réel de son sub-projet, accompagné en simulation.

### Format
2 checkpoints par semaine : mardi et jeudi, 45 min chacun.

### Mardi (J+10)
- Présentation par le référent de son cas métier (spec à l'écrit, 1 page).
- Critique constructive par l'instructeur : faisabilité, sources, dimensions, KPIs cibles.
- Validation ou ajustement du périmètre.
- Lancement de la construction.

### Jeudi (J+12)
- Le référent montre l'état d'avancement du workflow.
- Lancement de la première simulation `STRATIFIED`.
- Lecture du diff (mais pas encore comparé à un DEPLOYED car nouvelle création).
- Liste des points à corriger.

### Critères de progression
- [ ] Sujet métier validé en mardi.
- [ ] Workflow en statut `SIMULATED` à jeudi.
- [ ] Au moins 1 KPI et 1 visualisation fonctionnels.

---

## 4. Semaine 3 (J+17 à J+23) — Itération et préparation SHADOW

### Objectif
Le référent itère sur son workflow, le fiabilise, et prépare un déploiement SHADOW.

### Format
2 checkpoints par semaine : mardi et jeudi, 45 min chacun.

### Mardi (J+17)
- Revue du workflow consolidé (J+10 → J+16).
- Mise en place de la `driftPolicy`.
- Test : volontairement modifier le schéma source en sandbox pour déclencher un drift, le résoudre.
- Validation du workflow par un pair (autre référent du même domaine si possible).

### Jeudi (J+19)
- Préparation du déploiement SHADOW.
- Désignation d'un **approbateur 4-eyes** dans l'équipe métier du référent (autre cadre, pas un développeur).
- Brief de l'approbateur sur ce qu'il regardera.
- Simulation finale `STRATIFIED` 10 000 lignes.

### Critères de progression
- [ ] driftPolicy explicite et testée.
- [ ] Approbateur 4-eyes identifié et briefé.
- [ ] Workflow prêt pour déploiement SHADOW.

---

## 5. Semaine 4 (J+24 à J+30) — Déploiement SHADOW et rétrospective

### Objectif
Le référent **déploie son workflow en SHADOW** dans la pré-production, le mesure pendant 48-72h, puis participe à la rétrospective de formation.

### Mardi (J+24)
- Le référent lance le déploiement SHADOW depuis son compte.
- L'approbateur valide en 4-eyes.
- Le workflow tourne en SHADOW pour 72 h (jusqu'à vendredi).
- Le référent met en place un tableau de surveillance (latence, diff vs version actuelle, alertes).

### Vendredi (J+27)
- Lecture du tableau SHADOW Metrics après 72 h.
- Décision conjointe : promouvoir en `DEPLOYED` (si métriques OK) ou retravailler.
- Si OK : préparer une demande de **vrai déploiement DIRECT** (réservé Phase 5 avec accompagnement).

### Lundi-Mardi (J+30 — rétrospective)

Séance commune de **90 min** réunissant les 8 référents + 2 instructeurs.

#### Agenda rétrospective
1. **Tour de table** (15 min) : chaque référent partage son workflow déployé + 1 difficulté rencontrée.
2. **Bilan QCM bis** (10 min) : refaire les 5 questions les plus ratées en J0.
3. **Feedback formation** (20 min) :
   - Qu'est-ce qui était trop court / trop long ?
   - Quel module aurait nécessité plus de temps ?
   - Quels exercices ont été les plus utiles ?
4. **Échanges entre référents** (15 min) : partage des bonnes pratiques inter-sub-projets.
5. **Annonce du dispositif Phase 5** (10 min) : ouverture du canal `#analytics-workflow-referents` permanent, support runtime.
6. **Remise du certificat « stable »** (10 min) : passage du certificat conditionnel → certificat définitif.
7. **Photo de groupe** + collation (10 min).

### Critères de fin d'accompagnement
- [ ] **1 workflow réel déployé en SHADOW** par le référent, sans aide d'un développeur.
- [ ] Mesure SHADOW lue et interprétée par le référent.
- [ ] Approbation 4-eyes obtenue d'un cadre métier (pas un dev).
- [ ] Participation à la rétrospective.
- [ ] Score QCM bis ≥ 18 / 25.

---

## 6. Modalités pratiques

### Outils de communication
- Visio : Jitsi sovereign (`https://jitsi.fasodigitalisation.gov.bf`).
- Messagerie instantanée : canal KAYA `#analytics-workflow-referents` (post-formation).
- Email : adresse dédiée `formation-analytics@fasodigitalisation.gov.bf`.
- Tickets bloquants : Issue Tracker interne (Gitea sovereign).

### Encadrants disponibles
- 1 instructeur principal joignable lundi-vendredi 9h-17h.
- 1 instructeur secondaire en backup les jeudis.
- Support SecOps pour les accès Kratos / Keto / Vault.

### Documents partagés
- `docs/training/` (cette formation).
- `docs/user-guide/01-prise-en-main.typ` (guide utilisateur).
- `docs/runbook/runbook-ops.md` (ops courantes).
- `schemas/examples/` (8 workflows de référence).
- `schemas/workflow-v1.json` (schéma JSON normatif).

### Confidentialité
Les workflows réels manipulés en pré-prod restent **propriété du sub-projet** d'origine et **ne sont pas partagés** entre référents sans accord explicite.

---

## 7. Indicateurs de succès du dispositif d'accompagnement

À l'issue des 30 jours, on mesure :

| Indicateur | Cible | Mesure |
|---|---|---|
| Référents certifiés stables | 8 / 8 | Décompte sur le QCM bis. |
| Workflows réels déployés SHADOW | ≥ 6 / 8 | Audit BLAKE3 des événements `WORKFLOW_SHADOW_STARTED`. |
| Workflows promus DIRECT/BLUE_GREEN dans les 60 j | ≥ 4 / 8 | Audit BLAKE3 des événements `WORKFLOW_DEPLOYED`. |
| Tickets bloquants ouverts par référent | ≤ 2 / 30 jours | Gitea Issue Tracker. |
| Satisfaction référents | NPS ≥ +50 | Formulaire post-formation. |

---

## 8. Continuité au-delà du jour 30

L'accompagnement 30 jours n'est pas la fin du dispositif. **Phase 5** prévoit :

- Communauté de pratique mensuelle (1 réunion / mois, 2 h, présentation cas concrets).
- Champion par sub-projet : le référent devient le **support N1** pour son équipe.
- Escalade N2 vers l'équipe `analytics-workflow-core` pour les tickets complexes.
- Renouvellement de certification à 12 mois (mini-QCM 10 questions + 1 workflow récent).

---

## 9. Modèle de fiche de checkpoint individuelle

Pour chaque checkpoint individuel, l'instructeur remplit la fiche suivante :

```
Référent : [Nom]
Sub-projet : [VOUCHERS / E_TICKET / ...]
Date : [AAAA-MM-JJ]
Durée : [30 / 45 / 60 min]
Semaine : [1 / 2 / 3 / 4]

Avancement constaté :
[2-5 lignes : ce que le référent a fait depuis le dernier checkpoint]

Blocages identifiés :
[Liste à puces : techniques, métier, organisationnels]

Actions pour le prochain checkpoint :
[Liste à puces : reproduire X, construire Y, lire Z]

Note de progression (0-5) :
[Note attribuée par l'instructeur sur la base de l'autonomie observée]

Risque de décrochage :
[Faible / Modéré / Élevé]
```

Les fiches sont stockées dans un Drive sovereign (Nextcloud) accessible uniquement aux instructeurs et au référent concerné.

---

## 10. Récapitulatif du chemin de certification

```
Formation J0 (2 j)
  ↓ QCM J0 (≥ 18/25)
  ↓ Certificat conditionnel délivré
  ↓
Semaine 1 (J+3 à J+9) — 5 reproductions
  ↓
Semaine 2 (J+10 à J+16) — Workflow réel en construction
  ↓
Semaine 3 (J+17 à J+23) — Itération + drift testé
  ↓
Semaine 4 (J+24 à J+30) — Déploiement SHADOW + rétrospective
  ↓ QCM bis (≥ 18/25) + TP10 (≥ 4/5)
  ↓ Certificat stable délivré
  ↓
Phase 5 (post-J+30) — Communauté de pratique mensuelle
  ↓ À J+12 mois : renouvellement certification
```

---

*Fin du plan d'accompagnement 30 jours.*
