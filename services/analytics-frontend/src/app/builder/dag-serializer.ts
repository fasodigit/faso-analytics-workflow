import {
  Visualization,
  WorkflowDefinition,
} from '../widgets/workflow-definition.model';
import { Dag, DagEdge, DagNode, isTransformKind } from './dag-node.model';

export interface SerializeMetadata {
  name: string;
  subProject: string;
  semver: string;
}

export class DagSerializationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'DagSerializationError';
  }
}

export function validateDag(dag: Dag): void {
  const sources = dag.nodes.filter((n) => n.kind === 'source');
  if (sources.length !== 1) {
    throw new DagSerializationError(
      `DAG must contain exactly 1 source node (found ${sources.length}).`,
    );
  }

  // Check no cycles via topological sort
  const indegrees = new Map<string, number>();
  for (const n of dag.nodes) {
    indegrees.set(n.id, 0);
  }
  for (const e of dag.edges) {
    if (!indegrees.has(e.target) || !indegrees.has(e.source)) {
      throw new DagSerializationError(
        `Edge ${e.id} references unknown node(s).`,
      );
    }
    indegrees.set(e.target, (indegrees.get(e.target) ?? 0) + 1);
  }
  const queue: string[] = [];
  indegrees.forEach((v, k) => {
    if (v === 0) queue.push(k);
  });
  const adjacency = new Map<string, string[]>();
  for (const e of dag.edges) {
    const arr = adjacency.get(e.source) ?? [];
    arr.push(e.target);
    adjacency.set(e.source, arr);
  }
  let visited = 0;
  const remain = new Map(indegrees);
  while (queue.length > 0) {
    const cur = queue.shift() as string;
    visited++;
    for (const next of adjacency.get(cur) ?? []) {
      const r = (remain.get(next) ?? 0) - 1;
      remain.set(next, r);
      if (r === 0) queue.push(next);
    }
  }
  if (visited !== dag.nodes.length) {
    throw new DagSerializationError('DAG contains a cycle.');
  }

  // No orphan transforms (every transform/kpi/visualization/output must have at least one incoming edge)
  const incoming = new Map<string, number>();
  for (const n of dag.nodes) incoming.set(n.id, 0);
  for (const e of dag.edges) {
    incoming.set(e.target, (incoming.get(e.target) ?? 0) + 1);
  }
  for (const n of dag.nodes) {
    if (n.kind === 'source') continue;
    if ((incoming.get(n.id) ?? 0) === 0) {
      throw new DagSerializationError(
        `Node "${n.label}" (${n.id}) has no upstream connection.`,
      );
    }
  }
}

/**
 * Topological sort starting from the source node, restricted to transform nodes.
 * Non-transform nodes (kpi/viz/output) are excluded from the pipeline.
 */
function topoSortTransforms(dag: Dag): DagNode[] {
  const indeg = new Map<string, number>();
  const adj = new Map<string, string[]>();
  for (const n of dag.nodes) {
    indeg.set(n.id, 0);
    adj.set(n.id, []);
  }
  for (const e of dag.edges) {
    indeg.set(e.target, (indeg.get(e.target) ?? 0) + 1);
    const list = adj.get(e.source) ?? [];
    list.push(e.target);
    adj.set(e.source, list);
  }
  const result: DagNode[] = [];
  const queue: string[] = [];
  indeg.forEach((v, k) => {
    if (v === 0) queue.push(k);
  });
  const byId = new Map(dag.nodes.map((n) => [n.id, n]));
  while (queue.length > 0) {
    const id = queue.shift() as string;
    const node = byId.get(id);
    if (node && isTransformKind(node.kind)) {
      result.push(node);
    }
    for (const next of adj.get(id) ?? []) {
      const v = (indeg.get(next) ?? 0) - 1;
      indeg.set(next, v);
      if (v === 0) queue.push(next);
    }
  }
  return result;
}

function paramsToTransform(node: DagNode): Record<string, unknown> {
  return {
    id: node.id,
    kind: node.kind,
    label: node.label,
    ...node.paramsJson,
  };
}

