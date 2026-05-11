// Chapitre 1 — Prise en main
// ~6 pages.
// Public : analyste métier qui découvre l'outil. Aucun pré-requis technique.

#import "_style.typ": *

#set heading(numbering: "1.1")

= Prise en main

Ce premier chapitre vous présente le vocabulaire de base, l'interface du
constructeur visuel et les premiers pas après votre connexion. À la fin, vous
saurez ouvrir un workflow existant et lire son dashboard.

== Qu'est-ce qu'un workflow analytique ?

Un *workflow analytique* est une recette qui transforme des données brutes
(extraites d'une base ministérielle ou d'un questionnaire terrain) en
indicateurs et graphiques exploitables par un décideur. Cette recette est
modélisée sous forme d'un *graphe* : chaque étape est un nœud, chaque flèche
représente le passage des données d'une étape à la suivante.

Cinq familles de nœuds existent :

#table(
  columns: (auto, 1fr, 1fr),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Famille*][*Rôle*][*Exemples*],
  [Source], [Lit des données depuis un système externe.], [YugabyteDB, KoboToolbox, SurveyMonkey, Metabase, fichier CSV/XLSX/Parquet.],
  [Transformation], [Modifie ou enrichit les données.], [Filtre, agrégation, jointure, calcul de colonne, pivot, fenêtre glissante.],
  [KPI], [Calcule un indicateur clé chiffré.], [Volume total de céréales (tonnes), taux de prévalence (%), évolution annuelle (pp).],
  [Visualisation], [Représente graphiquement.], [Barre, donut, semi-donut, combo, choroplèthe, scatter, heatmap, jauge.],
  [Output], [Diffuse le résultat.], [Dashboard temps réel, export PDF, fichier Excel, slides PPTX, webhook, e-mail.],
)

#retenir[
  Un workflow se lit de gauche à droite : *source* en premier (les données
  d'entrée), *outputs* à la fin (ce que vous publiez aux décideurs). Tout ce
  qui se passe au milieu est de la transformation.
]

=== Vocabulaire à connaître

- *Source* — système qui fournit les données brutes (table SQL, formulaire
  Kobo, fichier déposé).
- *Transformation* — opération qui produit de nouvelles données à partir des
  précédentes (filtrer, agréger, joindre, calculer).
- *KPI* (Key Performance Indicator) — chiffre clé suivi dans le temps, avec
  une cible et une polarité (vert / ambre / rouge).
- *Visualisation* — représentation graphique (bar chart, donut, etc.) destinée
  à un humain.
- *Output* — canal de diffusion du résultat final.
- *Sub-projet* — espace de travail isolé d'un ministère
  (`pagsi`, `resurep`, `hospital`, `etat-civil`, `kobo-prefecture-eau`).
- *Version* — chaque workflow est versionné en SemVer (`1.0.0`, `1.1.0`, etc.).
- *DEPLOYED / DRAFT / SHADOW* — états possibles d'une version.

== Vue d'ensemble de l'interface

Le *constructeur visuel* est l'écran principal. Il est divisé en trois zones.

// Capture d'écran à produire en Phase 4 (formation référents).
// #image("screenshots/01-builder-overview.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/01-builder-overview.png` — vue
  d'ensemble du constructeur visuel avec annotations A / B / C._
]

=== Zone A — La palette (à gauche)

Liste catégorisée de tous les nœuds disponibles. Chaque entrée est
glisser-déposable vers le canvas. Les catégories repliables sont :

- *Sources* (5 connecteurs).
- *Transformations* (12 opérations).
- *KPIs* (1 template paramétrable).
- *Visualisations* (19 types — cf. chapitre 2).
- *Outputs* (6 canaux).

Un champ de recherche en haut de la palette permet de filtrer par nom (par
exemple `agg` pour ne voir que les agrégations).

=== Zone B — Le canvas (au centre)

Surface de travail où vous composez votre graphe. Les nœuds y sont posés et
reliés par des flèches. Quatre actions utiles :

- *Clic gauche* sur un nœud — sélectionne et ouvre la zone C à droite.
- *Glisser-déposer* depuis la palette — ajoute un nœud.
- *Clic droit* sur un nœud — menu contextuel (dupliquer, supprimer, isoler).
- *Roulette* — zoom in / out. *Espace + glisser* — déplace la vue.

Le canvas affiche en bas à droite un *mini-aperçu* du graphe complet
permettant de naviguer rapidement sur les workflows volumineux (> 20 nœuds).

=== Zone C — Le panneau de paramètres (à droite)

Apparaît dès qu'un nœud est sélectionné. Le formulaire est généré
automatiquement à partir du schéma JSON du workflow (cf. ADR-002). Les champs
obligatoires sont marqués d'un astérisque rouge ; les champs invalides sont
soulignés en rouge avec un message d'erreur.

#retenir[
  Le panneau de droite *s'auto-valide* à chaque frappe. Vous ne pouvez pas
  sauver un workflow qui contient des erreurs structurelles — le bouton
  *Enregistrer* reste grisé.
]

