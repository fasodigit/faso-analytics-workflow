// Chapitre 3 — Simuler et comparer
// ~6 pages.
// Couvre Simulate-Before-Deploy (ADR-002), stratégies d'échantillonnage,
// WebSocket STOMP, lecture du diff, blocage de déploiement.

#import "_style.typ": *

#set heading(numbering: "1.1")

= Simuler et comparer

Avant de déployer un workflow modifié, FASO-ANALYTICS-WORKFLOW vous oblige à
le *simuler* sur un échantillon des données réelles et à comparer le résultat
avec la version actuellement déployée. Cette discipline — *Simulate-Before-
Deploy* (cf. ADR-002) — est ce qui garantit que vous ne casserez pas un
dashboard ministériel en cliquant sur le mauvais bouton.

== Pourquoi simuler avant de déployer

L'expérience accumulée sur des plateformes analytiques similaires montre que
*40 % des incidents* en production proviennent d'une modification qui semble
inoffensive mais qui :

- introduit silencieusement une cellule `NULL` non gérée,
- change le sens d'un KPI (par exemple unité kg -> tonnes sans le dire),
- explose la mémoire à cause d'un `JOIN` mal contraint,
- coupe la conformité d'un schéma de sortie consommé par Metabase.

La simulation rejoue le workflow modifié sur un échantillon des données
réelles, mesure les KPI et compare le résultat *ligne à ligne, colonne à
colonne* avec la dernière version `DEPLOYED`.

#retenir[
  Tant que vous n'avez pas validé une simulation, le bouton *Déployer* reste
  désactivé. Une simulation est valable *7 jours* ; au-delà, elle est marquée
  périmée et doit être relancée.
]

== Choisir une stratégie d'échantillonnage

Six stratégies d'échantillonnage sont disponibles. Le choix dépend de la
nature de vos données et de l'objectif de la simulation.

#table(
  columns: (auto, 1fr, 1fr),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Stratégie*][*Mécanisme*][*Quand l'utiliser*],
  [`RANDOM`],     [Tirage aléatoire uniforme.], [Premier test rapide ; données homogènes.],
  [`STRATIFIED`], [Échantillon respectant la distribution d'une colonne (région, sexe, etc.).], [Données déséquilibrées : un échantillon RANDOM raterait les régions peu peuplées.],
  [`FIRST_N`],    [N premières lignes (par tri implicite).], [Inspection de la structure ; debug rapide.],
  [`LAST_N`],     [N dernières lignes.], [Vérifier le dernier batch d'ingestion ; détection d'incidents récents.],
  [`PERIOD`],     [Toutes les lignes sur une période (date min - date max).], [Reproduire un incident lié à une date précise.],
  [`GOLDEN`],     [Jeu de données figé maintenu par le sub-projet.], [Test de non-régression : la version actuelle *doit* produire exactement les mêmes résultats.],
)

#retenir[
  Pour les workflows critiques (`pagsi`, `resurep`, `hospital`), la cellule
  data recommande la séquence : (1) un `FIRST_N=50` pour valider la structure,
  (2) un `STRATIFIED=1000` pour valider la distribution, (3) un `GOLDEN` pour
  garantir la non-régression.
]

=== Cas particulier : la stratégie GOLDEN

Chaque sub-projet maintient un *dataset golden* : un échantillon représentatif
de la production avec des valeurs cibles connues. Si la nouvelle version
produit des chiffres différents sur GOLDEN, c'est qu'elle introduit une
régression.

Le dataset golden est versionné dans Git
(`/datasets/golden/<sub-projet>/<version>.parquet`) et signé BLAKE3. Vous ne
pouvez pas le modifier depuis l'interface ; c'est volontaire.

== Lancer une simulation

Depuis l'écran d'édition du workflow, cliquez *Simuler* (icône loupe à
gauche du bouton *Déployer*). Une boîte de dialogue s'ouvre.

// #image("screenshots/03-simulation-dialog.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/03-simulation-dialog.png` — boîte
  de dialogue avec sélecteur stratégie + taille + version de comparaison._
]

