# Jour 2 — Cas pratiques

> **Cible** : 8 référents métier ayant complété le Jour 1.
> **Pré-requis** : devoir maison J1 rendu et validé par l'instructeur.
> **Objectif global** : à la fin du J2, chaque participant a simulé, détecté un drift, déployé un workflow (SHADOW), lu une chaîne d'audit BLAKE3, et validé le QCM final.

---

## Vue d'ensemble du Jour 2

| Module | Heure | Titre | Format | Durée |
|---|---|---|---|---|
| M9 | 09:00 – 10:00 | Simulation : sample strategies + diff vs DEPLOYED | démo + TP4 | 1 h |
| — | 10:00 – 10:15 | Pause café | — | — |
| M10 | 10:15 – 11:30 | Cas pratique 1 : PAGSI volumes Région × Céréale | hands-on intégral | 1 h 15 |
| — | 11:30 – 12:30 | Cas pratique 2 : RESUREP-26 (santé animale, SurveyMonkey source) | hands-on | 1 h |
| — | 12:30 – 14:00 | Pause déjeuner | — | — |
| M11 | 14:00 – 15:00 | Schema drift : détection, résolution interactive | TP5 | 1 h |
| M12 | 15:00 – 16:00 | Déploiement : DIRECT / SHADOW / BLUE_GREEN + rollback | démo + TP6 | 1 h |
| — | 16:00 – 16:15 | Pause | — | — |
| M13 | 16:15 – 17:00 | Audit BLAKE3 : vérification offline + lecture des logs | démo CLI | 45 min |
| M14 | 17:00 – 17:30 | QCM + remise de certificats + plan d'accompagnement 30 jours | clôture | 30 min |

---

## Module M9 — Simulation : sample strategies + diff vs DEPLOYED (09:00 – 10:00)

### Objectif pédagogique

Comprendre les 4 stratégies d'échantillonnage de la simulation et savoir lire le diff entre une simulation et la version actuellement déployée.

### Plan détaillé

