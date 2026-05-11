# Workflows d'exemple — couverture des 19 types de visualisation

Chaque fichier JSON ci-dessous est un **workflow valide** conformément au JSON Schema `schemas/workflow-v1.json` (cf. ADR-002). Ils servent de :

- **L2-livrable Phase 1** : « jeux d'exemples valides » mentionnés au §10 du plan ultraplan.
- **Spécification vivante** pour les démos Phase 2.1 / 2.2 / 2.3 (§9.3 du plan).
- **Documentation par l'exemple** pour les analystes métier qui découvriront le format YAML/JSON.

## Index

| # | Fichier | Type viz | Cas métier | Sub-projet | Phase démo |
|---|---|---|---|---|---|
| 01 | `01-bar-vertical-pagsi-region.json` | `BAR_VERTICAL` (barres debout) | Superficie aménagée par région | VOUCHERS | — |
| 02 | `02-bar-horizontal-top10-communes.json` | `BAR_HORIZONTAL` (barres couchées) | Top 10 communes | VOUCHERS | — |
| 03 | `03-bar-grouped-region-x-cereale.json` | `BAR_GROUPED` (barres groupées) | PAGSI Région × Céréale | VOUCHERS | **Phase 2.1** |
| 04 | `04-donut-distribution-statuts.json` | `DONUT` (camembert/anneau) | Statuts des actes d'état civil | ETAT_CIVIL | — |
| 05 | `05-half-donut-resurep-especes.json` | `HALF_DONUT` (demi-cercle) | RESUREP — cas par espèce animale | HOSPITAL | **Phase 2.3** |
| 06 | `06-combo-dual-axis-sonagess.json` | **`COMBO_DUAL_AXIS`** (double échelle BAR + LINE) | SONAGESS — stock physique (t) + prix moyen (FCFA/kg) | VOUCHERS | **Phase 2.2** |
| 07 | `07-line-multi-evolution.json` | `LINE_MULTI` (courbes multiples) | Admissions hospitalières par région | HOSPITAL | — |
| 08 | `08-choropleth-bf-coverage.json` | `CHOROPLETH_BF` (carte choroplèthe) | Couverture vaccinale par région | HOSPITAL | — |

## Validation locale

```bash
# Avec ajv-cli (Node.js)
npx ajv-cli validate -s schemas/workflow-v1.json -d "schemas/examples/*.json"

# Avec jsonschema (Python)
python -c "
import json, jsonschema
from pathlib import Path
schema = json.loads(Path('schemas/workflow-v1.json').read_text())
for f in Path('schemas/examples').glob('*.json'):
    jsonschema.validate(json.loads(f.read_text()), schema)
    print(f'✓ {f.name}')
"

# Avec jsonschema-rs (Rust)
cargo run --bin validate-examples
```

## Types de visualisation explicitement couverts par le plan

Le plan §0 mentionne ces exigences métier non négociables :

- ✅ KPI (compteurs, %) → tous les exemples avec `kpis: [...]`
- ✅ **Barres debout** (BAR_VERTICAL) → exemple 01
- ✅ **Barres couchées** (BAR_HORIZONTAL) → exemple 02
- ✅ Barres groupées (BAR_GROUPED) → exemple 03 (démo Phase 2.1 PAGSI)
- ✅ **Camemberts** (DONUT) → exemple 04
- ✅ **Demi-cercle** (HALF_DONUT) → exemple 05 (démo Phase 2.3 RESUREP)
- ✅ Courbes en **double entrée** avec **échelle gauche et droite** (COMBO_DUAL_AXIS) → exemple 06 (démo Phase 2.2 SONAGESS — `leftAxis` BAR en tonnes, `rightAxis` LINE en FCFA/kg)
- ✅ Courbes multiples (LINE_MULTI) → exemple 07
- ✅ Carte choroplèthe BF (CHOROPLETH_BF) → exemple 08

Types complémentaires disponibles dans le schéma mais sans exemple dédié (à compléter Phase 3) :
- BAR_STACKED, BAR_100PCT, PIE, LINE, AREA, AREA_STACKED, SCATTER, BUBBLE, HEATMAP, GAUGE_SEMI, KPI_TILE, SPARKLINE, PIVOT_TABLE

## Particularité COMBO_DUAL_AXIS (exemple 06)

L'`encoding` impose **deux** `AxisSpec` distincts (`leftAxis` + `rightAxis`), chacun avec :
- `seriesType` propre (BAR | LINE | AREA) — indépendant entre les deux axes
- `aggregation` propre (SUM, AVG, COUNT, MIN, MAX, MEDIAN)
- `scale` propre (LINEAR | LOG)
- `format` propre (pattern, unit, decimals) — ex. `t` à gauche, `FCFA/kg` à droite
- `min`/`max` propres

Ceci est validé au niveau JSON Schema via une clause `if/then/allOf` (cf. `schemas/workflow-v1.json` ligne ~250). Toute définition `COMBO_DUAL_AXIS` sans `leftAxis` OU sans `rightAxis` est **refusée** par le validateur — exigence métier non-négociable.

## Démos visées par le plan (§9.3)

1. **Démo Phase 2.1** — Barres groupées : exemple 03 (PAGSI Région × Céréale)
2. **Démo Phase 2.2** — Combo double échelle : exemple 06 (SONAGESS stock + prix)
3. **Démo Phase 2.3** — Demi-cercle : exemple 05 (RESUREP cas par espèce animale)
