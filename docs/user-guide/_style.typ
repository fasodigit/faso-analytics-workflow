// Style commun + helpers callout pour le guide utilisateur
// FASO-ANALYTICS-WORKFLOW.
//
// Importé par 00-index.typ (style global) ET par chaque chapitre via
// `#import "_style.typ": *`. La duplication n'est pas un problème : Typst
// dédoublonne les `#set` à effet idempotent.

// Couleurs FASO
#let violet            = rgb("7C3AED")
#let violet-soft       = rgb("F5F3FF")
#let polarity-positive = rgb("16A34A")
#let polarity-warning  = rgb("F59E0B")
#let polarity-critical = rgb("DC2626")
#let polarity-soft     = rgb("FEF2F2")
#let neutral-soft      = rgb("F8FAFC")
#let neutral-border    = rgb("E2E8F0")

// Callout "À retenir" — fond violet clair, bordure violet, libellé en gras.
#let retenir(body) = block(
  fill: violet-soft,
  inset: 8pt,
  radius: 4pt,
  width: 100%,
  stroke: 0.5pt + violet,
)[
  #text(fill: violet, weight: "bold")[À retenir] #linebreak()
  #body
]

// Callout "Attention" — fond rouge clair, bordure rouge, libellé en gras.
#let attention(body) = block(
  fill: polarity-soft,
  inset: 8pt,
  radius: 4pt,
  width: 100%,
  stroke: 1pt + polarity-critical,
)[
  #text(fill: polarity-critical, weight: "bold")[Attention] #linebreak()
  #body
]

// Réglages communs aux chapitres autonomes (utiles si on compile un chapitre
// seul pour relecture). Le 00-index.typ a ses propres `#set` plus complets.
#let apply-chapter-defaults() = {
  set text(size: 11pt, lang: "fr")
  set par(justify: true, leading: 0.65em)
  set heading(numbering: "1.1")
}
