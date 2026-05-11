// Chapitre 4 — Déployer et monitorer
// ~10 pages.
// Couvre : pré-requis, stratégies DIRECT/SHADOW/BLUE_GREEN, rollback < 60s,
// audit chain BLAKE3, 3 dashboards Grafana, 8 alertes Prometheus.

#import "_style.typ": *

#set heading(numbering: "1.1")

= Déployer et monitorer

Vous savez créer un workflow et le simuler. Ce chapitre couvre les deux
dernières étapes du cycle de vie : *déployer* en production (avec le choix de
la bonne stratégie) et *monitorer* son fonctionnement dans la durée.

== Pré-requis avant déploiement

Trois conditions doivent être réunies. L'éditeur les vérifie automatiquement
et désactive le bouton *Déployer* si l'une manque.

#table(
  columns: (auto, 1fr, 1fr),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Pré-requis*][*Détail*][*Comment satisfaire*],
  [Approbation 4-yeux], [Pour les workflows tagués `critical` (PAGSI / RESUREP / HOSPITAL) ou pour toute bump majeure SemVer.], [Demande envoyée à `approver-<sub-projet>@faso-digitalisation.bf` ; signature numérique WebAuthn requise.],
  [Simulation < 7 jours], [Le rapport de simulation doit dater de moins de 7 jours et avoir le verdict `APPROVED_FOR_DEPLOYMENT`.], [Relancer une simulation si périmée (cf. chapitre 3).],
  [`driftPolicy` explicite], [La policy ne doit pas être vide ni utiliser uniquement `IGNORE` sur toutes les règles.], [Ouvrir l'étape 7 du workflow et valider une combinaison réaliste.],
)

#attention[
  Une demande d'approbation 4-yeux comporte un *cooldown* de 15 minutes entre
  la création et la possibilité de cliquer *Déployer*. Cela laisse le temps à
  l'approbateur d'examiner le diff de simulation. Ne soumettez pas une
  approbation dans la précipitation : la traçabilité BLAKE3 conserve le délai
  de réflexion.
]

== Choisir la stratégie de déploiement

Trois stratégies sont disponibles. Le choix dépend du risque associé au
changement.

#table(
  columns: (auto, 1fr, auto),
  align: (left, left, center),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Stratégie*][*Mécanisme*][*Risque*],
  [`DIRECT`],     [Remplace immédiatement la version DEPLOYED ; bascule atomique.], [#text(fill: polarity-warning)[Moyen]],
  [`SHADOW`],     [La nouvelle version tourne *en parallèle* de l'ancienne pendant N heures, sans servir le trafic. Les KPI sont comparés en continu.], [#text(fill: polarity-positive)[Faible]],
  [`BLUE_GREEN`], [Deux environnements complets ; bascule par DNS / load balancer interne.], [#text(fill: polarity-positive)[Très faible]],
)

=== Arbre de décision

#block(
  fill: violet-soft,
  inset: 12pt,
  radius: 4pt,
  width: 100%,
  stroke: 0.5pt + violet,
)[
  *Le changement est-il un correctif simple (patch SemVer) ?* #linebreak()
  -> oui : `DIRECT` (faible risque, gain de réactivité). #linebreak()
  -> non : continuer. #linebreak() #linebreak()

  *Le workflow alimente-t-il un dashboard décisionnel critique
  (`pagsi`, `resurep`, `hospital`) ?* #linebreak()
  -> oui : `SHADOW` 24 h minimum, puis bascule. #linebreak()
  -> non : continuer. #linebreak() #linebreak()

  *Le changement modifie-t-il un schéma de sortie consommé par > 1 système
  externe (Metabase + webhook + e-mail simultanés) ?* #linebreak()
  -> oui : `BLUE_GREEN`, rollback DNS instantané. #linebreak()
  -> non : `SHADOW` 12 h suffisent.
]

== Lancer le déploiement

Cliquez *Déployer*. Une boîte de dialogue récapitule :

- Version de départ (`DEPLOYED` actuelle) et version cible.
- Stratégie choisie + durée si SHADOW.
- Vérifications (cases cochées en vert) :
  - Simulation OK et < 7 jours.
  - Approbations recueillies.
  - `driftPolicy` explicite.
  - Compatibilité Schema avec les outputs externes.

Au clic *Confirmer*, le déploiement est journalisé dans l'audit chain BLAKE3 :

- `actor_subject` — votre identifiant Kratos.
- `action` — `DEPLOY_WORKFLOW`.
- `payload` — version source, version cible, stratégie, durée, approbateurs.
- `blake3_self` — hash de l'entrée, chaîné au précédent.

// #image("screenshots/04-deploy-dialog.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/04-deploy-dialog.png` — boîte de
  dialogue avec récap + cases vérification + bouton Confirmer._
]

