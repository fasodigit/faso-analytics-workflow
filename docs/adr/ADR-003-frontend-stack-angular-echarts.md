# ADR-003 — Frontend stack : Angular + ngx-graph + Apache ECharts

| Field | Value |
|---|---|
| **Status** | Proposed — default per ultraplan v0.1-DRAFT |
| **Decision date** | 2026-05-11 |

## Contexte

Le constructeur visuel de workflows doit fournir :

1. Un **canvas DAG** drag-and-drop pour relier sources, transformations, KPI, visualisations, outputs.
2. Un **éditeur de paramètres** par nœud, piloté par le JSON Schema (cohérent ADR-002).
3. Une **bibliothèque de visualisations** couvrant les 18 types du schéma v1.0, y compris : COMBO_DUAL_AXIS (deux axes verticaux distincts, l'un BAR, l'autre LINE), HALF_DONUT (demi-cercle), CHOROPLETH_BF (carte choroplèthe Burkina Faso), HEATMAP, GAUGE_SEMI, PIVOT_TABLE.
4. Une **expérience cohérente** avec les autres consoles FASO DIGITALISATION (Faso-Conductor, ARMAGEDDON gateway-ui, kobo-EAU admin-ui).

## Options évaluées

### Canvas DAG

| Option | Pour | Contre |
|---|---|---|
| **A. @angular/cdk/drag-drop + @swimlane/ngx-graph** | Natif Angular, pas de wrapper React, perf acceptable jusqu'à ~100 nœuds, ngx-graph activement maintenu sous SwimLane | Limite ~100 nœuds (acceptable pour v1) |
| **B. rete.js** | Très complet, écosystème node-based | Web component dans Angular = wrapper additionnel, doc Angular limitée |
| **C. drawflow** | Léger | Pas d'Angular natif, abandon partiel |
| **D. JointJS** | Mature, commercial | License commerciale pour les features étendues |

### Édition de paramètres

| Option | Pour | Contre |
|---|---|---|
| **A. @ngx-formly/core** | Pilotage direct par JSON Schema (cohérent ADR-002), zéro duplication, large communauté Angular | Courbe d'apprentissage si custom field type complexe |
| **B. Formulaires Angular manuels** | Contrôle total | Duplication entre schéma et UI, refactor coûteux à chaque évolution du schéma |
| **C. JSONForms** | Standard JSON Forms | Plus orienté React, support Angular moins riche |

### Visualisations

| Option | Pour | Contre |
|---|---|---|
| **A. Apache ECharts (ngx-echarts)** | Couvre **les 18 types nativement** : `yAxis: [{}, {}]` pour dual axis, `startAngle/endAngle` pour half-donut, choroplèthe via geoJSON custom, heatmap, jauges, sparklines, pivot externe via ag-grid | Bundle ~900 kB (acceptable, lazy-loaded par route) |
| **B. D3.js** | Contrôle absolu | Faible niveau, ré-écrire chaque chart from scratch (10x effort) |
| **C. Chart.js** | Léger | Pas de dual-axis natif, pas de choroplèthe, pas de heatmap |
| **D. AG-Charts** | Pro, dual-axis natif | Bundle plus lourd, mix ag-grid + ag-charts complexifie |
| **E. ApexCharts** | Bon dual-axis, Angular wrapper | Couverture pivot/choroplèthe limitée |

### Pivot table

| Option | Pour | Contre |
|---|---|---|
| **A. ag-grid-community** | Pivot natif, sous-totaux, export Excel/CSV, virtualisation perf | Bundle ~700 kB |
| **B. AG Grid Enterprise** | Idem + features avancées | Payant |
| **C. Angular CDK Table** | Léger | Pas de pivot natif, à coder |

## Décision

**Stack frontend retenue :**

1. **@angular/cdk/drag-drop + @swimlane/ngx-graph** pour le canvas DAG.
2. **@ngx-formly/core** pour les formulaires de paramètres pilotés par JSON Schema.
3. **Apache ECharts via ngx-echarts** pour les 18 types de visualisation.
4. **ag-grid-community** (open source) pour la pivot table.
5. **Tokens design partagés** : import du theme FASO DIGITALISATION (cohérent skill `frontend-design`).

