# Jour 1 — Fondamentaux

> **Cible** : 8 référents métier, premier contact avec FASO-ANALYTICS-WORKFLOW.
> **Pré-requis lus** : `docs/user-guide/01-prise-en-main.typ`.
> **Objectif global** : à la fin du J1, chaque participant a créé son premier workflow valide et reproductible.

---

## Vue d'ensemble du Jour 1

| Module | Heure | Titre | Format | Durée |
|---|---|---|---|---|
| M1 | 09:00 – 09:45 | Bienvenue + tour de table + vocabulaire métier | présentation + interaction | 45 min |
| M2 | 09:45 – 10:30 | Architecture C4 simplifiée — pourquoi un module transversal | Slides + Q/R | 45 min |
| — | 10:30 – 10:45 | Pause café | — | — |
| M3 | 10:45 – 11:30 | Tour guidé de l'interface : palette, canvas DAG, paramètres | démo live | 45 min |
| M4 | 11:30 – 12:30 | TP1 : Reproduire un workflow existant (PAGSI sur sample) | hands-on | 1 h |
| — | 12:30 – 14:00 | Pause déjeuner | — | — |
| M5 | 14:00 – 15:00 | Sources et transformations — déclarer, paramétrer | démo + TP2 | 1 h |
| M6 | 15:00 – 16:00 | KPI : définition, format, polarité, target | démo + TP3 | 1 h |
| — | 16:00 – 16:15 | Pause | — | — |
| M7 | 16:15 – 17:00 | Visualisations — choisir le bon graphique | démo des 19 types | 45 min |
| M8 | 17:00 – 17:30 | Débriefing J1 + devoir maison | rétrospective | 30 min |

---

## Module M1 — Bienvenue + tour de table + vocabulaire métier (09:00 – 09:45)

### Objectif pédagogique

Aligner les 8 référents sur un vocabulaire commun : workflow, source, transformation, KPI, visualisation, output.

### Plan détaillé

- Mot de bienvenue institutionnel (Min. Économie Numérique).
- Tour de table : prénom + sous-projet + 1 dashboard que le participant utilise déjà au quotidien.
- Présentation des instructeurs et du déroulé général.
- Distribution des fiches de pré-requis et vérification de l'accès Kratos sur chaque poste.
- Vocabulaire métier : projection d'une infographie A1 du DAG type avec ses 5 zones (source, pipeline, KPIs, visualizations, outputs).
- Mini-exercice oral : chacun reformule en français ses propres mots la différence entre **KPI** et **visualisation**.
- Cadrage des règles de la formation : participation active, droit à l'erreur, prise de notes encouragée, photos interdites des écrans de pré-prod.
- Annonce du planning : 8 modules J1, 6 modules J2, 1 QCM final.

### Slides à projeter

- S1 : page de garde « FASO-ANALYTICS-WORKFLOW — Formation Référents Métier ».
- S2 : portrait des 8 référents (placeholder).
- S3 : carte mentale du vocabulaire (workflow / source / pipeline / KPI / viz / output).
- S4 : règles de la formation.

### Exercice TP

**Énoncé** : sur le paperboard, chaque référent vient écrire 1 KPI métier qu'il connaît, puis indique sa **polarité** (`more_better` ou `less_better`).

**Golden answer attendue** : 8 KPIs distincts, par exemple :

- VOUCHERS — Tonnage distribué (more_better).
- E_TICKET — Taux de remplissage (more_better).
- ETAT_CIVIL — Délai moyen de délivrance (less_better).
- SOGESY — Taux de rupture de stock (less_better).
- HOSPITAL — Couverture vaccinale (more_better).
- FASO_KALAN — Taux d'assiduité (more_better).
- ALT_MISSION — Coût moyen par mission (less_better).
- E_SCHOOL — Taux de redoublement (less_better).

### Pièges courants

