// Chapitre 2 — Créer un workflow
// ~10 pages.
// Couvre les 7 étapes de création + table des 19 viz + exercice guidé PAGSI.

#import "_style.typ": *

#set heading(numbering: "1.1")

= Créer un workflow

Vous savez naviguer dans l'interface ; place à la pratique. Ce chapitre vous
guide pas à pas dans la création complète d'un workflow analytique, depuis le
choix d'une source jusqu'à la définition des règles de drift. Comptez 45 à 60
minutes pour réaliser l'exercice en fin de chapitre.

== Étape 1 — Créer le workflow et le nommer

Cliquez sur le bouton *+ Nouveau workflow* en haut à droite de la liste. Une
boîte de dialogue demande trois informations :

#table(
  columns: (auto, 1fr, auto),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Champ*][*Description*][*Format*],
  [Nom],         [Identifiant unique au sein du sub-projet.],            [`kebab-case`],
  [Sub-projet], [Espace de travail (verrouillé par le sélecteur amont).], [Liste déroulante],
  [Version],    [Numéro de version initial.],                            [`SemVer 1.0.0`],
)

#retenir[
  Le nom est en *kebab-case strict* : minuscules, chiffres, tirets uniquement.
  Exemples valides : `pagsi-volumes-region`, `resurep-prevalence-bovins-2026`.
  Exemples invalides : `Pagsi Volumes`, `pagsi_volumes`, `pagsi-2026!`.
]

L'unicité est vérifiée : si le nom existe déjà dans le sub-projet, vous obtenez
un message d'erreur "nom déjà pris" et le bouton *Créer* reste désactivé.

=== SemVer contraint (ADR-006)

Le numéro de version suit le standard *SemVer 2.0* avec une contrainte :

- *Majeure* incrémentée si le schéma de sortie change (colonnes renommées,
  supprimées, type modifié).
- *Mineure* incrémentée si une fonctionnalité est ajoutée (nouveau KPI,
  nouvelle visualisation, nouvelle source).
- *Patch* incrémentée pour les corrections sans impact contractuel.

#attention[
  Une *bump majeure* (par exemple `1.4.2` -> `2.0.0`) déclenche une approbation
  4-yeux obligatoire avant déploiement (cf. chapitre 4). Anticipez ce surcoût
  organisationnel en regroupant les changements breaking dans une même
  release.
]

// #image("screenshots/02-new-workflow.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/02-new-workflow.png` — boîte de
  dialogue de création avec champs nom + sub-projet + version._
]

== Étape 2 — Choisir une source

Glissez un nœud source depuis la palette vers le canvas. Cinq connecteurs sont
disponibles :

#table(
  columns: (auto, 1fr, 1fr),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Source*][*Quand l'utiliser*][*Configuration clé*],
  [`yugabyte`],      [Données ministérielles déjà industrialisées.],   [Schéma + table + filtre SQL.],
  [`kobo`],          [Données terrain de KoboToolbox.],                [URL formulaire + token API.],
  [`surveymonkey`],  [Sondages SurveyMonkey externes.],                [Survey ID + token OAuth.],
  [`metabase`],      [Réutiliser une question Metabase existante.],    [Question ID + paramètres.],
  [`upload`],        [Fichier ponctuel.],                              [CSV / XLSX / Parquet jusqu'à 500 Mo.],
)

Cliquez sur le nœud pour ouvrir la zone C de configuration. Pour `yugabyte`,
vous devez choisir le schéma (`pagsi`, `resurep`, etc.) puis la table parmi la
liste auto-complétée. Un aperçu (5 premières lignes) s'affiche en bas du
panneau pour confirmer que la connexion fonctionne.

#retenir[
  Pour les sources `kobo` et `surveymonkey`, le token API est stocké dans Vault
  Transit (souverain Burkina Faso). Vous ne le saisissez *qu'une fois* par
  sub-projet ; les workflows suivants le récupèrent automatiquement.
]

== Étape 3 — Glisser-déposer des transformations

Les 12 transformations disponibles couvrent l'essentiel du data wrangling.