=== Champs de la boîte de dialogue

- *Stratégie* — déroulant parmi les 6 valeurs ci-dessus.
- *Taille* (sauf GOLDEN) — nombre de lignes ; valeurs guides ci-dessous.
- *Comparaison* — version `DEPLOYED` à comparer (par défaut, la version
  actuelle).
- *Description* — texte libre, journalisé dans l'audit chain BLAKE3.

=== Taille recommandée par contexte

#table(
  columns: (auto, auto, 1fr),
  align: (left, right, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Contexte*][*Taille*][*Justification*],
  [Quick check structure],       [10],    [Vérifier que le DAG s'exécute sans erreur.],
  [Validation rapide KPI],       [50],    [Confirmer l'ordre de grandeur des indicateurs.],
  [Échantillon de revue métier], [1 000],  [Suffisant pour un coup d'œil expert ; ~30 s d'exécution.],
  [Pré-déploiement sérieux],     [5 000],  [Capture les déséquilibres rares ; ~2 min.],
  [Stress test],                 [10 000], [Mesure des performances ; ~5-10 min.],
)

#attention[
  Au-delà de 10 000 lignes, la simulation est rejetée par défaut (paramètre
  `simulation.max_rows` configurable par l'admin du sub-projet). Si vous avez
  besoin de plus, demandez à votre référent métier de l'augmenter — mais
  considérez plutôt un test en *SHADOW* (cf. chapitre 4).
]

== Suivre l'avancement via STOMP WebSocket

Dès la simulation lancée, une zone de suivi en temps réel apparaît. Elle se
met à jour automatiquement via une connexion *STOMP WebSocket* (pas besoin de
rafraîchir la page).

Les états s'enchaînent :

+ `QUEUED` — simulation en file d'attente.
+ `SAMPLING` — extraction de l'échantillon depuis la source.
+ `EXECUTING` — exécution du DAG dans la sandbox Podman rootless (cf. ADR-004).
+ `COMPUTING_KPIS` — calcul des KPI sur l'échantillon traité.
+ `DIFFING` — comparaison avec la version DEPLOYED.
+ `COMPLETED` — terminé ; le rapport est consultable.

Pendant `EXECUTING`, une barre de progression indique le pourcentage de nœuds
traités. Pour les workflows > 10 nœuds, un mini-graphe affiche en temps réel
les nœuds en cours, terminés (vert), en erreur (rouge).

#retenir[
  Si la connexion WebSocket est interrompue (perte de réseau, fermeture
  d'onglet), la simulation continue côté backend. À votre retour, le rapport
  reste disponible dans l'onglet *Historique des simulations*.
]

== Lire le diff vs version DEPLOYED

Une fois la simulation `COMPLETED`, cliquez *Voir le rapport*. Il est
structuré en 4 sections.

// #image("screenshots/03-diff-report.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/03-diff-report.png` — rapport de
  simulation avec onglets KPI / Schema / Alertes / Logs._
]

=== Section 1 — Comparaison des KPI

Tableau en 3 colonnes : KPI, valeur version DEPLOYED, valeur version simulée,
delta absolu, delta relatif (%), seuil de tolérance, décision automatique.