1. Confondre **KPI** et **visualisation** (le KPI est une valeur, la viz est sa représentation graphique).
2. Penser qu'un workflow contient « ses données » (en réalité il **pointe** vers une source, il n'embarque pas la donnée).
3. Oublier que la polarité conditionne la **couleur** des seuils warning/critical à l'affichage.
4. Croire qu'un KPI est automatiquement une viz `KPI_TILE` (faux, un KPI peut être réutilisé dans un BAR_VERTICAL par ex.).
5. Imaginer que le workflow est une « requête SQL » classique (faux, c'est un **DAG déclaratif** versionné).

### Évaluation orale (3 questions)

1. Qu'est-ce qu'un sous-projet et combien y en a-t-il à la FASO DIGITALISATION ?
2. Si un KPI est « nombre d'erreurs critiques », quelle est sa polarité ?
3. Donne 2 exemples de sources possibles dans un workflow.

---

## Module M2 — Architecture C4 simplifiée — pourquoi un module transversal (09:45 – 10:30)

### Objectif pédagogique

Comprendre **pourquoi** FASO-ANALYTICS-WORKFLOW est un module transversal et **où** il se place dans la cartographie applicative du gouvernement.

### Plan détaillé

- Rappel du contexte : 8 sous-projets, chacun avec ses propres données mais des besoins analytiques similaires.
- Avant FASO-ANALYTICS-WORKFLOW : duplication des codes statistiques dans chaque sous-projet, incohérence entre dashboards, formats hétérogènes.
- Après : 1 module = 8 consommateurs, 1 ADR-002 = 1 format de workflow = 100 % traçable.
- Vue C4 Level 1 (Context Diagram) : qui parle à qui ?
  - Acteurs : analyste métier, développeur, auditeur Cour des Comptes, citoyen via dashboard public.
  - Systèmes externes : Kratos OIDC, Keto RBAC, Vault Secrets, YugabyteDB sub-projets, Metabase, etc.
- Vue C4 Level 2 (Container Diagram) : les 5 services internes.
  - `analytics-api` (Java) — orchestration + endpoints REST/gRPC.
  - `analytics-engine` (Rust + DataFusion) — exécution DAG.
  - `analytics-simulator` (Rust) — sandbox podman rootless.
  - `analytics-sandbox` (Rust) — exécution isolée.
  - `analytics-frontend` (Angular) — UI de construction.
- Choix d'architecture importants pour les métiers :
  - **Sovereignty** : KAYA (sovereign DB) + ARMAGEDDON (sovereign event bus) — pas de Redis/Kafka.
  - **Audit infalsifiable** : BLAKE3 (ADR-005).
  - **Workflows déclaratifs** : JSON Schema Draft-07 (ADR-002).
- Cycle de vie d'un workflow : `DRAFT` → `SIMULATED` → `DEPLOYED` → `ARCHIVED`.
- Notion de **semver** appliqué aux workflows (ADR-006).

### Slides à projeter

- S5 : Avant / Après (analogie « 8 cuisinières vs 1 cuisine centrale »).
- S6 : C4 Level 1 (Context Diagram).
- S7 : C4 Level 2 (Container Diagram).
- S8 : cycle de vie d'un workflow (4 états).
- S9 : exemple de semver `1.0.0-draft.1` → `1.0.0` → `1.1.0`.

### Exercice TP

**Énoncé** : projection du C4 Level 1. Chaque référent désigne sur le diagramme :

1. Où se situe son sous-projet ;
2. Le chemin (logique) qu'emprunte une requête analytique depuis sa donnée brute jusqu'au dashboard métier.

**Golden answer attendue** : référent HOSPITAL pointe RESUREP → YugabyteDB → analytics-api (lecture) → analytics-engine (exécution) → analytics-frontend (affichage) ou Metabase (export).

### Pièges courants

1. Penser que `analytics-engine` lit directement les bases des sous-projets (faux, il passe toujours par `analytics-api`).
2. Confondre **simulation** et **déploiement** (simulation = sandbox, déploiement = production).
3. Imaginer que Metabase remplace FASO-ANALYTICS-WORKFLOW (non, Metabase est un consommateur d'export).
4. Croire que les workflows sont « partagés » entre sous-projets (par défaut non — cloisonnement RBAC strict).
5. Oublier que le **frontend est facultatif** — un workflow peut être créé via le SDK Python également.

### Évaluation orale (3 questions)

1. Combien de services internes composent FASO-ANALYTICS-WORKFLOW ? Cite-les.
2. Quel est le format des workflows et quelle ADR le définit ?
3. Pourquoi un workflow `isCritical = true` exige-t-il 4-eyes ?

---

## Module M3 — Tour guidé de l'interface : palette, canvas DAG, paramètres (10:45 – 11:30)

### Objectif pédagogique

Découvrir la **disposition** de l'écran de construction de workflow (3 zones : palette à gauche, canvas central, panneau de paramètres à droite).

### Plan détaillé

- Connexion à l'environnement pré-prod via Kratos (chaque participant le fait depuis son poste).
- Navigation : Tableau de bord → « Nouveau Workflow » → page builder.
- Tour de la **palette** (gauche) : 5 catégories.
  - Sources : YugabyteDB, REST, CSV, S3, SurveyMonkey.
  - Transformations : filter, aggregate, computed, join, pivot, window, outlier, normalize, recode, group_by.
  - KPIs : KPI_DEFINITION.
  - Visualizations : 19 types regroupés par famille (barres, lignes/aires, distributions, cartes, tableaux).
  - Outputs : dashboard, email, export PDF/Excel/PPTX, Metabase.
- Le **canvas central** : grille snap-to-grid, glisser-déposer des blocs, connexions filaires entre étapes, mini-map en bas à droite.
- Le **panneau de paramètres** (droite) : formulaire `ngx-formly` dynamique selon le bloc sélectionné.
- Raccourcis clavier essentiels :
  - `Ctrl+S` : sauvegarder le brouillon.
  - `Ctrl+Z` / `Ctrl+Shift+Z` : annuler / refaire.
  - `Ctrl+Enter` : valider et passer en mode simulation.
  - `Suppr` : supprimer le bloc sélectionné.
- Notion de **brouillon local** vs **draft persisté** côté `analytics-api`.

### Slides à projeter

- S10 : capture annotée de l'écran builder (3 zones encadrées).
- S11 : palette détaillée (5 catégories).
- S12 : table des raccourcis clavier.

### Exercice TP

**Énoncé** : depuis l'interface, chaque participant ouvre le workflow d'exemple `01-bar-vertical-pagsi-region.json` (chargement depuis `schemas/examples/`). Il identifie à l'oral les 5 zones (source / pipeline / KPI / viz / output) et compte le nombre total de blocs dans le DAG.

**Golden answer attendue** : 1 source + 1 transformation (filter) + 1 transformation (aggregate) + 1 KPI + 1 viz + 1 output = **6 blocs** dans le DAG (le KPI est généralement représenté comme un nœud distinct relié au canvas final).

### Pièges courants

1. Ne pas voir la mini-map sur les grands DAG et se perdre en scroll.
2. Cliquer sur un bloc et modifier les paramètres sans s'apercevoir que le panneau de droite est en lecture seule (workflow `DEPLOYED`).
3. Ne pas sauvegarder régulièrement (le brouillon local expire après 4 heures).
4. Confondre **glisser-déposer un nouveau bloc** et **glisser un bloc existant** pour le déplacer dans le canvas.
5. Ne pas remarquer que le canvas a un **niveau de zoom** (Ctrl+molette).

### Évaluation orale (3 questions)

1. Combien de catégories y a-t-il dans la palette ?
2. Quel raccourci pour lancer la simulation ?
3. Où voit-on le mode lecture-seule pour un workflow déployé ?

---

## Module M4 — TP1 : Reproduire un workflow existant (PAGSI sur sample) (11:30 – 12:30)

### Objectif pédagogique

Mettre la main dans la pâte : reproduire à l'identique le workflow d'exemple `01-bar-vertical-pagsi-region.json` en partant d'un canvas vide.

### Plan détaillé

- Présentation du TP1 (cf. `exercises/tp1-reproduire-pagsi.md`).
- Distribution du sample (référence : `migrations/production/pagsi-volumes-region-cereale-v1.0.0.json`).
- Démonstration en miroir par l'instructeur : il crée la source pendant que les participants observent, puis ils reproduisent en autonomie.
- Phases successives :
  1. Glisser une **source YugabyteDB** sur le canvas, configurer le `connectionRef`.
  2. Ajouter une **transformation `filter`** : `campagne = '2025-2026'`.
  3. Ajouter une **transformation `aggregate`** : groupBy `region`, agrégation `SUM(volume_kg)`.
  4. Ajouter un **KPI** : SUM(volume_kg) / 1000 → unité `t`.
  5. Ajouter une **visualisation `BAR_VERTICAL`** : xField=`region`, yField=`volume_tonnes`.
  6. Ajouter un **output dashboard** avec `dashboardCode='pagsi_region'`.
- Sauvegarde + validation JSON Schema visible dans la console (panneau du bas).
- Lancement d'une simulation rapide avec sample `RANDOM` (taille 1000).

### Slides à projeter

- S13 : énoncé synthétique du TP1.
- S14 : screenshot de la cible finale (placeholder).

### Exercice TP

Voir [`exercises/tp1-reproduire-pagsi.md`](./exercises/tp1-reproduire-pagsi.md).

**Golden answer** : workflow JSON équivalent au fichier `schemas/examples/01-bar-vertical-pagsi-region.json` (la formulation peut varier sur les `id`, mais la sémantique doit être identique).

### Pièges courants

1. Oublier le bloc `filter` et lancer un agrégat sur l'historique complet (perf catastrophique).
2. Choisir `AVG` au lieu de `SUM` pour le volume_kg.
3. Mettre `yField = volume_kg` (donc en kg) au lieu de `volume_tonnes` (après conversion).
4. Oublier de définir l'`encoding` complet (le validateur refusera).
5. Sauvegarder en mode `DEPLOYED` sans passer par simulation — refus systématique (cycle de vie).

### Évaluation orale (3 questions)

1. Quel type d'agrégation pour un tonnage total ?
2. Pourquoi convertir kg → tonnes côté workflow et non côté viz ?
3. Comment vérifier la validité du workflow sans le déployer ?

---

## Pause déjeuner (12:30 – 14:00)

---

## Module M5 — Sources et transformations — déclarer, paramétrer (14:00 – 15:00)

### Objectif pédagogique

Maîtriser les **6 types de sources** et les **10 types de transformations** disponibles.

### Plan détaillé

- Sources : tableau récapitulatif.
  - `yugabyte` : connexion SQL classique, `connectionRef` pointe sur un secret Vault.
  - `rest` : appel HTTP avec authentification (mTLS / OIDC).
  - `csv` : fichier déposé sur un bucket S3 sovereign.
  - `s3` : objet S3 (sovereign : MinIO interne).
  - `surveymonkey` : webhook SurveyMonkey (cas RESUREP-26).
  - `kaya` : lecture depuis la sovereign in-memory DB.
- Transformations : démo de chaque type sur sample.
  - `filter` : expression booléenne SQL-like.
  - `aggregate` : `groupBy` + `aggregations` (alias, function, field).
  - `computed` : nouvelle colonne dérivée.
  - `join` : jointure entre 2 sources (INNER, LEFT, RIGHT, FULL).
  - `pivot` : passage long → large.
  - `window` : fonctions fenêtrées (ROW_NUMBER, LAG, LEAD).
  - `outlier` : détection (IQR, z-score) + action (REMOVE, FLAG, CAP).
  - `normalize` : min-max ou z-score.
  - `recode` : transformation valeur → valeur (mapping).
  - `group_by` : variante de `aggregate` sans agrégation (juste regrouper).
- Lancement TP2 (cf. `exercises/tp2-source-transformations.md`).

### Slides à projeter

- S15 : table « 6 sources × propriétés clés ».
- S16 : table « 10 transformations × paramètres requis ».
- S17 : ordre canonique d'application (filter → aggregate → computed → outlier → …).

### Exercice TP

Voir [`exercises/tp2-source-transformations.md`](./exercises/tp2-source-transformations.md).

**Golden answer** : workflow avec source `yugabyte`, 1 filter, 1 join, 1 aggregate, 1 computed. Le validateur doit accepter la version finale.

### Pièges courants

1. Mettre une transformation `aggregate` AVANT `filter` : la perf chute drastiquement.
2. Spécifier un `connectionRef` en clair sans préfixe `secret/` (Vault refuse).
3. Oublier le `alias` dans `aggregations` → erreur de validation.
4. Mettre une expression SQL invalide dans `filter` (le moteur DataFusion la rejette).
5. Faire un `join` sans définir la clé de jointure (`leftKey`, `rightKey`).

### Évaluation orale (3 questions)

1. Citez l'ordre canonique recommandé des transformations.
2. Que fait `outlier` avec l'action `CAP` ?
3. Pourquoi `connectionRef` est-il un pointeur Vault et non un littéral ?

---

## Module M6 — KPI : définition, format, polarité, target (15:00 – 16:00)

### Objectif pédagogique

Définir un KPI métier complet : expression, format d'affichage, polarité, seuils warning/critical, target optionnel.

### Plan détaillé

- Anatomie d'un KPI selon `schemas/workflow-v1.json` :
  - `id` (unique dans le workflow).
  - `label` (texte affiché).
  - `expression` (SQL agrégat — SUM, AVG, COUNT, MEDIAN, MIN, MAX, etc.).
  - `format` : `pattern` (compatible Excel), `unit`, `decimals`.
  - `polarity` : `more_better` | `less_better` | `neutral`.
  - `target` (optionnel) : valeur cible.
  - `thresholds` (optionnel) : `warningPct`, `criticalPct`.
  - `critical` (optionnel) : booléen, déclenche alerte temps réel.
- Démo de chaque champ sur un KPI réel : « Volume total distribué — PAGSI ».
- Lien polarité ↔ couleur :
  - `more_better` + valeur < target × warningPct/100 → couleur **orange**.
  - `more_better` + valeur < target × criticalPct/100 → couleur **rouge**.
  - `less_better` + valeur > target × warningPct/100 → couleur **orange**.
  - etc.
- Formats Excel canoniques : `#,##0`, `#,##0.0`, `#,##0%`, `0.00`, `#,##0 t`, `#,##0 FCFA`.
- Lancement TP3 (cf. `exercises/tp3-kpi-polarite.md`).

### Slides à projeter

- S18 : anatomie d'un KPI annoté.
- S19 : matrice polarité × seuil → couleur (5×5).
- S20 : 10 formats Excel les plus utilisés en BF (FCFA, t, ha, %, etc.).

### Exercice TP

Voir [`exercises/tp3-kpi-polarite.md`](./exercises/tp3-kpi-polarite.md).

**Golden answer** : 3 KPIs distincts définis correctement, avec leurs polarités et seuils, sur sub-projet HOSPITAL.

### Pièges courants

1. Définir un KPI sans `expression` (rejeté par schéma).
2. Mettre `polarity = neutral` quand le KPI a clairement un sens métier (perte d'information visuelle).
3. Confondre `thresholds.warningPct` (pourcentage de la cible) avec une valeur absolue.
4. Mettre `format.pattern = "0"` (entiers stricts) alors que le KPI est en pourcentage flottant.
5. Définir un KPI critique sans `target` (impossible de calculer les pourcentages de seuil).

### Évaluation orale (3 questions)

1. Comment exprime-t-on « 95 % de couverture » dans le `format` ?
2. Quel `polarity` pour un KPI « durée moyenne de traitement » ?
3. Que se passe-t-il à l'affichage si on définit `critical = true` sans `target` ?

---

## Pause (16:00 – 16:15)

---

## Module M7 — Visualisations — choisir le bon graphique (16:15 – 17:00)

### Objectif pédagogique

Connaître les **19 types de visualisation** disponibles et savoir choisir le bon selon le message métier.

### Plan détaillé

- Démo des 19 types projetés successivement, avec un cas métier illustratif pour chacun :
  1. `BAR_VERTICAL` — comparaisons de catégories (1 série).
  2. `BAR_HORIZONTAL` — top N (étiquettes longues).
  3. `BAR_GROUPED` — comparaison de catégories × dimension (n séries).
  4. `BAR_STACKED` — composition par catégorie.
  5. `BAR_100PCT` — composition relative en pourcentage.
  6. `PIE` — part d'un tout (≤ 6 segments recommandé).
  7. `DONUT` — variante PIE avec trou central.
  8. `HALF_DONUT` — variante esthétique demi-cercle.
  9. `LINE` — évolution temporelle simple (1 série).
  10. `LINE_MULTI` — évolution temporelle (n séries).
  11. `AREA` — surface sous courbe (volume).
  12. `AREA_STACKED` — composition cumulée temporelle.
  13. `SCATTER` — corrélation entre 2 variables continues.
  14. `BUBBLE` — corrélation entre 3 variables (x, y, taille).
  15. `HEATMAP` — densité sur grille (x catégorie, y catégorie, valeur couleur).
  16. `CHOROPLETH_BF` — carte choroplèthe régions/communes Burkina Faso.
  17. `GAUGE_SEMI` — jauge demi-cercle (1 KPI).
  18. `KPI_TILE` — tuile KPI plain (1 chiffre + libellé + comparaison N-1).
  19. `SPARKLINE` — micro-courbe inline (tendance compacte).
  20. `PIVOT_TABLE` — tableau croisé dynamique.
  21. `COMBO_DUAL_AXIS` — combinaison BAR + LINE avec 2 échelles (gauche/droite).
- Règles de choix :
  - **Comparer** → BAR_VERTICAL / BAR_HORIZONTAL.
  - **Composer** → BAR_STACKED / PIE / DONUT.
  - **Évoluer** → LINE / LINE_MULTI / AREA.
  - **Cartographier** → CHOROPLETH_BF.
  - **Suivre 1 chiffre** → KPI_TILE.
  - **Croiser 2 variables continues** → SCATTER.
  - **Comparer 2 unités différentes** → COMBO_DUAL_AXIS.
- Anti-patterns courants :
  - PIE avec > 6 segments (illisible).
  - LINE pour des catégories non ordonnées.
  - HEATMAP sans légende de couleurs.
  - BAR_VERTICAL avec 20 labels horizontaux (préférer BAR_HORIZONTAL).

### Slides à projeter

- S21 : matrice 19 viz × cas métier (1 vignette par viz).
- S22 : arbre de décision « quel graphique pour quoi ? ».
- S23 : 5 anti-patterns à proscrire.

### Exercice TP

**Énoncé** : projection de 5 cas métiers anonymisés. Chaque participant choisit individuellement le bon type de viz et justifie en 1 phrase.

**Golden answer attendue** :

1. « Évolution mensuelle du nombre d'admissions hospitalières par région » → `LINE_MULTI`.
2. « Top 10 communes en couverture vaccinale » → `BAR_HORIZONTAL`.
3. « Répartition des actes d'état civil par statut » → `DONUT`.
4. « Suivi temps réel du taux de remplissage des écoles primaires » → `KPI_TILE`.
5. « Stock physique (tonnes) et prix moyen (FCFA/kg) du maïs SONAGESS » → `COMBO_DUAL_AXIS`.

### Pièges courants

1. Choisir un PIE pour > 6 catégories.
2. Utiliser HEATMAP sans normaliser les couleurs.
3. Empiler des BAR_STACKED quand les ordres de grandeur diffèrent fortement.
4. Forcer un COMBO_DUAL_AXIS quand 1 seul axe suffit.
5. Oublier `style.showLegend = true` quand il y a > 3 séries.

### Évaluation orale (3 questions)

1. Quelle viz pour « part de marché de 3 grossistes » ?
2. Pourquoi `CHOROPLETH_BF` nécessite-t-il un champ géographique normalisé ?
3. Combien d'axes encode-t-on dans un BUBBLE ?

---

## Module M8 — Débriefing J1 + devoir maison (17:00 – 17:30)

### Objectif pédagogique

Synthèse de la journée + préparation au J2.

### Plan détaillé

- Tour de table inversé : chaque référent dit 1 chose apprise + 1 chose pas encore claire.
- Rappel des 8 modules vus.
- Annonce du devoir maison : **réécrire à la main** (en JSON) le workflow PAGSI vu en TP1, puis le valider avec `npx ajv-cli validate -s schemas/workflow-v1.json -d <fichier>`.
- Critères d'acceptation du devoir :
  - Validation Schema OK.
  - 1 source + 1 filter + 1 aggregate + 1 KPI + 1 viz + 1 output minimum.
  - Polarité du KPI explicitée.
- Distribution du planning J2 imprimé.
- Annonce du QCM final J2.

### Slides à projeter

- S24 : récap visuel des 8 modules J1.
- S25 : énoncé du devoir maison.
- S26 : programme synthétique J2.

### Exercice TP

**Devoir maison** : envoyer le fichier JSON à l'instructeur principal avant 23h59 le soir même via email institutionnel. L'instructeur valide ou demande correction avant J2 09:00.

### Pièges courants

1. Ne pas faire le devoir → blocage du TP4 J2.
2. Envoyer un workflow non validé par le schéma.
3. Oublier de mentionner la polarité.
4. Mettre un `connectionRef` factice non lisible par l'instructeur.
5. Confondre `metadata.semver` (la version) et `metadata.name` (l'identifiant).

### Évaluation orale (3 questions)

1. Quel est le critère d'acceptation principal du devoir ?
2. Combien d'éléments minimum dans la spec d'un workflow ?
3. À quelle heure doit être rendu le devoir ?

---

*Fin du Jour 1 — Fondamentaux.*