#table(
  columns: (auto, 1fr),
  align: (left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Transformation*][*Effet*],
  [`filter`],     [Garde uniquement les lignes répondant à une expression booléenne.],
  [`aggregate`],  [Calcule SUM / AVG / MIN / MAX / COUNT par groupe.],
  [`computed`],   [Crée une nouvelle colonne via une expression DataFusion.],
  [`join`],       [Fusionne deux flux sur une clé commune (INNER / LEFT / RIGHT).],
  [`pivot`],      [Transforme des lignes en colonnes (long -> large).],
  [`unpivot`],    [Inverse de pivot (large -> long).],
  [`window`],     [Calcul glissant (moyenne mobile, lag, lead, rank).],
  [`outlier`],    [Détecte ou supprime les outliers (IQR, Z-score).],
  [`normalize`],  [Met à l'échelle (min-max, z-score, log).],
  [`recode`],     [Recode des valeurs (table de correspondance).],
  [`group_by`],   [Groupage simple sans agrégation immédiate.],
  [`sort`],       [Trie par une ou plusieurs colonnes.],
)

Pour relier deux nœuds : passez la souris sur la pastille de droite du nœud
source jusqu'à voir apparaître un point bleu, puis cliquez-glissez vers le
nœud cible. Une flèche se crée.

#attention[
  Les *boucles* sont interdites : si vous tentez de créer un cycle (A -> B -> A),
  l'éditeur refuse la liaison et affiche en rouge "Cycle détecté". Le graphe
  doit être un *DAG* (Directed Acyclic Graph).
]

=== Exemple : filtre régional

Pour ne garder que les lignes de la région du Centre, le nœud `filter` reçoit
l'expression :

```
region = 'CENTRE'
```

Les expressions utilisent la syntaxe SQL DataFusion (cf. ADR-001). Les
fonctions disponibles : opérateurs arithmétiques, `LIKE`, `IN`, `BETWEEN`,
fonctions de chaîne (`UPPER`, `LOWER`, `TRIM`), fonctions de date
(`DATE_TRUNC`, `EXTRACT`, `NOW`), opérateurs logiques (`AND`, `OR`, `NOT`).

== Étape 4 — Définir des KPI

Glissez un nœud `kpi` après la dernière agrégation. Le panneau de configuration
demande 5 champs :

#table(
  columns: (auto, 1fr),
  align: (left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Champ KPI*][*Détail*],
  [Label],      [Nom affiché ; libre, en français.],
  [Expression], [Expression DataFusion produisant un scalaire ; ex. `SUM(volume_tonnes)`.],
  [Format],     [`number`, `percent`, `currency_xof`, `duration_seconds`, `bytes`.],
  [Polarité],   [`POSITIVE` (vert), `WARNING` (ambre), `CRITICAL` (rouge), `NEUTRAL`.],
  [Cible],      [Valeur attendue ; le dashboard compare valeur réelle vs cible.],
)

#retenir[
  La polarité conditionne la couleur de la tuile KPI dans le dashboard et
  pilote les alertes Prometheus (cf. chapitre 4). Un KPI `CRITICAL` en breach
  envoie une alerte SMS aux astreintes ; un KPI `WARNING`, une simple
  notification e-mail.
]

== Étape 5 — Ajouter des visualisations

19 types de visualisations sont disponibles. Le tableau ci-dessous présente
chacune avec un mini-aperçu textuel et son cas d'usage typique.

