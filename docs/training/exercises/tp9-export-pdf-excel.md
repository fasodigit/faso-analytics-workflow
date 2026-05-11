# TP9 — Exports PDF / Excel / PPTX

> **Durée** : 45 minutes.
> **Niveau** : intermédiaire.
> **Module rattaché** : Jour 2 (complément M12, à la demande).

---

## Contexte métier

Le Cabinet du Ministre de l'Économie reçoit chaque vendredi un PDF reprenant le tableau de bord PAGSI hebdomadaire. Le directeur de cabinet utilise Excel pour ses calculs personnels — il demande aussi un export `.xlsx`. Et pour les conseils des ministres, une mise en forme PowerPoint `.pptx` est requise.

## Données fournies

- **Workflow source** : `migrations/production/pagsi-volumes-region-cereale-v1.0.0.json` *(nom validé avec l'agent M ; à re-vérifier en cas de renommage)*.
- 3 templates :
  - `templates/export-pdf/cabinet-ministre.tex` (Typst/LaTeX).
  - `templates/export-excel/cabinet-ministre.xlsx` (squelette avec styles).
  - `templates/export-pptx/conseils-ministres.pptx` (1 slide cible).

## Objectif

Ajouter 3 outputs au workflow existant :
- 1 export PDF (cron VEN 18:00).
- 1 export Excel (cron VEN 18:00, en parallèle).
- 1 export PPTX (cron LUN 06:00, pour le conseil hebdo).

## Pas-à-pas suggéré

1. Ouvrir le workflow PAGSI existant.
2. Cloner en `1.2.0-draft.1`.
3. Ajouter 3 outputs supplémentaires :
   - `export_pdf` : `kind = export`, `format = PDF`, `templateRef = templates/export-pdf/cabinet-ministre.tex`, `to = ["cabinet-ministre@gov.bf"]`, `schedule.cron = "0 0 18 ? * FRI"`.
   - `export_excel` : `kind = export`, `format = EXCEL`, `templateRef = templates/export-excel/cabinet-ministre.xlsx`, `to = ["dircab@gov.bf"]`, `schedule.cron = "0 0 18 ? * FRI"`.
   - `export_pptx` : `kind = export`, `format = PPTX`, `templateRef = templates/export-pptx/conseils-ministres.pptx`, `to = ["sgg@gov.bf"]`, `schedule.cron = "0 0 6 ? * MON"`.
4. Simuler en sandbox : vérifier que le PDF généré contient bien le titre + la table régions + 1 graphique vectoriel.
5. Vérifier que l'Excel contient 1 onglet `Data` et 1 onglet `Resume`.
6. Vérifier que le PPTX contient 1 slide avec graphique + 1 KPI tile.

## Critères de validation

- [ ] Les 3 outputs sont déclarés avec `kind = export`.
- [ ] Chaque output référence un template existant.
- [ ] Chaque output a un `cron` valide en timezone Africa/Ouagadougou.
- [ ] Le PDF généré est compilable sans erreur Typst.
- [ ] L'Excel contient 2 onglets minimum.
- [ ] Le PPTX contient ≥ 1 slide avec ≥ 1 graphique.

## Solution attendue

<details>
<summary>Cliquer pour voir la solution complète</summary>

Outputs ajoutés au workflow existant :

```json
"outputs": [
  { "kind": "dashboard", "dashboardCode": "pagsi_region", "refreshSec": 1800 },
  {
    "kind": "export",
    "format": "PDF",
    "templateRef": "templates/export-pdf/cabinet-ministre.tex",
    "to": ["cabinet-ministre@gov.bf"],
    "schedule": { "cron": "0 0 18 ? * FRI", "timezone": "Africa/Ouagadougou", "trigger": "cron" }
  },
  {
    "kind": "export",
    "format": "EXCEL",
    "templateRef": "templates/export-excel/cabinet-ministre.xlsx",
    "to": ["dircab@gov.bf"],
    "schedule": { "cron": "0 0 18 ? * FRI", "timezone": "Africa/Ouagadougou", "trigger": "cron" }
  },
  {
    "kind": "export",
    "format": "PPTX",
    "templateRef": "templates/export-pptx/conseils-ministres.pptx",
    "to": ["sgg@gov.bf"],
    "schedule": { "cron": "0 0 6 ? * MON", "timezone": "Africa/Ouagadougou", "trigger": "cron" }
  }
]
```

**Validation sandbox** :

```
✓ PDF généré : 1 page, 3 sections, taille 245 KB.
✓ Excel généré : 2 onglets (Data, Resume), 13 lignes, 5 colonnes.
✓ PPTX généré : 1 slide, 1 KPI_TILE, 1 BAR_VERTICAL.
✓ Export Metabase métadonnées (optionnel) : disponible mais non activé pour ce TP.
```

</details>