== Que se passe-t-il pendant SHADOW

Pendant la fenêtre SHADOW (typiquement 12-48 h), la plateforme :

+ *Exécute simultanément* l'ancienne et la nouvelle version sur les mêmes
  données entrantes.
+ *Calcule les KPI* des deux exécutions.
+ *Compare en temps réel* : delta % par KPI, schema drift, divergences au
  niveau ligne (par échantillonnage).
+ *Affiche un dashboard de comparaison* accessible via *Voir SHADOW* dans
  l'écran du workflow.
+ *Émet une métrique Prometheus* `faso_shadow_kpi_delta_abs_pct{kpi=...}` qui
  alimente l'alerte `shadow_kpi_divergence` (cf. plus loin).

La nouvelle version *ne sert pas le trafic utilisateur* : les dashboards, les
PDF, les webhooks, les e-mails continuent de tourner sur l'ancienne version.
SHADOW est une *observation*, pas un service.

#retenir[
  À la fin de la fenêtre SHADOW, vous recevez un *rapport de promotion* par
  e-mail. Vous devez explicitement cliquer *Promouvoir* pour basculer le
  trafic ; sans action, SHADOW s'arrête mais la version DEPLOYED reste
  inchangée.
]

== Rollback rapide

Toute version `DEPLOYED` peut être *rollbackée* vers une version précédente.
L'objectif est *< 60 secondes* entre le clic et la prise d'effet (cf.
ADR-001).

=== Procédure

+ Ouvrez le workflow.
+ Cliquez *Historique des versions* (icône horloge).
+ Sélectionnez la version cible (en général, la `1.x.y` immédiatement
  précédente).
+ Cliquez *Rollback*.
+ Saisissez le motif (champ obligatoire, journalisé).
+ Confirmez.

Le rollback est atomique : un instant `T`, la version `2.0.0` est servie ;
l'instant `T+1`, c'est la version `1.4.7`. Aucune requête n'est perdue.

#attention[
  Le rollback ne s'applique pas aux *outputs externes déjà émis*. Si la
  version `2.0.0` a envoyé un PDF erroné ce matin à 06:00, le rollback
  n'efface pas le PDF des boîtes mail. Émettez une *erratum* en suivant le
  runbook ops §"PDF erroné en circulation".
]

== Audit chain BLAKE3 — vérification offline

Chaque action (création, modification, simulation, approbation, déploiement,
rollback) est journalisée dans `audit_log` avec un hash BLAKE3-256 keyed Vault
chaîné. Ce mécanisme permet à un auditeur *externe* (Cour des comptes, audit
interne) de vérifier la chaîne *sans accès à la base de données*.

=== Procédure de vérification (3 étapes)

+ *Extraire la chaîne* — votre administrateur exporte l'audit_log au format
  JSONL :
  ```
  faso-analytics audit export --workflow <id> --output chain.jsonl
  ```
+ *Récupérer la clé Vault transit* — l'auditeur reçoit la clé `vault_key_v3`
  par canal sécurisé (PGP, hand-delivery).
+ *Valider* — exécuter le binaire de vérification fourni :
  ```
  faso-analytics audit verify --chain chain.jsonl --key vault_key_v3
  ```

Le binaire walk la chaîne dans l'ordre chronologique, recalcule chaque
`blake3_self` à partir du payload + `blake3_parent`, et signale toute
divergence (`MISMATCH at entry 4271 : expected ... got ...`).

#retenir[
  La conception « chaîne *par workflow* » (et non globale) signifie que vérifier
  l'audit d'un workflow ne nécessite *pas* d'extraire les 100 millions
  d'entrées de la table — seulement les ~ centaines à milliers d'entrées du
  workflow concerné. Voir ADR-005 §"CLI vérification" pour les détails.
]

== Observabilité — les 3 dashboards Grafana

Une fois en production, votre workflow est observé via 3 dashboards Grafana
préinstallés.

=== Dashboard 1 — `workflow-overview`

Vue d'ensemble *métier* — destinée aux analystes et chefs de projet.

- KPI tiles : nombre de workflows DEPLOYED, taux d'erreur 24 h, MTBF (mean
  time between failures), MTTR (mean time to recovery).
- Heatmap : exécutions par sub-projet × heure.
- Top 10 des workflows les plus consommateurs en mémoire / CPU.
- Liste des dernières simulations (succès / échecs).

=== Dashboard 2 — `engine-internals`

Vue *technique* — destinée aux administrateurs et ops.

- Latence p50 / p95 / p99 de chaque RPC gRPC (`Compile`, `Simulate`,
  `Deploy`, `Rollback`).
- Taux de cache hit DataFusion (cf. ADR-001).
- Pool de connexions YugabyteDB : utilisation, latence.
- Mémoire JVM (heap + non-heap), GC pauses.

