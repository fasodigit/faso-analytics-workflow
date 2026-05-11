import { Injectable } from '@angular/core';
import { DemoDataset } from './echarts-renderer.component';
import { WorkflowDefinition } from './workflow-definition.model';

import barGroupedJson from '../../../../../schemas/examples/03-bar-grouped-region-x-cereale.json';
import halfDonutJson from '../../../../../schemas/examples/05-half-donut-resurep-especes.json';
import comboDualAxisJson from '../../../../../schemas/examples/06-combo-dual-axis-sonagess.json';

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

@Injectable({ providedIn: 'root' })
export class SampleDataService {
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
}