## Justification

### Couverture native ECharts des exigences

| Type schéma v1.0 | Implémentation ECharts |
|---|---|
| `BAR_VERTICAL`, `BAR_HORIZONTAL` | `series.type: 'bar'`, `xAxis/yAxis.type: 'category'/'value'` |
| `BAR_STACKED`, `BAR_GROUPED`, `BAR_100PCT` | `series.stack: 'a'`, `series.barGap: '0%'`, `stack: 'total'` |
| `PIE`, `DONUT`, `HALF_DONUT` | `series.type: 'pie'`, `radius: ['40%','70%']`, **`startAngle: 180, endAngle: 360`** |
| `LINE`, `LINE_MULTI`, `AREA`, `AREA_STACKED` | `series.type: 'line'`, `areaStyle`, `stack: 'a'` |
| **`COMBO_DUAL_AXIS`** | **`yAxis: [{position: 'left', ...}, {position: 'right', ...}]` + `series[i].yAxisIndex: 0 or 1`** |
| `SCATTER`, `BUBBLE` | `series.type: 'scatter'`, `symbolSize: f(data)` |
| `HEATMAP` | `series.type: 'heatmap'` + `visualMap` |
| `CHOROPLETH_BF` | `echarts.registerMap('BF', geoJSON_BF)` + `series.type: 'map'` (geoJSON découpé en 13 régions ou 351 communes au choix) |
| `GAUGE_SEMI` | `series.type: 'gauge'`, `startAngle: 180, endAngle: 0` |
| `KPI_TILE`, `SPARKLINE` | composant Angular custom + mini-line ECharts |
| `PIVOT_TABLE` | délégué à ag-grid avec colonnes pivot dynamiques |

### Cohérence ADR-002

Formly consomme le JSON Schema produit en ADR-002. Si on ajoute un champ optionnel `metadata.cost_center` au schéma, Formly détecte le nouvel attribut et affiche le champ correspondant **sans modification de code Angular**.

### Performance v1 cible

- Canvas DAG ≤ 100 nœuds : ngx-graph est testé public à ~200 nœuds. Marge confortable.
- Bundle initial Angular + ECharts lazy-loaded + ngx-graph + Formly : ~1.5 MB gzip (cible < 2 MB validée par Lighthouse Phase 1).
- Hydratation post-DOMContentLoaded p95 < 1.5 s sur connection 3G simulée (Burkina Faso).

## Conséquences

### Positives

- **Une seule lib de viz pour 18 types** = pas de patchwork de libs, pas d'incohérence visuelle.
- **Formly couplé au schéma** : itérations rapides sur la définition du workflow.
- **ngx-graph stable** : SwimLane backe la lib, releases régulières.
- **ag-grid-community** = pivot enterprise-grade gratuit.

### Négatives

- Bundle frontend ≥ 1.5 MB : à mitiger via route-level lazy loading et tree-shaking ECharts (`echarts/core` + imports sélectifs).
- ag-grid + ECharts = deux paradigmes de styling à harmoniser.
- ngx-graph plafonné ~100 nœuds : si un workflow devient géant, prévoir un mode "compressed" ou un fallback rete.js.

## Conditions de réexamen

- Si un cas exotique nécessite un type non supporté par ECharts (ex. Sankey 3D, network graph interactif éditable), évaluer un plugin custom SVG/D3 enregistrable via le mécanisme d'extension.
- Si la performance ngx-graph dégrade au-delà de 80 nœuds (mesure FPS Chrome DevTools), passer à rete.js wrapper.

## Référence

- ngx-graph : <https://github.com/swimlane/ngx-graph>
- @ngx-formly/core : <https://formly.dev>
- Apache ECharts : <https://echarts.apache.org>
- ngx-echarts : <https://github.com/xieziyu/ngx-echarts>
- ag-grid-community : <https://github.com/ag-grid/ag-grid>
- geoJSON Burkina Faso (13 régions, 351 communes) : à générer depuis OpenStreetMap (Overpass) ou consommer le référentiel MDM commun à la plateforme.