== Premier contact : connexion et navigation

=== Étape 1 — Connexion via Kratos

L'authentification est gérée par *ORY Kratos* (composant souverain déployé en
Burkina Faso). Trois méthodes sont possibles :

1. *Identifiant + mot de passe* — par défaut.
2. *WebAuthn* (clé Yubikey) — pour les rôles `approver` et `admin`.
3. *TOTP* (Google Authenticator, Aegis, Authy) — second facteur obligatoire
   pour tous les rôles à partir d'`editor`.

// #image("screenshots/01-login.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/01-login.png` — écran de connexion
  Kratos avec choix de méthode._
]

#attention[
  En cas de perte de votre second facteur (téléphone perdu, clé Yubikey
  cassée), *ne créez pas un nouveau compte*. Contactez immédiatement
  `support-analytics@faso-digitalisation.bf` qui déclenchera la procédure de
  récupération (réinitialisation à 4 yeux par un administrateur).
]

=== Étape 2 — Choix du sub-projet

Après connexion, un sélecteur de *sub-projet* apparaît. Chaque sub-projet
correspond à un domaine ministériel :

#table(
  columns: (auto, 1fr, auto),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Sub-projet*][*Domaine*][*Ministère*],
  [`pagsi`],               [Sécurité alimentaire — stocks céréaliers], [MAARH / DGPER],
  [`resurep`],             [Santé animale — épizooties], [MARAH / DGSV],
  [`hospital`],            [Indicateurs sanitaires hospitaliers], [MSHP / DGESS],
  [`etat-civil`],          [Naissances, décès, mariages], [MATD],
  [`kobo-prefecture-eau`], [Sondages terrain accès à l'eau], [Préfectures + DREA],
)

Vous ne voyez que les sub-projets pour lesquels votre compte est habilité. Si
un sub-projet manque, ouvrez un ticket auprès de votre référent métier.

=== Étape 3 — Liste des workflows

Une fois le sub-projet choisi, la page principale liste tous les workflows
déjà créés. Pour chacun :

- *Nom* + *version active* (la version `DEPLOYED`).
- *État* — `DRAFT`, `SIMULATING`, `DEPLOYED`, `SHADOW`, `ARCHIVED`.
- *Dernière modification* — date + auteur.
- *Actions* — Ouvrir, Dupliquer, Simuler, Voir le dashboard.

// #image("screenshots/01-workflow-list.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/01-workflow-list.png` — liste des
  workflows du sub-projet PAGSI avec colonnes Nom, Version, État, Dernière
  modification._
]

Le filtre en haut de la liste accepte :

- Recherche plein texte sur le nom.
- Filtre par état (multi-sélection).
- Filtre par auteur.
- Tri par dernière modification (par défaut, le plus récent en haut).

== Exercice guidé : visualiser le dashboard PAGSI déjà déployé

L'objectif est de prendre en main la navigation sans rien créer ni modifier.

+ *Connectez-vous* à `https://analytics.faso-digitalisation.bf` avec votre
  identifiant.
+ *Choisissez* le sub-projet `pagsi`.
+ Dans la liste des workflows, *cherchez* `pagsi-volumes-stocks-cereales` et
  cliquez sur la ligne.
+ Le panneau de droite affiche les métadonnées : version active (par exemple
  `2.3.0`), date de dernier déploiement, auteur de la dernière mise à jour,
  KPI principaux.
+ Cliquez sur le bouton *Voir le dashboard* (icône graphique en haut à
  droite). Un nouvel onglet s'ouvre sur le dashboard temps réel.
+ Le dashboard affiche typiquement :
  - 3 à 4 KPI tuiles en haut (volume total, évolution YoY, nombre de régions
    couvertes, dernière mise à jour),
  - une carte choroplèthe du Burkina Faso colorée par volume,
  - un bar chart horizontal des 5 céréales principales,
  - un combo chart double-axe (volume + évolution %),
  - un tableau pivot drill-down région × céréale.

// #image("screenshots/01-pagsi-dashboard.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/01-pagsi-dashboard.png` — dashboard
  PAGSI volumes de stocks céréaliers (KPI tiles + choroplèthe + bar + combo +
  pivot)._
]

#retenir[
  Tout dashboard est cliquable : un clic sur une barre ou une région filtre
  toutes les autres visualisations. C'est ce qu'on appelle le *cross-filter*.
  Pour désactiver : bouton *Réinitialiser les filtres* en haut.
]

=== Question de contrôle

Vous saurez que ce chapitre est acquis si vous pouvez répondre oui aux trois
questions suivantes :

+ Je peux nommer les 5 familles de nœuds (Source, Transformation, KPI,
  Visualisation, Output).
+ Je sais à quoi servent les zones A, B et C de l'interface.
+ J'ai réussi à ouvrir et naviguer dans le dashboard PAGSI.

Dans le chapitre suivant, vous *créerez* votre premier workflow de bout en
bout.