#table(
  columns: (1fr, auto, auto, auto, auto, auto),
  align: (left, right, right, right, right, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*KPI*][*DEPLOYED*][*Simulé*][*Δ %*][*Seuil*][*Décision*],
  [Volume total céréales (T)],   [1 412 000],  [1 423 800],  [+0,84 %],  [±2 %], [#text(fill: polarity-positive)[OK]],
  [Nombre régions couvertes],    [13],         [13],         [0 %],      [exact], [#text(fill: polarity-positive)[OK]],
  [Évolution YoY (%)],           [+4,2],       [+4,5],       [+0,3 pp],  [±0,5 pp], [#text(fill: polarity-positive)[OK]],
  [Taux complétude provinces],   [98,1 %],     [89,3 %],     [-8,8 pp],  [±3 pp], [#text(fill: polarity-critical)[BREACH]],
)

La décision automatique est :

- #text(fill: polarity-positive)[*OK*] — variation dans le seuil de tolérance.
- #text(fill: polarity-warning)[*WARN*] — variation hors seuil mais sans
  impact direct (KPI non critique).
- #text(fill: polarity-critical)[*BREACH*] — variation hors seuil sur un KPI
  critique ; le déploiement sera bloqué.

=== Section 2 — Schema diff

Affiche les changements de schéma entre la sortie attendue (version DEPLOYED)
et la sortie produite (version simulée). 5 types d'événements :

- *Colonne ajoutée* — nouvelle colonne dans la sortie (généralement bénin).
- *Colonne supprimée* — colonne disparue (impact aval, bloquant par défaut).
- *Type modifié* — type changé (`int` -> `bigint` toléré ; `int` -> `string`
  bloquant).
- *Colonne renommée* (détection Levenshtein) — l'éditeur vous propose 3
  options : `IGNORE` (créer une nouvelle colonne), `MAP_TO` (mapper l'ancien
  nom vers le nouveau), `USE` (utiliser le nouveau nom).
- *Précision modifiée* — par exemple `decimal(10,2)` -> `decimal(12,4)` (bénin
  par défaut).

=== Section 3 — Alertes & seuils

Récapitule tous les KPI en BREACH, tous les drifts non résolus, et le verdict
de `withinThreshold` global.

#attention[
  Le déploiement est *bloqué* dès que :

  - un seul KPI critique est en BREACH, ou
  - un drift `onColumnRemoved` est non résolu, ou
  - `withinThreshold = false` au niveau global.

  Le bouton *Déployer* reste désactivé. Il faut soit ajuster le workflow, soit
  résoudre les drifts explicitement, soit demander une dérogation 4-yeux
  (cf. chapitre 4).
]

=== Section 4 — Logs d'exécution

Trace complète de la simulation : nœuds exécutés, temps par nœud, mémoire
crête, lignes en entrée / sortie de chaque transformation. Les warnings
DataFusion (par exemple `INTEGER_OVERFLOW`, `IMPLICIT_CAST`) y figurent
explicitement.

== Bloquer le déploiement

Le verdict global est affiché en haut du rapport :

- *Vert* — `decision: APPROVED_FOR_DEPLOYMENT, withinThreshold: true`. Le
  bouton *Déployer* devient actif.
- *Ambre* — `decision: APPROVED_WITH_WARNINGS, withinThreshold: true`. Le
  déploiement est possible mais nécessite une justification écrite.
- *Rouge* — `decision: BLOCKED, withinThreshold: false`. Déploiement
  impossible sans correction.

#retenir[
  Le champ `decision.reasons[]` du rapport explicite *exactement* pourquoi un
  déploiement est bloqué (ex : `kpi.taux-completude.breach`,
  `schema.region_id.removed`). C'est aussi cette liste que vous lirez au
  chapitre 5 (troubleshooting) pour diagnostiquer un refus de déploiement.
]

== Exercice guidé : simuler le workflow créé au chapitre 2

+ Ouvrez le workflow `pagsi-volumes-region-cereale-exo`.
+ Cliquez *Simuler*.
+ Stratégie : `STRATIFIED` sur la colonne `region`.
+ Taille : `1000`.
+ Description : "Première simulation post-création".
+ Cliquez *Lancer la simulation*.
+ Observez les états s'enchaîner (~30-45 s).
+ À `COMPLETED`, cliquez *Voir le rapport*.
+ Vérifiez :
  - Section 1 : tous les KPI doivent être OK ou WARN (la version DEPLOYED
    n'existe pas encore — première création — donc la comparaison se fait vs
    *baseline théorique*).
  - Section 2 : aucun drift attendu.
  - Verdict global : *Vert*.

Le workflow est désormais prêt à être déployé. Direction le chapitre 4.

=== Question de contrôle

+ Quelle est la différence entre `RANDOM` et `STRATIFIED` ?
+ Pourquoi le dataset GOLDEN est-il en lecture seule depuis l'UI ?
+ Que se passe-t-il si `decision.withinThreshold = false` ?
