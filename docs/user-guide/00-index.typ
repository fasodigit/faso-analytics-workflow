// FASO-ANALYTICS-WORKFLOW — Guide utilisateur v1.0
// Document maître : agrège les 5 chapitres + couverture + table des matières.
//
// Compilation :
//   typst compile docs/user-guide/00-index.typ /tmp/faso-guide-utilisateur.pdf
//
// Conventions de style : voir `_style.typ` (couleurs FASO + callouts).

// ============================================================================
// 1. Réglages globaux (langue, police, couleurs FASO, typographie)
// ============================================================================

#import "_style.typ": *

#set document(
  title: "FASO-ANALYTICS-WORKFLOW — Guide utilisateur v1.0",
  author: "FASO DIGITALISATION",
)

#set page(
  paper: "a4",
  margin: (top: 2.5cm, bottom: 2.5cm, left: 2.2cm, right: 2.2cm),
  numbering: "1 / 1",
  number-align: center,
)

// Police : Inter si disponible (poste analyste ou CI Typst), sinon fallback
// New Computer Modern Sans (par défaut Typst, toujours présent).
#set text(
  font: ("Inter", "New Computer Modern Sans"),
  size: 11pt,
  lang: "fr",
)

#set par(
  justify: true,
  leading: 0.65em,
)

#set heading(numbering: "1.1")

// Titres en violet FASO
#show heading.where(level: 1): it => [
  #pagebreak(weak: true)
  #v(0.5cm)
  #text(fill: violet, weight: "bold", size: 22pt, it.body)
  #v(0.3cm)
  #line(length: 100%, stroke: 1pt + violet)
  #v(0.4cm)
]
#show heading.where(level: 2): it => [
  #v(0.3cm)
  #text(fill: violet, weight: "bold", size: 15pt, it.body)
  #v(0.15cm)
]
#show heading.where(level: 3): it => [
  #v(0.2cm)
  #text(fill: violet.darken(20%), weight: "bold", size: 12pt, it.body)
]

// ============================================================================
// 2. Couverture
// ============================================================================

#set page(numbering: none)

#v(2cm)

// TODO(phase4-design) : remplacer par le logo officiel FASO lorsque l'asset
// `docs/assets/faso-logo.svg` aura été produit par la cellule design.
#align(center)[
  // #image("../assets/faso-logo.svg", width: 4cm)
  #block(
    fill: violet,
    inset: 16pt,
    radius: 8pt,
  )[
    #text(fill: white, weight: "bold", size: 28pt)[FASO]
    #linebreak()
    #text(fill: white, size: 11pt)[DIGITALISATION]
  ]
]

#v(2cm)

#align(center)[
  #text(size: 26pt, weight: "bold", fill: violet)[
    FASO-ANALYTICS-WORKFLOW
  ]
  #v(0.4cm)
  #text(size: 18pt, weight: "bold")[
    Guide utilisateur v1.0
  ]
  #v(0.6cm)
  #text(size: 13pt, style: "italic", fill: rgb("64748B"))[
    Pour analystes métier — sans prérequis technique
  ]
]

#v(3cm)

#align(center)[
  #block(width: 80%)[
    #set text(size: 10pt)
    #set align(left)
    #grid(
      columns: (auto, 1fr),
      column-gutter: 16pt,
      row-gutter: 8pt,
      [*Public visé :*],   [Analystes DREA, DGAEP, DGSV, DGESS, DGPER],
      [*Pré-requis :*],    [Aucun — un navigateur récent suffit],
      [*Durée totale :*],  [~2 heures de lecture + ~3 heures d'exercices],
      [*Date :*],          [#datetime.today().display("[day]/[month]/[year]")],
      [*Version :*],       [1.0],
      [*Édition :*],       [Phase 4 — Mai 2026],
    )
  ]
]

#v(1fr)

#align(center)[
  #text(size: 9pt, style: "italic", fill: rgb("64748B"))[
    © 2026 FASO DIGITALISATION — Burkina Faso #linebreak()
    Document confidentiel à usage interne — Diffusion sous accord du chef de projet
  ]
]

#pagebreak()

// ============================================================================
// 3. Avant-propos
// ============================================================================

#set page(numbering: "i")
#counter(page).update(1)

#heading(level: 1, numbering: none)[Avant-propos]

Ce guide accompagne les analystes métier des ministères burkinabè qui utilisent
FASO-ANALYTICS-WORKFLOW au quotidien : modéliser un workflow analytique sur des
données ministérielles (santé animale, sécurité alimentaire, santé publique,
état civil, sondages terrain), le simuler, le déployer en production puis le
suivre dans la durée.

Il a été conçu pour être lu *sans connaissance technique préalable*. Tous les
termes spécifiques sont définis au premier usage. Les concepts complexes
(échantillonnage stratifié, schema drift, chaîne d'audit BLAKE3) sont vulgarisés
puis approfondis.

#retenir[
  Si vous n'avez qu'une heure devant vous, lisez les chapitres 1 et 3.
  Vous saurez visualiser un dashboard existant et déclencher une simulation —
  cela couvre 80 % des cas d'usage quotidiens.
]

=== Conventions typographiques

- *Texte en gras* — élément d'interface (bouton, menu, champ).
- `Texte monospace` — nom technique (sous-projet, champ JSON, identifiant).
- _Texte en italique_ — vocabulaire métier introduit pour la première fois.
- Encadré violet "À retenir" — point essentiel à mémoriser.
- Encadré rouge "Attention" — risque de perte de données ou de blocage.

=== Comment nous joindre

| Canal | Adresse |
|---|---|
| Support N1 (analystes) | `support-analytics@faso-digitalisation.bf` |
| Support N2 (incidents) | `ops-analytics@faso-digitalisation.bf` |
| Sécurité | `secu@faso-digitalisation.bf` |
| Référent métier DREA | Voir annuaire interne |

#pagebreak()

// ============================================================================
// 4. Sommaire
// ============================================================================

#heading(level: 1, numbering: none)[Sommaire]

#outline(
  title: none,
  depth: 3,
  indent: auto,
)

#pagebreak()

// ============================================================================
// 5. Inclusion des 5 chapitres
// ============================================================================

#set page(numbering: "1")
#counter(page).update(1)

#include "01-prise-en-main.typ"
#include "02-creer-un-workflow.typ"
#include "03-simuler-comparer.typ"
#include "04-deployer-monitorer.typ"
#include "05-troubleshooting.typ"

// ============================================================================
// 6. Colophon
// ============================================================================

#pagebreak()

#heading(level: 1, numbering: none)[Colophon]

Ce guide a été produit avec :

- *Typst* 0.12+ — moteur de composition (cf. `docs/exports/analytics-dashboard-report.typ`).
- *Police Inter* — famille de polices sans-serif optimisée pour l'écran.
- *Couleurs charte FASO* — violet `#7C3AED`, vert `#16A34A`, ambre `#F59E0B`, rouge `#DC2626`.
- *Sources* — ADR-001 à ADR-006, ultraplan FASO-ANALYTICS-WORKFLOW §10.

Rédigé par la cellule documentation FASO DIGITALISATION en mai 2026. Cycle de
révision trimestriel. Les remontées d'erreurs ou de manques peuvent être
adressées à `support-analytics@faso-digitalisation.bf` ou ouvertes sous forme
d'issue Git dans le dépôt `faso-analytics-workflow`.

#align(right)[
  #text(size: 9pt, style: "italic", fill: rgb("64748B"))[
    Fin du document — #datetime.today().display("[day]/[month]/[year]")
  ]
]
