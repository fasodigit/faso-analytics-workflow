export type VisualizationType =
  | 'BAR_VERTICAL'
  | 'BAR_HORIZONTAL'
  | 'BAR_GROUPED'
  | 'BAR_STACKED'
  | 'BAR_100PCT'
  | 'PIE'
  | 'DONUT'
  | 'HALF_DONUT'
  | 'LINE'
  | 'LINE_MULTI'
  | 'AREA'
  | 'AREA_STACKED'
  | 'COMBO_DUAL_AXIS'
  | 'SCATTER'
  | 'BUBBLE'
  | 'HEATMAP'
  | 'CHOROPLETH_BF'
  | 'GAUGE_SEMI'
  | 'KPI_TILE'
  | 'SPARKLINE'
  | 'PIVOT_TABLE'
  | 'CHOROPLETH';

export type Polarity = 'more_better' | 'less_better' | 'neutral';

export interface BarVerticalEncoding {
  xField: string;
  yField: string;
}

export interface BarHorizontalEncoding {
  xField: string;
  yField: string;
}

export interface BarGroupedEncoding {
  xField: string;
  yField: string;
  series: string;
}

export interface BarStackedEncoding {
  xField: string;
  yField: string;
  series: string;
}

export interface Bar100PctEncoding {
  xField: string;
  yField: string;
  series: string;
}

export interface PieEncoding {
  category: string;
  value: string;
}

export interface DonutEncoding {
  category: string;
  value: string;
}

export interface HalfDonutEncoding {
  category: string;
  value: string;
}

export interface LineEncoding {
  xField: string;
  yField: string;
}

export interface LineMultiEncoding {
  xField: string;
  yField: string;
  series: string;
}

export interface AreaEncoding {
  xField: string;
  yField: string;
}

export interface AreaStackedEncoding {
  xField: string;
  yField: string;
  series: string;
}

export interface ScatterEncoding {
  xField: string;
  yField: string;
}

export interface BubbleEncoding {
  xField: string;
  yField: string;
  size: string;
}

export interface HeatmapEncoding {
  xField: string;
  yField: string;
  value: string;
}

export interface ChoroplethBfEncoding {
  regionField: string;
  valueField: string;
  level?: 'region' | 'province' | 'commune';
}

export interface GaugeSemiEncoding {
  value: string;
  target?: string;
}

export interface KpiTileEncoding {
  value: string;
  delta?: string;
  trend?: string;
}

export interface SparklineEncoding {
  xField: string;
  yField: string;
}

export interface PivotTableEncoding {
  rowFields: string[];
  columnFields: string[];
  valueField: string;
  aggregation?: 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX';
}

export interface DualAxisSide {
  seriesType: 'BAR' | 'LINE';
  field: string;
  aggregation?: string;
  label?: string;
  min?: number;
  scale?: 'LINEAR' | 'LOG';
  format?: { pattern?: string; unit?: string; decimals?: number };
}

export interface ComboDualAxisEncoding {
  xField: string;
  leftAxis: DualAxisSide;
  rightAxis: DualAxisSide;
}

export interface VisualizationStyle {
  palette?: string;
  showLegend?: boolean;
  showLabels?: boolean;
  showGrid?: boolean;
  stacked?: boolean;
}

export interface Thresholds {
  warningPct?: number;
  criticalPct?: number;
}

export interface Visualization {
  id: string;
  type: VisualizationType;
  title?: string;
  subtitle?: string;
  encoding: Record<string, unknown>;
  style?: VisualizationStyle;
  polarity?: Polarity;
  thresholds?: Thresholds;
  target?: { source: 'literal' | 'cible_form' | 'computed'; value?: number | string };
}

export interface WorkflowMetadata {
  name: string;
  subProject: string;
  semver: string;
  isCritical?: boolean;
  owner?: string;
  description?: string;
  labels?: Record<string, string>;
}

export interface WorkflowSpec {
  source: Record<string, unknown>;
  pipeline: Array<Record<string, unknown>>;
  kpis?: Array<Record<string, unknown>>;
  visualizations: Visualization[];
  outputs?: Array<Record<string, unknown>>;
}

export interface WorkflowDefinition {
  apiVersion: string;
  kind: string;
  metadata: WorkflowMetadata;
  spec: WorkflowSpec;
}
