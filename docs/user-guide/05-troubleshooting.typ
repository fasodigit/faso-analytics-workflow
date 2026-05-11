// Chapitre 5 — Troubleshooting
// ~6 pages.
// Top 15 erreurs, drift, KPI breach, simulation timeout, déploiement refusé,
// lien runbook ops.

#import "_style.typ": *

#set heading(numbering: "1.1")

= Troubleshooting

Tôt ou tard, vous rencontrerez une erreur. Ce chapitre liste les 15
situations les plus courantes et explique, pour chacune, le diagnostic et la
résolution. Il se termine par un renvoi au *runbook ops* pour les cas hors
périmètre analyste.

== Top 15 erreurs courantes

Le mapping erreur API -> cause -> action ci-dessous couvre 95 % des tickets
support remontés en année N-1 sur des plateformes analytiques comparables.

#table(
  columns: (auto, 1fr, 1fr),
  align: (left, left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Code erreur*][*Cause probable*][*Action*],
  [`WF-001 INVALID_NAME`],          [Nom contenant majuscules / underscore / caractère spécial.], [Renommer en `kebab-case` strict.],
  [`WF-002 NAME_ALREADY_EXISTS`],   [Workflow existe déjà dans le sub-projet.], [Choisir un autre nom ou ouvrir l'existant.],
  [`WF-003 SCHEMA_VIOLATION`],      [JSON invalide vs `workflow-v1.json`.], [Lire le message ; le chemin pointe vers le champ fautif (ex : `kpis[0].polarity`).],
  [`WF-010 CYCLE_DETECTED`],        [Liaison créant un cycle dans le DAG.], [Supprimer la liaison fautive (encadrée en rouge).],
  [`WF-011 ORPHAN_NODE`],           [Nœud sans connexion entrante (sauf source).], [Relier ou supprimer le nœud.],
  [`WF-012 MISSING_REQUIRED_FIELD`], [Champ obligatoire vide.], [Compléter dans le panneau droit ; le champ est souligné rouge.],
  [`SIM-001 SOURCE_UNREACHABLE`],   [Connexion source impossible (réseau, credentials).], [Vérifier les credentials Vault ; tester la source seule via *Tester la source*.],
  [`SIM-002 TIMEOUT`],              [Simulation > 30 min sans `COMPLETED`.], [Voir checklist dédiée plus bas.],
  [`SIM-010 SAMPLE_TOO_LARGE`],     [Taille demandée > `simulation.max_rows` (10 000 par défaut).], [Réduire la taille ou demander élévation à l'admin.],
  [`SIM-020 GOLDEN_NOT_FOUND`],     [Aucun dataset golden pour ce sub-projet.], [Demander à la cellule data de produire un golden initial.],
  [`DEP-001 NO_VALID_SIMULATION`],  [Simulation absente ou périmée (> 7 j).], [Relancer une simulation ; relancer le déploiement.],
  [`DEP-002 APPROVAL_REQUIRED`],    [Workflow `critical` ou bump majeure.], [Envoyer demande à `approver-<sub-projet>@...` ; attendre signature.],
  [`DEP-003 DRIFT_UNRESOLVED`],     [Drift non résolu (`onColumnRemoved`, etc.).], [Ouvrir l'étape 7 ; choisir IGNORE / MAP_TO / USE.],
  [`DEP-010 WEBHOOK_DOMAIN_NOT_ALLOWED`], [Webhook vers domaine hors liste blanche.], [Demander ajout via cellule sécurité.],
  [`AUDIT-001 CHAIN_BREAK`],        [Hash BLAKE3 incohérent.], [#text(fill: polarity-critical)[Ne pas tenter de réparer]. Ouvrir incident sécu immédiatement.],
)

#retenir[
  Toutes les erreurs API sont retournées avec un champ `code` (ex `WF-003`)
  *et* un champ `message` localisé en français. Copiez le `code` dans votre
  ticket support : il accélère drastiquement la prise en charge.
]

== Drift détecté — que faire

Un drift est détecté à 2 moments : pendant la simulation (rapport section 2)
ou pendant l'exécution en production (alerte `schema_drift_detected`). Trois
options vous sont proposées dans l'éditeur.

=== Option 1 — IGNORE

Vous acceptez de perdre la colonne / ignorer le changement. Choisir si :

- La colonne n'était pas utilisée par les transformations en aval.
- Le KPI ne dépend pas de cette colonne.
- L'impact métier est nul.

#attention[
  Choisir `IGNORE` sur une colonne *utilisée* en aval propagera des `NULL`
  silencieux. Vérifiez le DAG : la colonne en question ne doit pas figurer
  dans le `Group by`, dans un `JOIN`, ni dans une expression KPI.
]

=== Option 2 — MAP_TO

Vous mappez l'ancienne colonne disparue vers une autre. Choisir si :

- C'est un *renommage* détecté (Levenshtein < 3).
- La nouvelle colonne porte la même sémantique (`region_name` -> `region`).

L'éditeur applique automatiquement le mapping sur toutes les références à
l'ancien nom dans le workflow.

=== Option 3 — USE

Vous adoptez le nouveau nom dans le workflow. Choisir si :

- C'est un renommage propre côté source (`statistique_centrale_regional` ->
  `region`) et vous voulez que le workflow reflète le nouveau vocabulaire.
