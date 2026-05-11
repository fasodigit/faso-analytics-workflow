// FASO-ANALYTICS-WORKFLOW — Typst dashboard report template
//
// Invocation depuis le PdfTypstAdapter Java :
//
//   typst compile --input data=<absolute-path-to-data.json> \
//                 docs/exports/analytics-dashboard-report.typ \
//                 /tmp/faso-analytics/exports/export-<jobId>.pdf
//
// Le fichier data.json est produit par PdfTypstAdapter#buildDataJson(ExportRequest) et a
// la forme :
//
//   {
//     "metadata":       { "name": "...", "subProject": "...", "semver": "1.0.0" },
//     "kpis":           [ { "id": "...", "label": "...", "value": 123, "unit": "ha" } ],
//     "visualizations": [
//       {
//         "id": "...", "title": "...", "subtitle": "...", "type": "bar|line|table",
//         "encoding": { "headers": ["col1","col2"], "rows": [[v1,v2], ...] }
//       }
//     ],
//     "period": "2025-Q4"   // optionnel
//   }

#let data-path = sys.inputs.at("data", default: "data.json")
#let data = json(data-path)

#set page(paper: "a4", margin: 2cm)
#set text(size: 11pt, lang: "fr")
#set heading(numbering: "1.")

// ----- Cover ---------------------------------------------------------------

#align(center)[
  #text(size: 22pt, weight: "bold")[
    #data.metadata.at("name", default: "Rapport Analytics FASO")
  ]
]

#v(0.5cm)

#grid(
  columns: (auto, 1fr),
  column-gutter: 12pt,
  row-gutter: 6pt,
  [*Sous-projet :*], [#data.metadata.at("subProject", default: "—")],
  [*Version :*],     [#data.metadata.at("semver", default: "—")],
  [*Période :*],     [#data.at("period", default: "—")],
  [*Date :*],        [#datetime.today().display()],
)

#line(length: 100%)

// ----- KPIs ----------------------------------------------------------------

= Indicateurs clés

#let kpis = data.at("kpis", default: ())
#if kpis.len() == 0 [
  _Aucun KPI défini._
] else [
  #table(
    columns: (1fr, auto, auto),
    align: (left, right, left),
    table.header[*Indicateur*][*Valeur*][*Unité*],
    ..kpis.map(k => (
      k.at("label", default: k.at("id", default: "—")),
      str(k.at("value", default: "")),
      k.at("unit", default: ""),
    )).flatten()
  )
]

// ----- Visualizations ------------------------------------------------------

= Visualisations

#let vizes = data.at("visualizations", default: ())
#if vizes.len() == 0 [
  _Aucune visualisation définie._
] else [
  #for viz in vizes [
    == #viz.at("title", default: viz.at("id", default: "Visualisation"))

    #if viz.at("subtitle", default: none) != none [
      _#viz.subtitle_
    ]

    #let encoding = viz.at("encoding", default: (:))
    #let headers = encoding.at("headers", default: ())
    #let rows = encoding.at("rows", default: ())

    #if headers.len() > 0 and rows.len() > 0 [
      #table(
        columns: headers.len(),
        table.header(..headers.map(h => [*#h*])),
        ..rows.flatten().map(c => str(c))
      )
    ] else [
      _Type #viz.at("type", default: "chart") — données graphiques rendues côté frontend._
    ]

    #v(0.4cm)
  ]
]

#line(length: 100%)
#align(right)[
  #text(size: 9pt, style: "italic")[
    Généré par FASO-ANALYTICS-WORKFLOW v1.0 — #datetime.today().display()
  ]
]
