import { FormlyFieldConfig } from '@ngx-formly/core';
import { NodeKind } from './dag-node.model';

/**
 * Static mapping `kind -> formly fields`. Phase 2 simplification per spec.
 * Kinds not in this map fall back to a single JSON textarea editor.
 */
export function getFormlySchemaForKind(kind: NodeKind): FormlyFieldConfig[] {
  switch (kind) {
    case 'source':
      return [
        {
          key: 'type',
          type: 'select',
          defaultValue: 'yugabyte',
          props: {
            label: 'Type',
            required: true,
            options: [
              { value: 'yugabyte', label: 'Yugabyte' },
              { value: 'kobo', label: 'Kobo' },
              { value: 'surveymonkey', label: 'SurveyMonkey' },
              { value: 'dragonfly', label: 'Dragonfly' },
              { value: 'redpanda', label: 'Redpanda' },
              { value: 'metabase', label: 'Metabase' },
              { value: 'upload', label: 'Upload (CSV/XLSX/…)' },
            ],
          },
        },
        {
          key: 'connectionRef',
          type: 'input',
          props: {
            label: 'connectionRef (Vault path)',
            placeholder: 'secret/data/analytics/connectors/VOUCHERS/pagsi-prod',
          },
        },
        {
          key: 'schema',
          type: 'input',
          props: { label: 'Schema (yugabyte)' },
          expressions: { hide: 'model.type !== "yugabyte"' },
        },
        {
          key: 'table',
          type: 'input',
          props: { label: 'Table (yugabyte)' },
          expressions: { hide: 'model.type !== "yugabyte"' },
        },
        {
          key: 'sqlPredicate',
          type: 'textarea',
          props: { label: 'sqlPredicate (optional)', rows: 2 },
          expressions: { hide: 'model.type !== "yugabyte"' },
        },
      ];

    case 'filter':
      return [
        {
          key: 'expression',
          type: 'textarea',
          props: {
            label: 'Expression SQL WHERE (DataFusion)',
            placeholder: "campagne = '2025-2026'",
            required: true,
            rows: 3,
          },
        },
      ];

    case 'aggregate':
      return [
        {
          key: 'groupBy',
          type: 'input',
          props: {
            label: 'groupBy (comma-separated)',
            placeholder: 'region, type_cereale',
          },
        },
        {
          key: 'aggregations',
          type: 'repeat',
          props: { label: 'Aggregations' },
          fieldArray: {
            fieldGroup: [
              {
                key: 'alias',
                type: 'input',
                props: { label: 'alias', required: true },
              },
              {
                key: 'function',
                type: 'select',
                props: {
                  label: 'function',
                  required: true,
                  options: [
                    'SUM',
                    'AVG',
                    'COUNT',
                    'COUNT_DISTINCT',
                    'MIN',
                    'MAX',
                    'MEDIAN',
                  ].map((v) => ({ value: v, label: v })),
                },
              },
              {
                key: 'field',
                type: 'input',
                props: { label: 'field', required: true },
              },
            ],
          },
        },
      ];

    case 'computed':
      return [
        {
          key: 'alias',
          type: 'input',
          props: { label: 'alias', required: true },
        },
        {
          key: 'expression',
          type: 'textarea',
          props: {
            label: 'expression (DataFusion)',
            placeholder: 'volume_tonnes / 1000.0',
            required: true,
            rows: 2,
          },
        },
        {
          key: 'type',
          type: 'select',
          props: {
            label: 'type',
            options: [
              'INTEGER',
              'BIGINT',
              'DOUBLE',
              'DECIMAL',
              'STRING',
              'BOOL',
              'DATE',
              'TIMESTAMP',
            ].map((v) => ({ value: v, label: v })),
          },
        },
      ];

    case 'kpi':
      return [
        {
          key: 'label',
          type: 'input',
          props: { label: 'label', required: true },
        },
        {
          key: 'expression',
          type: 'textarea',
          props: { label: 'expression', required: true, rows: 2 },
        },
        {
          key: 'polarity',
          type: 'select',
          defaultValue: 'more_better',
          props: {
            label: 'polarity',
            options: [
              { value: 'more_better', label: 'more_better' },
              { value: 'less_better', label: 'less_better' },
              { value: 'neutral', label: 'neutral' },
            ],
          },
        },
        {
          key: 'format',
          fieldGroup: [
            {
              key: 'pattern',
              type: 'input',
              props: { label: 'format.pattern', placeholder: '#,##0.0' },
            },
            { key: 'unit', type: 'input', props: { label: 'format.unit' } },
            {
              key: 'decimals',
              type: 'input',
              props: { label: 'format.decimals', type: 'number' },
            },
          ],
        },
      ];

    case 'visualization':
      return [
        {
          key: 'type',
          type: 'select',
          defaultValue: 'BAR_GROUPED',
          props: {
            label: 'Type',
            required: true,
            options: [
              'BAR_VERTICAL',
              'BAR_HORIZONTAL',
              'BAR_STACKED',
              'BAR_GROUPED',
              'BAR_100PCT',
              'PIE',
              'DONUT',
              'HALF_DONUT',
              'LINE',
              'LINE_MULTI',
              'AREA',
              'AREA_STACKED',
              'COMBO_DUAL_AXIS',
              'SCATTER',
              'BUBBLE',
              'HEATMAP',
              'CHOROPLETH_BF',
              'GAUGE_SEMI',
              'KPI_TILE',
              'SPARKLINE',
              'PIVOT_TABLE',
            ].map((v) => ({ value: v, label: v })),
          },
        },
        { key: 'title', type: 'input', props: { label: 'title' } },
        { key: 'subtitle', type: 'input', props: { label: 'subtitle' } },
        {
          key: 'encoding',
          fieldGroup: [
            {
              key: 'xField',
              type: 'input',
              props: { label: 'encoding.xField' },
              expressions: {
                hide: '!["BAR_GROUPED","BAR_VERTICAL","BAR_HORIZONTAL","LINE_MULTI","COMBO_DUAL_AXIS"].includes(model.type)',
              },
            },
            {
              key: 'yField',
              type: 'input',
              props: { label: 'encoding.yField' },
              expressions: {
                hide: '!["BAR_GROUPED","BAR_VERTICAL","BAR_HORIZONTAL","LINE_MULTI"].includes(model.type)',
              },
            },
            {
              key: 'series',
              type: 'input',
              props: { label: 'encoding.series' },
              expressions: {
                hide: '!["BAR_GROUPED","LINE_MULTI"].includes(model.type)',
              },
            },
            {
              key: 'category',
              type: 'input',
              props: { label: 'encoding.category' },
              expressions: {
                hide: '!["HALF_DONUT","DONUT","PIE"].includes(model.type)',
              },
            },
            {
              key: 'value',
              type: 'input',
              props: { label: 'encoding.value' },
              expressions: {
                hide: '!["HALF_DONUT","DONUT","PIE"].includes(model.type)',
              },
            },
          ],
        },
      ];

    case 'output':
      return [
        {
          key: 'kind',
          type: 'select',
          defaultValue: 'dashboard',
          props: {
            label: 'Output kind',
            options: [
              { value: 'dashboard', label: 'dashboard' },
              { value: 'pdf', label: 'pdf' },
              { value: 'excel', label: 'excel' },
              { value: 'pptx', label: 'pptx' },
              { value: 'metabase', label: 'metabase' },
              { value: 'webhook', label: 'webhook' },
              { value: 'email', label: 'email' },
            ],
          },
        },
        {
          key: 'dashboardCode',
          type: 'input',
          props: { label: 'dashboardCode' },
          expressions: { hide: 'model.kind !== "dashboard"' },
        },
        {
          key: 'refreshSec',
          type: 'input',
          props: { label: 'refreshSec', type: 'number' },
          expressions: { hide: 'model.kind !== "dashboard"' },
        },
      ];

    case 'group_by':
      return [
        {
          key: 'fields',
          type: 'input',
          props: {
            label: 'fields (comma-separated)',
            required: true,
            placeholder: 'region, type_cereale',
          },
        },
      ];

    // Pivot/window/outlier/normalize/recode/join: Phase 2 stubs using a raw JSON editor
    case 'pivot':
    case 'window':
    case 'outlier':
    case 'normalize':
    case 'recode':
    case 'join':
      return [
        {
          key: '__raw',
          type: 'textarea',
          props: {
            label: 'Raw JSON params (Phase 2 stub)',
            placeholder: '{ "field": "...", "strategy": "IQR" }',
            rows: 8,
          },
        },
      ];
  }
}
