export type NodeKind =
  | 'source'
  | 'filter'
  | 'aggregate'
  | 'computed'
  | 'join'
  | 'pivot'
  | 'window'
  | 'outlier'
  | 'normalize'
  | 'recode'
  | 'group_by'
  | 'kpi'
  | 'visualization'
  | 'output';

export type NodeCategory = 'Source' | 'Transform' | 'KPI/Viz' | 'Output';

export interface DagNode {
  id: string;
  kind: NodeKind;
  label: string;
  paramsJson: Record<string, unknown>;
  x?: number;
  y?: number;
}

export interface DagEdge {
  id: string;
  source: string;
  target: string;
}

export interface Dag {
  nodes: DagNode[];
  edges: DagEdge[];
}

export interface PaletteItem {
  kind: NodeKind;
  label: string;
  category: NodeCategory;
  description?: string;
}

export const PALETTE_ITEMS: PaletteItem[] = [
  { kind: 'source', label: 'Source', category: 'Source', description: 'Yugabyte / Kobo / Upload …' },
  { kind: 'filter', label: 'Filter', category: 'Transform' },
  { kind: 'join', label: 'Join', category: 'Transform' },
  { kind: 'aggregate', label: 'Aggregate', category: 'Transform' },
  { kind: 'group_by', label: 'Group By', category: 'Transform' },
  { kind: 'pivot', label: 'Pivot', category: 'Transform' },
  { kind: 'computed', label: 'Computed', category: 'Transform' },
  { kind: 'window', label: 'Window', category: 'Transform' },
  { kind: 'outlier', label: 'Outlier', category: 'Transform' },
  { kind: 'normalize', label: 'Normalize', category: 'Transform' },
  { kind: 'recode', label: 'Recode', category: 'Transform' },
  { kind: 'kpi', label: 'KPI', category: 'KPI/Viz' },
  { kind: 'visualization', label: 'Visualization', category: 'KPI/Viz' },
  { kind: 'output', label: 'Output', category: 'Output' },
];

export function isTransformKind(kind: NodeKind): boolean {
  return (
    kind === 'filter' ||
    kind === 'join' ||
    kind === 'aggregate' ||
    kind === 'group_by' ||
    kind === 'pivot' ||
    kind === 'computed' ||
    kind === 'window' ||
    kind === 'outlier' ||
    kind === 'normalize' ||
    kind === 'recode'
  );
}