- Vous n'avez pas peur de re-tester (oui, c'est un changement breaking).

== KPI critique en breach — workflow d'investigation

L'alerte `kpi_critical_breach` est arrivée sur le canal d'astreinte.
Procédure d'investigation à 5 étapes.

+ *Confirmer la breach* — ouvrez le dashboard du workflow. La tuile KPI doit
  être en rouge. Si elle est verte, c'est une fausse alerte (rare ; ouvrir un
  ticket).
+ *Comparer dans le temps* — sur la sparkline du KPI, la breach est-elle un
  pic ponctuel ou une dérive de fond ?
+ *Drill-down* — cliquez sur la tuile pour voir la décomposition par
  dimension (région, période, catégorie). Trouvez le sous-segment responsable.
+ *Vérifier la source* — la breach est-elle due à un changement de la donnée
  amont (incident d'ingestion, mauvais batch) ou à une modification du
  workflow (régression d'un déploiement récent) ?
+ *Décider* — soit la donnée est correcte (le KPI reflète une réalité métier
  ; informer le décideur), soit la donnée est incorrecte (rollback ou
  correction d'ingestion).

#retenir[
  Le délai depuis le dernier déploiement est affiché en haut du dashboard
  ("Déployé il y a 2 h 14 min"). Si la breach est apparue dans les heures qui
  suivent un déploiement, *suspectez d'abord une régression*. Le rollback (cf.
  chapitre 4) est l'outil approprié.
]

== Simulation qui dépasse le timeout

Si une simulation reste `EXECUTING` > 30 min, elle est tuée et marquée
`SIM-002 TIMEOUT`. Checklist de diagnostic :

#table(
  columns: (auto, 1fr),
  align: (left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Vérification*][*Détail*],
  [Taille de l'échantillon],   [10 000 lignes c'est le max ; ré-essayer avec 1 000.],
  [Présence d'un `JOIN`],      [Sans clé indexée, les JOINs explosent en complexité quadratique. Ajouter un filtre amont.],
  [Présence d'une `window`],   [Les fenêtres glissantes coûtent cher en mémoire ; réduire la taille de fenêtre.],
  [Logs sandbox],              [Onglet *Logs* du rapport : chercher `OUT_OF_MEMORY` ou `OOM_KILLED`.],
  [Source distante],           [SurveyMonkey / Metabase parfois lents en API ; tester en `RANDOM` 100 pour isoler.],
  [Heure de la journée],       [Le pool DataFusion peut être chargé en heures de pointe (08:00-10:00 et 14:00-17:00 Africa/Ouagadougou).],
)

#attention[
  *Ne lancez pas en rafale* 5 simulations identiques en espérant qu'une
  passe. Chaque simulation consomme une enveloppe sandbox et une transaction
  audit. Diagnostiquez d'abord, ré-essayez ensuite.
]

== Workflow refusé au déploiement

Le bouton *Déployer* reste désactivé ou retourne `DEP-XXX`. Le rapport de
simulation contient la clé `decision.reasons[]` qui explicite *exactement*
pourquoi.

=== Format de `decision.reasons[]`

```json
{
  "decision": {
    "verdict": "BLOCKED",
    "withinThreshold": false,
    "reasons": [
      "kpi.taux-completude-provinces.breach",
      "schema.region_id.removed",
      "approval.4-eyes.missing"
    ]
  }
}
```

Chaque raison suit la syntaxe `<famille>.<id>.<sous-cause>`. Famille :

- `kpi` — KPI en breach.
- `schema` — drift non résolu.
- `approval` — approbation manquante.
- `simulation` — simulation périmée ou absente.
- `policy` — `driftPolicy` invalide.
- `webhook` — domaine non whitelisté.

=== Lecture pratique

Listez les raisons une par une et traitez-les :

#table(
  columns: (auto, 1fr),
  align: (left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Raison*][*Comment résoudre*],
  [`kpi.<id>.breach`],          [Ouvrir le rapport de simulation, voir le delta, corriger le workflow ou ajuster la cible/seuil.],
  [`schema.<col>.removed`],     [Choisir IGNORE / MAP_TO / USE (cf. ci-dessus).],
  [`schema.<col>.type_changed`], [Ajouter un nœud `computed` qui caste explicitement (`CAST(x AS BIGINT)`).],
  [`approval.4-eyes.missing`],  [Envoyer demande à l'approbateur.],
  [`simulation.outdated`],      [Relancer la simulation.],
  [`simulation.missing`],       [Lancer une simulation.],
  [`policy.empty`],             [Renseigner les 4 règles de `driftPolicy`.],
  [`webhook.<domain>.not_allowed`], [Demander whitelist à la cellule sécurité.],
)

#retenir[
  Le champ `decision.reasons[]` est *exhaustif* : si vous résolvez tout ce
  qu'il liste, le déploiement passera. Aucune cause cachée. C'est l'un des
  contrats de fiabilité explicités dans le `Compile()` RPC (cf. ADR-001).
]

