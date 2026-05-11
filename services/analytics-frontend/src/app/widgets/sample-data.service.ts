import { Injectable } from '@angular/core';
import { DemoDataset } from './echarts-renderer.component';
import { WorkflowDefinition } from './workflow-definition.model';

import barGroupedJson from '../../../../../schemas/examples/03-bar-grouped-region-x-cereale.json';
import halfDonutJson from '../../../../../schemas/examples/05-half-donut-resurep-especes.json';
import comboDualAxisJson from '../../../../../schemas/examples/06-combo-dual-axis-sonagess.json';
import barVerticalJson from '../../../../../schemas/examples/01-bar-vertical-pagsi-region.json';
import barHorizontalJson from '../../../../../schemas/examples/02-bar-horizontal-top10-communes.json';
import donutJson from '../../../../../schemas/examples/04-donut-distribution-statuts.json';
import lineMultiJson from '../../../../../schemas/examples/07-line-multi-evolution.json';
import choroplethJson from '../../../../../schemas/examples/08-choropleth-bf-coverage.json';

const BF_REGIONS = [
  'Boucle du Mouhoun',
  'Cascades',
  'Centre',
  'Centre-Est',
  'Centre-Nord',
  'Centre-Ouest',
  'Centre-Sud',
  'Est',
  'Hauts-Bassins',
  'Nord',
  'Plateau-Central',
  'Sahel',
  'Sud-Ouest',
];

const CEREALES: Array<{ kind: string; base: number }> = [
  { kind: 'mil', base: 1100 },
  { kind: 'sorgho', base: 1400 },
  { kind: 'mais', base: 900 },
];

const ESPECES: Array<{ name: string; cas: number }> = [
  { name: 'bovins', cas: 42 },
  { name: 'caprins', cas: 18 },
  { name: 'ovins', cas: 11 },
  { name: 'volaille', cas: 7 },
  { name: 'equides', cas: 2 },
  { name: 'autres', cas: 1 },
];

const ESPECES_STACK = ['bovins', 'caprins', 'ovins', 'volaille'];
const MALADIES_RES = ['PPCB', 'Fièvre aphteuse', 'Newcastle', 'Rage'];
const STATUTS_ACTE = ['délivré', 'en_cours', 'rejeté', 'archivé'];

const STUB_REGIONS_FOR_MAP = ['Centre', 'Hauts-Bassins', 'Sahel', 'Nord', 'Est', 'Boucle du Mouhoun'];

function pseudoRandom(seed: number): number {
  const x = Math.sin(seed) * 10000;
  return x - Math.floor(x);
}

function makeWorkflow(
  name: string,
  type: string,
  encoding: Record<string, unknown>,
  title: string,
  subtitle: string,
  extra: Record<string, unknown> = {},
): WorkflowDefinition {
  return {
    apiVersion: 'analytics.faso/v1',
    kind: 'AnalyticsWorkflow',
    metadata: {
      name,
      subProject: 'HOSPITAL',
      semver: '1.0.0-draft.1',
    },
    spec: {
      source: { type: 'yugabyte', schema: 'demo', table: 'demo' },
      pipeline: [],
      kpis: [],
      visualizations: [
        {
          id: `viz_${name}`,
          type: type as never,
          title,
          subtitle,
          encoding,
          ...extra,
        },
      ],
      outputs: [],
    },
  };
}

@Injectable({ providedIn: 'root' })
export class SampleDataService {
  // Phase 1 fixtures

  getBarGroupedWorkflow(): WorkflowDefinition {
    return barGroupedJson as unknown as WorkflowDefinition;
  }

  getHalfDonutWorkflow(): WorkflowDefinition {
    return halfDonutJson as unknown as WorkflowDefinition;
  }

  getComboDualAxisWorkflow(): WorkflowDefinition {
    return comboDualAxisJson as unknown as WorkflowDefinition;
  }

  getBarGroupedDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    BF_REGIONS.forEach((region, idxRegion) => {
      CEREALES.forEach((cereale) => {
        const variation = ((idxRegion * 137 + cereale.base) % 600) - 200;
        const volume = Math.max(150, cereale.base + variation);
        rows.push({
          region,
          type_cereale: cereale.kind,
          volume_tonnes: volume,
        });
      });
    });
    return { rows };
  }

  getHalfDonutDataset(): DemoDataset {
    return {
      rows: ESPECES.map((e) => ({
        espece_animale: e.name,
        cas_confirmes: e.cas,
      })),
    };
  }

  getComboDualAxisDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    for (let i = 0; i < 12; i++) {
      const t = i / 11;
      const stock = Math.round(18000 + t * 8000 + ((i * 7) % 1200));
      const prix = Math.round(320 + t * 220 - ((i * 11) % 30));
      rows.push({
        mois: `M${i + 1}`,
        stock_tonnes: stock,
        prix_moyen_fcfa: prix,
      });
    }
    return { rows };
  }

  // Phase 3 — 14 new fixtures

  getBarVerticalWorkflow(): WorkflowDefinition {
    return barVerticalJson as unknown as WorkflowDefinition;
  }

  getBarVerticalDataset(): DemoDataset {
    return {
      rows: BF_REGIONS.map((region, i) => ({
        region,
        superficie_totale_ha: Math.round(2500 + pseudoRandom(i + 1) * 6500),
      })),
    };
  }

  getBarHorizontalWorkflow(): WorkflowDefinition {
    return barHorizontalJson as unknown as WorkflowDefinition;
  }

  getBarHorizontalDataset(): DemoDataset {
    const communes = [
      'Ouagadougou', 'Bobo-Dioulasso', 'Koudougou', 'Banfora', 'Ouahigouya',
      'Dori', "Fada N'Gourma", 'Kaya', 'Tenkodogo', 'Dédougou',
    ];
    return {
      rows: communes.map((commune, i) => ({
        commune,
        superficie_ha: Math.round(950 - i * 60 - pseudoRandom(i) * 40),
      })),
    };
  }

  getBarStackedWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'effectifs-stack-region-espece',
      'BAR_STACKED',
      { xField: 'region', yField: 'effectif', series: 'espece' },
      'Effectifs animaux par région × espèce',
      'Empilé : bovins + caprins + ovins + volaille',
    );
  }

  getBarStackedDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    BF_REGIONS.forEach((region, ri) => {
      ESPECES_STACK.forEach((espece, ei) => {
        const base = [22000, 9000, 6500, 18000][ei] ?? 5000;
        rows.push({
          region,
          espece,
          effectif: Math.round(base + pseudoRandom(ri * 7 + ei) * base * 0.6),
        });
      });
    });
    return { rows };
  }

  getBar100PctWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'maladies-pct-par-region',
      'BAR_100PCT',
      { xField: 'region', yField: 'cas', series: 'maladie' },
      'Répartition % des maladies par région',
      'Cas confirmés normalisés à 100 % par région',
    );
  }

  getBar100PctDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    BF_REGIONS.forEach((region, ri) => {
      MALADIES_RES.forEach((maladie, mi) => {
        rows.push({
          region,
          maladie,
          cas: Math.round(10 + pseudoRandom(ri * 11 + mi * 3) * 40),
        });
      });
    });
    return { rows };
  }

  getPieWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'actes-pie',
      'PIE',
      { category: 'statut', value: 'nombre_actes' },
      "Actes d'état civil par statut (camembert)",
      'Répartition globale toutes communes',
    );
  }

  getPieDataset(): DemoDataset {
    return {
      rows: STATUTS_ACTE.map((statut, i) => ({
        statut,
        nombre_actes: Math.round(5000 + pseudoRandom(i + 1) * 35000),
      })),
    };
  }

  getDonutWorkflow(): WorkflowDefinition {
    return donutJson as unknown as WorkflowDefinition;
  }

  getDonutDataset(): DemoDataset {
    return {
      rows: STATUTS_ACTE.map((statut, i) => ({
        statut,
        nombre_actes: Math.round(5000 + pseudoRandom(i + 17) * 35000),
      })),
    };
  }

  getLineWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'admissions-hospital-line',
      'LINE',
      { xField: 'mois', yField: 'nb_admissions' },
      'Admissions hospitalières mensuelles',
      'Évolution sur 12 mois — toutes régions confondues',
    );
  }

  getLineDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    for (let i = 0; i < 12; i++) {
      rows.push({
        mois: `M${i + 1}`,
        nb_admissions: Math.round(8200 + Math.sin(i / 2) * 1500 + pseudoRandom(i) * 800),
      });
    }
    return { rows };
  }

  getLineMultiWorkflow(): WorkflowDefinition {
    return lineMultiJson as unknown as WorkflowDefinition;
  }

  getLineMultiDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    BF_REGIONS.forEach((region, ri) => {
      for (let i = 0; i < 12; i++) {
        rows.push({
          date_admission: `M${i + 1}`,
          region,
          nb_admissions: Math.round(80 + pseudoRandom(ri * 13 + i) * 220 + Math.cos(i / 2 + ri) * 50),
        });
      }
    });
    return { rows };
  }

  getAreaWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'vaccinations-cumul-area',
      'AREA',
      { xField: 'mois', yField: 'cumul' },
      'Cumul des vaccinations (toutes maladies)',
      'Aire pleine — somme glissante depuis le début de la campagne',
    );
  }

  getAreaDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    let cumul = 0;
    for (let i = 0; i < 12; i++) {
      cumul += Math.round(3500 + pseudoRandom(i) * 4500);
      rows.push({ mois: `M${i + 1}`, cumul });
    }
    return { rows };
  }

  getAreaStackedWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'vaccinations-cumul-maladie-area-stack',
      'AREA_STACKED',
      { xField: 'mois', yField: 'cumul', series: 'maladie' },
      'Cumul vaccinations par maladie',
      'Aires empilées — 4 maladies, 12 mois',
    );
  }

  getAreaStackedDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    const cumuls: Record<string, number> = {};
    MALADIES_RES.forEach((m) => (cumuls[m] = 0));
    for (let i = 0; i < 12; i++) {
      MALADIES_RES.forEach((maladie, mi) => {
        cumuls[maladie] = (cumuls[maladie] ?? 0) + Math.round(800 + pseudoRandom(i * 5 + mi) * 1200);
        rows.push({ mois: `M${i + 1}`, maladie, cumul: cumuls[maladie] ?? 0 });
      });
    }
    return { rows };
  }

  getScatterWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'scatter-age-couverture',
      'SCATTER',
      { xField: 'age', yField: 'taux_couverture' },
      'Âge × Taux de couverture vaccinale',
      'Dispersion ponctuelle (50 individus échantillonnés)',
    );
  }

  getScatterDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    for (let i = 0; i < 50; i++) {
      const age = Math.round(1 + pseudoRandom(i) * 70);
      const taux = Math.round(40 + pseudoRandom(i * 3 + 1) * 55);
      rows.push({ age, taux_couverture: taux });
    }
    return { rows };
  }

  getBubbleWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'bubble-region-pop-couverture',
      'BUBBLE',
      { xField: 'region_index', yField: 'population', size: 'taux_couverture' },
      'Population × Taux couverture par région',
      'Bulles : taille = taux couverture (%)',
    );
  }

  getBubbleDataset(): DemoDataset {
    return {
      rows: BF_REGIONS.map((region, i) => ({
        region,
        region_index: i + 1,
        population: Math.round(400000 + pseudoRandom(i + 5) * 2800000),
        taux_couverture: Math.round(55 + pseudoRandom(i * 4 + 9) * 35),
      })),
    };
  }

  getHeatmapWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'heatmap-jour-heure-hospitalier',
      'HEATMAP',
      { xField: 'heure', yField: 'jour', value: 'cas' },
      'Cas hospitaliers — jour × heure',
      'Carte de chaleur des admissions hebdomadaires',
    );
  }

  getHeatmapDataset(): DemoDataset {
    const jours = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
    const rows: Array<Record<string, string | number>> = [];
    jours.forEach((jour, ji) => {
      for (let h = 0; h < 24; h++) {
        const peak = h >= 7 && h <= 19 ? 1 : 0.3;
        const cas = Math.round(peak * (10 + pseudoRandom(ji * 31 + h) * 55));
        rows.push({ jour, heure: `${h}h`, cas });
      }
    });
    return { rows };
  }

  getChoroplethBfWorkflow(): WorkflowDefinition {
    return choroplethJson as unknown as WorkflowDefinition;
  }

  getChoroplethBfDataset(): DemoDataset {
    // Match the 6 region names declared in bf-geo.json (stub).
    return {
      rows: STUB_REGIONS_FOR_MAP.map((name, i) => ({
        region_code: name,
        taux_couverture: Math.round(58 + pseudoRandom(i + 31) * 35),
      })),
    };
  }

  getGaugeSemiWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'gauge-couverture-nationale',
      'GAUGE_SEMI',
      { value: 'taux', target: 'cible' },
      'Couverture vaccinale nationale',
      'Cible 90 %',
      {
        polarity: 'more_better',
        thresholds: { warningPct: 75, criticalPct: 60 },
        target: { source: 'literal', value: 90 },
      },
    );
  }

  getGaugeSemiDataset(): DemoDataset {
    return { rows: [{ taux: 84.2, cible: 90 }] };
  }

  getKpiTileWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'kpi-total-actes',
      'KPI_TILE',
      { value: 'total', delta: 'delta_pct', trend: 'last_30d' },
      'Total actes enregistrés',
      'Sur les 30 derniers jours',
    );
  }

  getKpiTileDataset(): DemoDataset {
    const trend: number[] = [];
    let val = 380;
    for (let i = 0; i < 30; i++) {
      val += Math.round((pseudoRandom(i + 100) - 0.4) * 60);
      trend.push(val);
    }
    const rows: Array<Record<string, string | number>> = [];
    trend.forEach((v, i) => {
      rows.push({
        total: i === 0 ? '12 847' : '',
        delta_pct: i === 0 ? 5.2 : 0,
        last_30d: v,
      });
    });
    return { rows };
  }

  getSparklineWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'sparkline-kpi-principal',
      'SPARKLINE',
      { xField: 'jour', yField: 'valeur' },
      '',
      '',
    );
  }

  getSparklineDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    let v = 9800;
    for (let i = 0; i < 30; i++) {
      v += Math.round((pseudoRandom(i + 7) - 0.45) * 350);
      rows.push({ jour: i + 1, valeur: v });
    }
    return { rows };
  }

  getPivotTableWorkflow(): WorkflowDefinition {
    return makeWorkflow(
      'pivot-region-maladie-espece',
      'PIVOT_TABLE',
      {
        rowFields: ['region', 'maladie'],
        columnFields: ['espece'],
        valueField: 'cas',
        aggregation: 'SUM',
      },
      'Pivot Région × Maladie × Espèce',
      'Cas cumulés (somme), avec sous-totaux',
    );
  }

  getPivotTableDataset(): DemoDataset {
    const rows: Array<Record<string, string | number>> = [];
    BF_REGIONS.forEach((region, ri) => {
      MALADIES_RES.forEach((maladie, mi) => {
        ESPECES_STACK.forEach((espece, ei) => {
          rows.push({
            region,
            maladie,
            espece,
            cas: Math.round(2 + pseudoRandom(ri * 37 + mi * 7 + ei) * 50),
          });
        });
      });
    });
    return { rows };
  }
}
