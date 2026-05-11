export type VisualizationType =
  | 'BAR_VERTICAL'
  | 'BAR_HORIZONTAL'
  | 'BAR_GROUPED'
  | 'DONUT'
  | 'HALF_DONUT'
  | 'COMBO_DUAL_AXIS'
  | 'LINE_MULTI'
  | 'CHOROPLETH';

export interface BarGroupedEncoding {
  xField: string;
  yField: string;
  series: string;
}

export interface HalfDonutEncoding {
  category: string;
  value: string;
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
}

export interface Visualization {
  id: string;
  type: VisualizationType;
  title?: string;
  subtitle?: string;
  encoding: BarGroupedEncoding | HalfDonutEncoding | ComboDualAxisEncoding | Record<string, unknown>;
  style?: VisualizationStyle;
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