export function dagToWorkflow(
  dag: Dag,
  metadata: SerializeMetadata,
): WorkflowDefinition {
  validateDag(dag);
  const source = dag.nodes.find((n) => n.kind === 'source');
  if (!source) {
    throw new DagSerializationError('Source node missing after validation.');
  }
  const pipeline = topoSortTransforms(dag).map(paramsToTransform);
  const kpis = dag.nodes
    .filter((n) => n.kind === 'kpi')
    .map((n) => ({ id: n.id, label: n.label, ...n.paramsJson }));
  const visualizations = dag.nodes
    .filter((n) => n.kind === 'visualization')
    .map(
      (n) =>
        ({
          id: n.id,
          ...n.paramsJson,
        }) as Visualization,
    );
  const outputs = dag.nodes
    .filter((n) => n.kind === 'output')
    .map((n) => ({ ...n.paramsJson }));

  return {
    apiVersion: 'analytics.faso/v1',
    kind: 'AnalyticsWorkflow',
    metadata: {
      name: metadata.name,
      subProject: metadata.subProject,
      semver: metadata.semver,
    },
    spec: {
      source: { ...source.paramsJson },
      pipeline,
      kpis,
      visualizations,
      outputs,
    },
  };
}

function stripStandardFields(
  obj: Record<string, unknown>,
  ...skip: string[]
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const k of Object.keys(obj)) {
    if (skip.includes(k)) continue;
    out[k] = obj[k];
  }
  return out;
}

export function workflowToDag(def: WorkflowDefinition): Dag {
  const nodes: DagNode[] = [];
  const edges: DagEdge[] = [];

  const sourceId = 'src_main';
  nodes.push({
    id: sourceId,
    kind: 'source',
    label: `Source (${String(def.spec.source['type'] ?? 'unknown')})`,
    paramsJson: { ...def.spec.source },
    x: 80,
    y: 80,
  });

  let lastId = sourceId;
  let yPos = 80;
  const xStep = 220;
  let xPos = 80 + xStep;
  for (const step of def.spec.pipeline) {
    const id = String(step['id'] ?? `step_${nodes.length}`);
    const kind = String(step['kind'] ?? 'filter') as DagNode['kind'];
    const label = String(step['label'] ?? id);
    const params = stripStandardFields(step, 'id', 'kind', 'label');
    nodes.push({ id, kind, label, paramsJson: params, x: xPos, y: yPos });
    edges.push({ id: `e_${lastId}_${id}`, source: lastId, target: id });
    lastId = id;
    xPos += xStep;
  }

  const fanoutSource = lastId;
  let kpiY = yPos + 180;
  for (const k of def.spec.kpis ?? []) {
    const id = String(k['id'] ?? `kpi_${nodes.length}`);
    const label = String(k['label'] ?? id);
    const params = stripStandardFields(k, 'id', 'label');
    nodes.push({
      id,
      kind: 'kpi',
      label,
      paramsJson: params,
      x: xPos,
      y: kpiY,
    });
    edges.push({
      id: `e_${fanoutSource}_${id}`,
      source: fanoutSource,
      target: id,
    });
    kpiY += 100;
  }

  let vizY = yPos;
  for (const v of def.spec.visualizations ?? []) {
    const id = String(v.id ?? `viz_${nodes.length}`);
    const label = v.title ?? id;
    const params = stripStandardFields(
      v as unknown as Record<string, unknown>,
      'id',
    );
    nodes.push({
      id,
      kind: 'visualization',
      label,
      paramsJson: params,
      x: xPos + xStep,
      y: vizY,
    });
    edges.push({
      id: `e_${fanoutSource}_${id}`,
      source: fanoutSource,
      target: id,
    });
    vizY += 120;
  }

  let outY = yPos + 300;
  for (const o of def.spec.outputs ?? []) {
    const id = `output_${nodes.length}`;
    const label = String(o['kind'] ?? 'output');
    nodes.push({
      id,
      kind: 'output',
      label,
      paramsJson: { ...o },
      x: xPos + xStep * 2,
      y: outY,
    });
    edges.push({
      id: `e_${fanoutSource}_${id}`,
      source: fanoutSource,
      target: id,
    });
    outY += 90;
  }

  return { nodes, edges };
}