#table(
  columns: (auto, 1fr, 1fr),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Type*][*Mini-aperçu*][*Usage typique*],
  [`BAR_VERTICAL`],     [Barres dressées côte à côte.],                     [Comparer une mesure entre catégories (5-15 items).],
  [`BAR_HORIZONTAL`],   [Barres couchées de la gauche vers la droite.],     [Idem, lisible pour longues étiquettes.],
  [`BAR_STACKED`],      [Barres empilées par catégorie.],                   [Décomposer un total en sous-parties.],
  [`BAR_100PCT`],       [Barres empilées normalisées à 100 %.],             [Comparer les *parts relatives* entre groupes.],
  [`PIE`],              [Camembert classique.],                             [Répartition simple, max 5 segments.],
  [`DONUT`],            [Camembert troué au centre.],                       [Idem PIE, avec valeur centrale possible.],
  [`HALF_DONUT`],       [Demi-cercle.],                                     [Jauge d'avancement, taux d'atteinte.],
  [`LINE`],             [Courbe simple dans le temps.],                     [Évolution d'une seule métrique.],
  [`LINE_MULTI`],       [Plusieurs courbes superposées.],                   [Comparer plusieurs séries dans le temps.],
  [`AREA`],             [Zone remplie sous une courbe.],                    [Accentuer un volume cumulé.],
  [`AREA_STACKED`],     [Zones empilées.],                                  [Décomposer un total dans le temps.],
  [`COMBO_DUAL_AXIS`],  [Barres + courbe avec 2 axes Y.],                   [Mêler volume (gauche) et taux (droite).],
  [`SCATTER`],          [Nuage de points.],                                 [Détecter une corrélation entre 2 variables.],
  [`BUBBLE`],           [Scatter avec taille du point variable.],           [Ajouter une 3ème dimension au scatter.],
  [`HEATMAP`],          [Grille colorée.],                                  [Densité d'événements (jour × heure, région × mois).],
  [`CHOROPLETH_BF`],    [Carte du Burkina Faso colorée.],                   [Indicateur par région ou province.],
  [`GAUGE_SEMI`],       [Jauge semi-circulaire.],                           [Taux d'atteinte d'un objectif unique.],
  [`KPI_TILE`],         [Tuile chiffre + sparkline.],                       [Affichage condensé d'un KPI principal.],
  [`PIVOT_TABLE`],      [Tableau pivot avec drill-down.],                   [Exploration multi-dimensionnelle.],
)

Pour chaque visualisation, vous renseignez :

- *Titre* et *sous-titre* — affichés sur le dashboard.
- *Encodage* — quelle colonne sur l'axe X, quelle colonne sur l'axe Y, quel
  champ pour la couleur, quel champ pour la taille (si bubble).
- *Source* — quel nœud du graphe fournit les données (le dernier nœud
  d'agrégation par défaut).

#retenir[
  Pour `COMBO_DUAL_AXIS`, le schéma exige explicitement deux configurations :
  `leftAxis` (typiquement barres) et `rightAxis` (typiquement courbe). C'est
  vérifié par le validateur JSON Schema avant sauvegarde.
]

== Étape 6 — Configurer les outputs

Glissez un ou plusieurs nœuds `output` en fin de graphe. Six canaux de
diffusion sont supportés :

#table(
  columns: (auto, 1fr),
  align: (left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Output*][*Détail*],
  [`dashboard`], [Dashboard temps réel intégré (par défaut, toujours actif).],
  [`pdf`],       [Export PDF Typst, planifié quotidiennement ou à la demande.],
  [`excel`],     [Export `.xlsx` multi-feuilles via Apache POI.],
  [`pptx`],      [Slides PowerPoint, 1 slide par visualisation.],
  [`metabase`],  [Publication automatique vers une question Metabase.],
  [`webhook`],   [POST JSON vers une URL externe (notification, intégration).],
  [`email`],     [Envoi e-mail aux destinataires, avec PDF en pièce jointe.],
)

Pour chaque output, configurez la *cadence* (`on_demand`, `daily`, `weekly`,
`monthly`, `cron`) et les *destinataires* (e-mails, identifiants Metabase,
URL webhook).

#attention[
  Les webhooks sortants doivent pointer vers un domaine *liste blanche*
  validée par la cellule sécurité. Toute tentative vers un domaine inconnu est
  rejetée à la sauvegarde avec l'erreur `WEBHOOK_DOMAIN_NOT_ALLOWED`.
]

== Étape 7 — Définir la driftPolicy

Le *schema drift* est une modification du schéma de la source (colonne
renommée, supprimée, type changé) entre deux exécutions. La `driftPolicy`
explicite ce qu'il faut faire dans 4 scénarios :