=== Dashboard 3 — `sandbox-security`

Vue *sécurité* — destinée à la cellule sécu et aux ops.

- Tentatives d'évasion détectées (seccomp violations).
- Workflows ayant approché les limites de la sandbox (CPU > 80 %, mémoire >
  90 %, durée > 80 % du quota).
- Tokens Vault expirant dans les 24 h.
- Échecs d'authentification Kratos (par compte).

// #image("screenshots/04-grafana-overview.png", width: 100%)
#block(
  fill: neutral-soft,
  inset: 12pt,
  radius: 4pt,
  stroke: 0.5pt + neutral-border,
  width: 100%,
)[
  _Capture d'écran à insérer : `screenshots/04-grafana-overview.png` —
  dashboard workflow-overview avec KPI tiles + heatmap + top 10._
]

== Les 8 alertes Prometheus configurées

Toute violation déclenche une notification via le canal configuré
(`#ops-analytics` Mattermost + SMS d'astreinte pour les CRITIQUES).

#table(
  columns: (auto, 1fr, auto, auto),
  align: (left, left, center, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Alerte*][*Condition*][*Sévérité*][*Action attendue*],
  [`schema_drift_detected`],   [Drift non résolu pendant > 30 min.], [#text(fill: polarity-warning)[WARN]],     [Analyste : résoudre IGNORE/MAP_TO/USE.],
  [`simulation_timeout`],      [Simulation > 30 min sans `COMPLETED`.], [#text(fill: polarity-warning)[WARN]],     [Analyste : voir checklist diagnostic chapitre 5.],
  [`kpi_critical_breach`],     [KPI critical en breach > 15 min.], [#text(fill: polarity-critical)[CRIT]],   [Astreinte + analyste : investigation immédiate.],
  [`audit_chain_break`],       [Mismatch BLAKE3 détecté.], [#text(fill: polarity-critical)[CRIT]],   [Sécu + ops : isoler + investigation forensique.],
  [`rollback_failed`],         [Rollback n'aboutit pas en < 60 s.], [#text(fill: polarity-critical)[CRIT]],   [Astreinte : escalade niveau 3.],
  [`sandbox_oom_kill`],        [Sandbox tuée pour OOM.], [#text(fill: polarity-critical)[CRIT]],   [Ops : analyser le workflow + ajuster quotas.],
  [`vault_token_expiring`],    [Token Vault expirant < 24 h.], [#text(fill: polarity-warning)[WARN]],     [Ops : renouveler le token.],
  [`sandbox_resource_warning`], [CPU > 80 % ou mémoire > 90 % en sandbox.], [#text(fill: polarity-warning)[WARN]],     [Ops : surveiller, possible OOM imminent.],
)

#retenir[
  Les 4 alertes *CRITIQUES* (`kpi_critical_breach`, `audit_chain_break`,
  `rollback_failed`, `sandbox_oom_kill`) déclenchent un appel automatique sur
  le téléphone d'astreinte via le bridge SMS Orange Burkina. Ne désactivez
  *jamais* ces alertes sans accord du chef de projet.
]

== Exercice guidé : déployer en SHADOW 24 h puis observer

Objectif : prendre en main le cycle complet déploiement -> observation ->
promotion.

+ Ouvrez le workflow `pagsi-volumes-region-cereale-exo` (créé chapitre 2,
  simulé chapitre 3).
+ Vérifiez que la simulation date de moins de 7 jours.
+ Cliquez *Déployer*.
+ Choisissez stratégie : `SHADOW`.
+ Durée : 24 h.
+ Confirmez (les pré-requis sont automatiquement vérifiés ; pas d'approbation
  4-yeux requise pour ce workflow exo non `critical`).
+ Patientez 1-2 minutes ; cliquez *Voir SHADOW*.
+ Le dashboard de comparaison s'ouvre. Observez :
  - Les KPI tiles double colonne (DEPLOYED vs SHADOW).
  - Le graphique d'évolution du delta % au fil du temps.
  - Le compteur "lignes traitées" qui s'incrémente.
+ Ouvrez en parallèle Grafana, dashboard `workflow-overview`. Cherchez votre
  workflow dans la liste des exécutions.
+ Au bout de 24 h, vous recevrez le *rapport de promotion* par e-mail.
+ Cliquez *Promouvoir* dans l'UI. La bascule prend < 1 seconde.

=== Question de contrôle

+ Pourquoi SHADOW ne sert-il pas de trafic utilisateur ?
+ Quel est l'objectif de temps pour un rollback ?
+ Que signifie l'alerte `audit_chain_break` ? Quelle est l'action attendue ?

Au chapitre suivant, vous apprendrez à diagnostiquer les erreurs les plus
courantes.