== Que faire en dehors de mon périmètre

Certains incidents dépassent le rôle d'analyste métier. Ils sont décrits dans
le *runbook ops* :

#table(
  columns: (auto, 1fr),
  align: (left, left),
  stroke: 0.5pt + rgb("E2E8F0"),
  table.header[*Cas*][*Section runbook*],
  [Mismatch chaîne audit BLAKE3],  [`docs/runbook/runbook-ops.md` §"Audit chain forensic"],
  [Sandbox OOM répétée],           [`docs/runbook/runbook-ops.md` §"Sandbox OOM diagnostic"],
  [Token Vault expiré],            [`docs/runbook/runbook-ops.md` §"Rotation Vault transit"],
  [Rollback échoue],               [`docs/runbook/runbook-ops.md` §"Rollback failure escalation"],
  [Incident sécu (intrusion suspectée)], [`docs/runbook/runbook-ops.md` §"Incident sécurité — playbook"],
  [Restauration depuis sauvegarde], [`docs/runbook/runbook-ops.md` §"DR — restore from backup"],
)

#attention[
  *Ne tentez pas* de résoudre un incident sécu (`AUDIT-001 CHAIN_BREAK`, accès
  inhabituel, exfiltration suspectée) seul. Contactez immédiatement
  `secu@faso-digitalisation.bf` et préservez les preuves (logs, captures,
  horodatages). Chaque minute compte.
]

== Synthèse — les 3 réflexes à acquérir

#block(
  fill: violet-soft,
  inset: 12pt,
  radius: 4pt,
  width: 100%,
  stroke: 0.5pt + violet,
)[
  + *Lire d'abord, agir ensuite.* Le code erreur, le `decision.reasons[]`, les
    logs sandbox contiennent presque toujours la réponse.
  + *Reproduire avant de fixer.* Une erreur intermittente n'est pas la même
    chose qu'une erreur déterministe ; le diagnostic diffère.
  + *Documenter chaque résolution.* Le champ "Description" de chaque
    simulation et chaque déploiement est journalisé BLAKE3. Soyez précis : le
    futur vous (ou un collègue) vous remerciera.
]

=== Question de contrôle

+ Citez 3 codes erreur fréquents et leur résolution.
+ Quelle est la différence entre `MAP_TO` et `USE` pour résoudre un drift ?
+ Où trouve-t-on la décomposition d'un refus de déploiement ?

Vous êtes désormais autonome sur l'ensemble du cycle de vie d'un workflow
analytique FASO. Pour aller plus loin : ateliers trimestriels animés par la
cellule data, et fiches pratiques par sub-projet disponibles sur l'intranet
`docs.faso-digitalisation.bf/analytics`.