#table(
  columns: (auto, 1fr, 1fr),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Règle*][*Cas couvert*][*Valeurs possibles*],
  [`onColumnRemoved`],         [Une colonne utilisée a disparu de la source.], [`FAIL`, `WARN`, `IGNORE`],
  [`onColumnTypeChanged`],     [Le type d'une colonne a changé (int -> string par exemple).], [`FAIL`, `COERCE`, `WARN`],
  [`onColumnRenamedDetected`], [Renommage probable détecté (Levenshtein < 3).], [`MAP_AUTO`, `MAP_CONFIRM`, `FAIL`],
  [`onNewColumnAdded`],        [Nouvelle colonne dans la source (pas utilisée).], [`IGNORE`, `WARN`],
)

#retenir[
  Les recommandations par défaut, validées par la cellule data :
  `onColumnRemoved = FAIL`, `onColumnTypeChanged = FAIL`,
  `onColumnRenamedDetected = MAP_CONFIRM`, `onNewColumnAdded = IGNORE`.
  Cette configuration *bloque* tout déploiement qui casserait silencieusement
  le workflow.
]

== Exercice guidé : recréer le workflow PAGSI volumes par région × céréale

Objectif : produire, de zéro, un workflow qui agrège les volumes de stocks
céréaliers par région et par type de céréale, avec 3 visualisations et un PDF
hebdomadaire.

=== Étape 1 — Création

+ Cliquez *+ Nouveau workflow*.
+ Nom : `pagsi-volumes-region-cereale-exo`.
+ Sub-projet : `pagsi` (présélectionné).
+ Version : `1.0.0`.
+ Cliquez *Créer*.

=== Étape 2 — Source

+ Depuis la palette, glissez un nœud `yugabyte` sur le canvas.
+ Cliquez le nœud. Dans le panneau de droite :
  - Schéma : `pagsi`.
  - Table : `pagsi_stocks_cereales_2026`.
  - Filtre SQL (optionnel) : `annee = 2026 AND trimestre IN (1, 2)`.
+ L'aperçu affiche 5 lignes typées (`region`, `province`, `cereale`,
  `volume_tonnes`, `annee`, `trimestre`).

=== Étape 3 — Transformation

+ Glissez un nœud `aggregate` à droite de la source ; reliez-les.
+ Configuration :
  - Group by : `region`, `cereale`.
  - Agrégation : `SUM(volume_tonnes) AS volume_total`.
+ Glissez un nœud `sort` après l'aggregate ; trier par `volume_total DESC`.

=== Étape 4 — KPI

+ Glissez un nœud `kpi` après le sort.
+ Label : "Volume total céréales (T)".
+ Expression : `SUM(volume_total)`.
+ Format : `number`.
+ Polarité : `POSITIVE`.
+ Cible : `1500000`.

=== Étape 5 — Visualisations

+ `BAR_HORIZONTAL` — encodage X = `volume_total`, Y = `region`.
+ `CHOROPLETH_BF` — couleur = `volume_total`, granularité = `region`.
+ `PIVOT_TABLE` — lignes = `region`, colonnes = `cereale`, valeur = `volume_total`.

=== Étape 6 — Outputs

+ `dashboard` — cadence `realtime` (par défaut).
+ `pdf` — cadence `weekly` (lundi 06:00 Africa/Ouagadougou), destinataires =
  `directeur-pagsi@maarh.gov.bf`.
+ `excel` — cadence `on_demand`.

=== Étape 7 — driftPolicy

+ Acceptez les valeurs par défaut.
+ Cliquez *Enregistrer*.

Le workflow est désormais en état `DRAFT` et apparaît dans la liste avec un
ruban gris. *Ne le déployez pas encore* : nous allons d'abord le simuler au
chapitre 3.

#retenir[
  Sauvegarder en `DRAFT` ne consomme aucune ressource côté backend : aucun job
  n'est lancé tant que vous n'avez pas explicitement déclenché une simulation
  ou un déploiement.
]

=== Récapitulatif du chapitre

Vous avez vu les 7 étapes complètes de création d'un workflow :

+ Création + nommage en `kebab-case` + SemVer.
+ Choix d'une source parmi 5 connecteurs.
+ Glisser-déposer de transformations (12 disponibles).
+ Définition de KPIs avec polarité.
+ Ajout de visualisations parmi 19 types.
+ Configuration d'outputs multi-canaux.
+ Définition de la `driftPolicy`.

Au prochain chapitre, vous simulerez ce workflow sur un échantillon avant tout
déploiement réel.