- Rappel : un workflow ne devient `DEPLOYED` qu'après avoir passé l'état `SIMULATED`.
- 4 sample strategies :
  - **RANDOM** : tirage aléatoire uniforme (par défaut 1 000 lignes).
  - **STRATIFIED** : tirage avec préservation des proportions par catégorie (recommandé pour les workflows avec `groupBy`).
  - **BOUNDARY_VALUES** : valeurs extrêmes (min, max, nulls, doublons) — pour stress-tester un workflow.
  - **FULL** : exécution complète (lent, à n'utiliser que pour le bench final).
- Démo live : lancer une simulation `STRATIFIED` sur le workflow PAGSI du J1.
- Lecture du **panneau de diff** :
  - Colonne gauche : résultat de la version `DEPLOYED`.
  - Colonne droite : résultat de la simulation courante.
  - Surlignage : valeurs identiques (vert), valeurs nouvelles (bleu), valeurs disparues (rouge).
- Notion de **toleranceMargin** : tolérance acceptable pour les agrégats flottants (souvent 0.01 %).
- Lancement du TP4 (cf. `exercises/tp4-simulation-diff.md`).

### Slides à projeter

- S27 : 4 sample strategies × cas d'usage.
- S28 : capture du panneau de diff annoté.
- S29 : exemple de `toleranceMargin` mal réglé (faux positifs).

### Exercice TP

Voir [`exercises/tp4-simulation-diff.md`](./exercises/tp4-simulation-diff.md).

**Golden answer** : simulation lancée avec sample `STRATIFIED` 5 000 lignes ; diff lisible ; conclusion : « workflow équivalent à la version déployée modulo tolérance ».

### Pièges courants

1. Toujours utiliser `RANDOM` pour un workflow avec `groupBy` (sous-représentation de certaines catégories rares).
2. Confondre `FULL` (exécution complète mais en sandbox) et le **déploiement** (en prod).
3. Ne pas voir le warning « simulation expirée » après 30 minutes.
4. Surinterpréter une différence < `toleranceMargin` comme un vrai changement.
5. Lancer une simulation FULL sur 100 M de lignes en pleine journée (saturation cluster).

### Évaluation orale (3 questions)

1. Quelle sample strategy pour stress-tester un workflow ?
2. Quelle est la durée de vie d'une simulation en sandbox ?
3. Quel rôle joue `toleranceMargin` ?

---

## Module M10 — Cas pratique 1 : PAGSI volumes Région × Céréale (10:15 – 11:30)

### Objectif pédagogique

Construire **de A à Z** un workflow métier réel : PAGSI volumes distribués par Région × Type de céréale, campagne 2025-2026.

### Plan détaillé

- Contexte métier complet : DGEAP (Direction Générale Études Aménagement Prévision) demande chaque mois un tableau de bord croisant les régions et les types de céréales pour la campagne en cours.
- Données disponibles : table `voucher_schema.distribution` dans le sub-projet VOUCHERS.
  - Champs : `id`, `region`, `province`, `commune`, `type_cereale`, `volume_kg`, `campagne`, `date_distribution`, `beneficiaire_id`.
- Spécification métier reçue par email :
  - Filtrer sur `campagne = '2025-2026'`.
  - Agréger par `region` × `type_cereale`.
  - Sommer `volume_kg`.
  - Convertir kg → tonnes.
  - 1 KPI « Volume total distribué » (more_better, target 50 000 t).
  - 1 viz BAR_GROUPED.
  - 1 output dashboard `pagsi_region_cereale` (refresh 30 min).
- Construction en autonomie (45 min effectives).
- Aide à la demande de l'instructeur.
- Référence : `migrations/production/pagsi-volumes-region-cereale-v1.0.0.json` (nom validé avec l'agent M).
- Simulation `STRATIFIED` puis diff.
- Validation finale par l'instructeur (annotation TP « validé »).

### Slides à projeter

- S30 : page de spécification métier reçue par email.
- S31 : schéma table `voucher_schema.distribution`.
- S32 : capture d'écran cible (placeholder).

### Exercice TP

Identique au cas pratique du module — l'énoncé est rappelé dans `exercises/tp1-reproduire-pagsi.md` (TP1) mais en version **complète** (avec KPIs, polarités, seuils, sans aide de l'instructeur).

**Golden answer** : workflow JSON équivalent sémantiquement à `schemas/examples/03-bar-grouped-region-x-cereale.json`.

### Pièges courants

1. Filtrer sur `campagne LIKE '2025%'` au lieu de `'2025-2026'` (inclut 2024-2025 et 2025-2026).
2. Oublier la **conversion kg → tonnes**.
3. Mettre la polarité `neutral` (perte d'information visuelle).
4. Mettre `seriesType` sur le viz BAR_GROUPED (ce champ est pour COMBO).
5. Oublier l'output ou mettre un `refreshSec < 60` (refus par schéma — éviter 60s sur prod).

### Évaluation orale (3 questions)

1. Pourquoi convertir kg → tonnes côté workflow ?
2. Quelle est la polarité d'un volume distribué ?
3. Quel champ pour la dimension secondaire dans BAR_GROUPED ?

---

## Cas pratique 2 — RESUREP-26 (11:30 – 12:30)

### Objectif pédagogique

Découvrir une **source non-SQL** : SurveyMonkey webhook (cas RESUREP-26, santé animale).

### Plan détaillé

- Contexte métier : Direction des Services Vétérinaires (DGSV) collecte hebdomadairement des observations de terrain via formulaires SurveyMonkey. Cas par espèce animale, validations terrain.
- Source `surveymonkey` :
  - `webhookRef` : URL d'écoute interne (sovereign).
  - `surveyId` : identifiant du formulaire (ex. `survey:resurep-26`).
  - `mappingRef` : mapping question → champ structuré.
- Pipeline :
  - `filter validation_status_id = 'validated'`.
  - `filter date_observation BETWEEN date_trunc('week', now()) AND now()`.
  - `aggregate par espece_animale`.
- KPI critique : nombre de cas (less_better, polarité inversée car « moins = mieux »).
- Visualisation HALF_DONUT pour répartition par espèce.
- 2 outputs : dashboard + email weekly summary (cron MON 08:00).
- Référence : `migrations/production/resurep-surveillance-26-v1.0.0.json` (nom validé avec l'agent M).
- Construction en autonomie (45 min effectives) — cf. `exercises/tp7-cas-pratique-resurep.md`.

### Slides à projeter

- S33 : architecture webhook SurveyMonkey → analytics-api.
- S34 : exemple de payload SurveyMonkey + mapping.
- S35 : cible visuelle HALF_DONUT.

### Exercice TP

Voir [`exercises/tp7-cas-pratique-resurep.md`](./exercises/tp7-cas-pratique-resurep.md).

**Golden answer** : workflow équivalent à `schemas/examples/05-half-donut-resurep-especes.json`.

### Pièges courants

1. Oublier `validation_status_id = 'validated'` (pollue les chiffres avec données brutes).
2. Polarité `more_better` pour un nombre de cas (erreur sémantique grave).
3. Output email sans `cron` (rejet schéma).
4. Mettre `refreshSec = 60` (trop fréquent pour un hebdo).
5. Oublier `critical = true` sur le KPI (pas d'alerte temps réel).

### Évaluation orale (3 questions)

1. Pourquoi RESUREP-26 utilise SurveyMonkey et non YugabyteDB ?
2. Que se passe-t-il si on oublie `critical = true` ?
3. Quelle est la fréquence d'envoi de l'email ?

---

## Pause déjeuner (12:30 – 14:00)

---

## Module M11 — Schema drift : détection, résolution interactive (14:00 – 15:00)

### Objectif pédagogique

Apprendre à diagnostiquer et résoudre un **drift de schéma** : nouveau champ, type modifié, champ renommé, champ supprimé.

### Plan détaillé

- Rappel : la `driftPolicy` du workflow définit comment réagir aux évolutions du schéma source.
  - `onNewField` : `IGNORE` | `REQUIRE_MAPPING` | `BLOCK`.
  - `onRemovedField` : `BLOCK` (par défaut, sinon impossible de calculer).
  - `onTypeChange` : `BLOCK` | `REQUIRE_CAST`.
  - `onRenamed` : `SUGGEST` (proposition automatique via Levenshtein < 3).
- Démo : modifier le sample sub-projet pour ajouter un champ `id_certificat` et renommer `region` → `nom_region`.
- Lancement de la détection : `analytics-api detect-drift --workflow <name>`.
- Lecture du rapport JSON de drift :
  - 1 nouveau champ détecté → action `REQUIRE_MAPPING`.
  - 1 champ renommé détecté → suggestion `region → nom_region` (Levenshtein distance 4 → pas suggéré, l'analyste doit confirmer manuellement).
- Résolution interactive depuis l'UI :
  - Ouvrir le drift report.
  - Pour chaque entrée, choisir une action (Map, Ignore, Cast, Abort).
  - Sauvegarder le mapping → re-simulation automatique.
- Lancement TP5 (cf. `exercises/tp5-schema-drift-resolution.md`).

### Slides à projeter

- S36 : 4 types de drift × action recommandée.
- S37 : capture du rapport de drift (placeholder).
- S38 : UI de résolution interactive (placeholder).

### Exercice TP

Voir [`exercises/tp5-schema-drift-resolution.md`](./exercises/tp5-schema-drift-resolution.md).

**Golden answer** : drift résolu avec 1 mapping (renommé) + 1 ignore (nouveau champ non utilisé) + re-simulation OK.

### Pièges courants

1. Mettre `onRemovedField = IGNORE` (impossible, le moteur refuse).
2. Confondre `onTypeChange = REQUIRE_CAST` avec une conversion automatique (en réalité l'analyste doit fournir l'expression de cast).
3. Renommer arbitrairement un champ sans drift report → casse silencieuse en production.
4. Accepter une suggestion Levenshtein sans vérifier la sémantique (faux ami).
5. Sauter la re-simulation après résolution → déploiement sur base potentiellement incohérente.

### Évaluation orale (3 questions)

1. Quelle valeur par défaut de `onRemovedField` ?
2. À quoi sert `Levenshtein < 3` pour `onRenamed` ?
3. Que faire après avoir résolu un drift ?

---

## Module M12 — Déploiement : DIRECT / SHADOW / BLUE_GREEN + rollback (15:00 – 16:00)

### Objectif pédagogique

Comprendre les 3 stratégies de déploiement, la règle 4-eyes et savoir exécuter un **rollback en moins de 60 secondes**.

### Plan détaillé

- **DIRECT** : le nouveau workflow remplace immédiatement l'ancien. Risque : un bug atterrit en prod.
  - Cas d'usage : workflows non critiques, faible volume.
  - 4-eyes : optionnel.
- **SHADOW** : les 2 versions tournent en parallèle, mais seule l'ancienne sert les dashboards. La nouvelle est mesurée silencieusement.
  - Cas d'usage : changement important, validation par mesure pendant 24-72 h.
  - 4-eyes : recommandé.
- **BLUE_GREEN** : bascule progressive 0 % → 10 % → 50 % → 100 % du trafic vers la nouvelle version.
  - Cas d'usage : workflows critiques, fort volume, possibilité de rollback immédiat.
  - 4-eyes : **obligatoire** si `isCritical = true`.
- Règle **4-eyes** :
  - 2 utilisateurs distincts requis : 1 auteur + 1 approbateur.
  - Approbateur doit avoir le rôle `analytics:approver` dans Keto.
  - Trace immuable : qui a approuvé, à quelle heure, sur quelle version.
- Rollback :
  - Sur SHADOW : annulation simple (pas d'impact prod).
  - Sur DIRECT : rollback explicite — restauration de la version N-1 en < 60 s (SLO).
  - Sur BLUE_GREEN : remise du curseur à 0 % vers la nouvelle version, le trafic revient à 100 % vers l'ancienne en < 30 s.
- Démo de chaque stratégie + démonstration d'un rollback complet.
- Lancement TP6 (cf. `exercises/tp6-deploy-shadow-rollback.md`).

### Slides à projeter

- S39 : matrice 3 stratégies × cas d'usage × 4-eyes.
- S40 : timeline d'un déploiement SHADOW (24 h de mesure).
- S41 : runbook rollback en 5 étapes.

### Exercice TP

Voir [`exercises/tp6-deploy-shadow-rollback.md`](./exercises/tp6-deploy-shadow-rollback.md).

**Golden answer** : workflow déployé en SHADOW, mesure constatée OK, puis rollback exécuté en < 60 s avec confirmation via la console.

### Pièges courants

1. Déployer un workflow `isCritical = true` en DIRECT sans 4-eyes (refus immédiat).
2. Confondre SHADOW (mesure silencieuse) avec BLUE_GREEN (bascule progressive).
3. Oublier de surveiller la **chaîne d'audit** lors d'un rollback (perte de traçabilité).
4. Croire qu'un workflow déployé en SHADOW affecte les dashboards (faux, c'est silencieux).
5. Approuver soi-même son propre workflow (violation 4-eyes — refus).

### Évaluation orale (3 questions)

1. Quelle stratégie pour un workflow `isCritical = true` ?
2. Combien de temps maximum pour un rollback DIRECT ?
3. Peut-on s'auto-approuver ?

---

## Pause (16:00 – 16:15)

---

## Module M13 — Audit BLAKE3 : vérification offline + lecture des logs (16:15 – 17:00)

### Objectif pédagogique

Savoir lire et vérifier la **chaîne d'audit BLAKE3** d'un workflow (cf. ADR-005).

### Plan détaillé

- Pourquoi BLAKE3 ? (vs SHA-256) :
  - Performances : ~10× plus rapide.
  - Compatibilité chaînage en arbre (Merkle).
  - Résistance cryptographique élevée.
- Anatomie d'un événement d'audit :
  ```json
  {
    "eventId": "evt-uuid-v7",
    "timestamp": "2026-05-11T14:30:00Z",
    "actor": "user:traore.lionel@gmail.com",
    "action": "WORKFLOW_DEPLOYED",
    "workflowId": "pagsi-volumes-region",
    "semver": "1.0.0",
    "strategy": "SHADOW",
    "hash": "blake3:abc123...",
    "previousHash": "blake3:def456..."
  }
  ```
- Chaque événement contient le **hash BLAKE3 de tout son contenu + le hash de l'événement précédent** → chaîne infalsifiable.
- Vérification offline via CLI :
  ```bash
  analytics-cli audit verify --since 2026-05-01 --until 2026-05-11
  ```
- Lecture du résultat :
  - `✓ 1247 événements vérifiés, chaîne intègre`.
  - `✗ Événement evt-xxx : hash inattendu (corruption détectée à 2026-05-08T12:34:56Z)`.
- Cas concret : un auditeur Cour des Comptes demande la liste des changements sur le workflow `resurep-26` entre janvier et avril 2026.
  - Commande : `analytics-cli audit list --workflow resurep-26 --since 2026-01-01 --until 2026-04-30`.
- Lecture des logs structurés :
  - Format JSON (clé/valeur).
  - Champs clés : `traceId`, `spanId`, `workflowId`, `actor`, `severity`.
  - Outils de filtrage : `jq`, Loki, Grafana.

### Slides à projeter

- S42 : analogie chaîne BLAKE3 ↔ blockchain ↔ registre d'état civil.
- S43 : commande CLI annotée.
- S44 : exemple de rapport d'audit pour Cour des Comptes (placeholder).

### Exercice TP

**Énoncé** : depuis le poste de chaque participant, lancer la commande `analytics-cli audit verify --since J-7` et lire le résultat. Identifier au moins 1 événement de type `WORKFLOW_DEPLOYED` dans la dernière semaine.

**Golden answer** : commande exécutée, chaîne intègre confirmée, événement déploiement identifié avec son actor et son hash.

### Pièges courants

1. Confondre `eventId` (UUID v7 unique) et `hash` (hash BLAKE3 du contenu).
2. Modifier un événement passé (impossible — la chaîne sera cassée).
3. Penser que les logs applicatifs remplacent l'audit BLAKE3 (faux, ce sont deux flux distincts).
4. Oublier la fenêtre `--since`/`--until` et auditer toute l'histoire (très lent).
5. Lancer la commande sans les droits Keto adéquats (`analytics:auditor`).

### Évaluation orale (3 questions)

1. Quel est l'avantage de BLAKE3 sur SHA-256 ?
2. Comment vérifier l'intégrité d'une chaîne offline ?
3. Quel rôle Keto pour lancer un audit ?

---

## Module M14 — QCM + remise de certificats + plan d'accompagnement 30 jours (17:00 – 17:30)

### Objectif pédagogique

Évaluer les acquis de la formation et organiser le suivi post-formation.

### Plan détaillé

- Distribution du QCM (cf. `qcm-final.md`).
  - 25 questions, 4 réponses par question.
  - Durée : 20 min.
  - Format : sur papier (corrigé par les instructeurs en temps réel).
  - Seuil de réussite : 70 % (18/25).
- Pendant la correction (10 min) : présentation du plan d'accompagnement 30 jours (cf. `plan-accompagnement-30j.md`).
- Remise des certificats :
  - Certificat de référent FASO-ANALYTICS-WORKFLOW (signé par le ministre).
  - Mention « Avec félicitations du jury » pour les notes ≥ 22/25.
- Tour de table final : chaque référent annonce le premier workflow qu'il compte migrer dans les 30 jours.
- Fin officielle : photo de groupe (avec accord des participants).
- Annonce du **canal de support post-formation** : channel KAYA `#analytics-workflow-referents`.

### Slides à projeter

- S45 : règles du QCM.
- S46 : aperçu du certificat.
- S47 : organigramme du plan d'accompagnement.
- S48 : remerciements.

### Exercice TP

QCM final (cf. `qcm-final.md`) — 25 questions QCM individuelles.

**Golden answer** : chaque référent atteint ≥ 18/25 (sinon rattrapage planifié).

### Pièges courants

1. Ne pas avoir révisé le vocabulaire du J1 (M1).
2. Confondre `more_better` et `less_better` dans les questions polarité.
3. Confondre les 3 stratégies de déploiement.
4. Oublier que SHADOW ne sert pas le trafic réel.
5. Penser qu'on peut s'auto-approuver en 4-eyes.

### Évaluation orale (3 questions)

1. Quel est le seuil de réussite ?
2. Que se passe-t-il si on échoue ?
3. Quel est le canal de support post-formation ?

---

*Fin du Jour 2 — Cas pratiques.*
