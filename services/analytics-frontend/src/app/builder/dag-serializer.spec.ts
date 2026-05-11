/**
 * Phase 2 unit tests for dag-serializer.
 *
 * Jasmine-style describe/it API is used (compatible with `ng test`/Karma if
 * configured), but a fallback shim at the top of this module also lets it run
 * directly via `tsx` for a smoke test since no test runner is wired in the
 * analytics-frontend project for Phase 2.
 */

type Suite = { name: string; tests: Array<{ name: string; fn: () => void }> };

const __SUITES: Suite[] = [];
let __CUR: Suite | null = null;

const g = globalThis as Record<string, unknown>;

if (typeof g['describe'] === 'undefined') {
  g['describe'] = (name: string, fn: () => void): void => {
    __CUR = { name, tests: [] };
    __SUITES.push(__CUR);
    fn();
    __CUR = null;
  };
  g['it'] = (name: string, fn: () => void): void => {
    if (__CUR) __CUR.tests.push({ name, fn });
  };
  g['expect'] = (actual: unknown): unknown => ({
    toBe: (e: unknown): void => {
      if (actual !== e) {
        throw new Error(`expected ${String(actual)} to be ${String(e)}`);
      }
    },
    toEqual: (e: unknown): void => {
      if (JSON.stringify(actual) !== JSON.stringify(e)) {
        throw new Error('toEqual failure');
      }
    },
    toBeTruthy: (): void => {
      if (!actual) throw new Error('expected truthy');
    },
    toThrow: (): void => {
      let threw = false;
      try {
        (actual as () => void)();
      } catch {
        threw = true;
      }
      if (!threw) throw new Error('expected to throw');
    },
  });
}

declare const describe: (name: string, fn: () => void) => void;
declare const it: (name: string, fn: () => void) => void;
declare const expect: (v: unknown) => {
  toBe(v: unknown): void;
  toEqual(v: unknown): void;
  toBeTruthy(): void;
  toThrow(): void;
};

import example03 from '../../../../../schemas/examples/03-bar-grouped-region-x-cereale.json';
import { WorkflowDefinition } from '../widgets/workflow-definition.model';
import { Dag } from './dag-node.model';
import { dagToWorkflow, workflowToDag } from './dag-serializer';

const sampleDag: Dag = {
  nodes: [
    {
      id: 'src',
      kind: 'source',
      label: 'Source',
      paramsJson: {
        type: 'yugabyte',
        schema: 'voucher_schema',
        table: 'distribution',
      },
    },
    {
      id: 'flt',
      kind: 'filter',
      label: 'Period',
      paramsJson: { expression: "campagne = '2025-2026'" },
    },
    {
      id: 'agg',
      kind: 'aggregate',
      label: 'Volumes',
      paramsJson: {
        groupBy: ['region'],
        aggregations: [{ alias: 'v', function: 'SUM', field: 'volume_kg' }],
      },
    },
  ],
  edges: [
    { id: 'e1', source: 'src', target: 'flt' },
    { id: 'e2', source: 'flt', target: 'agg' },
  ],
};

describe('dagToWorkflow', () => {
  it('dagToWorkflow_with_1_source_1_filter_1_aggregate_produces_valid_workflow', () => {
    const def = dagToWorkflow(sampleDag, {
      name: 'test-wf',
      subProject: 'VOUCHERS',
      semver: '1.0.0-draft.1',
    });
    expect(def.apiVersion).toBe('analytics.faso/v1');
    expect(def.kind).toBe('AnalyticsWorkflow');
    expect(def.metadata.name).toBe('test-wf');
    expect(def.spec.source['type'] as string).toBe('yugabyte');
    expect(def.spec.pipeline.length).toBe(2);
    expect(def.spec.pipeline[0]['kind']).toBe('filter');
    expect(def.spec.pipeline[1]['kind']).toBe('aggregate');
  });

  it('throws on 0 source', () => {
    expect(() =>
      dagToWorkflow(
        { nodes: [], edges: [] },
        { name: 'x', subProject: 'VOUCHERS', semver: '1.0.0' },
      ),
    ).toThrow();
  });

  it('throws on cycle', () => {
    const cyclic: Dag = {
      nodes: [...sampleDag.nodes],
      edges: [
        { id: 'e1', source: 'src', target: 'flt' },
        { id: 'e2', source: 'flt', target: 'agg' },
        { id: 'e3', source: 'agg', target: 'flt' },
      ],
    };
    expect(() =>
      dagToWorkflow(cyclic, {
        name: 'x',
        subProject: 'VOUCHERS',
        semver: '1.0.0',
      }),
    ).toThrow();
  });

  it('throws on orphan transform', () => {
    const orphan: Dag = {
      nodes: [sampleDag.nodes[0], sampleDag.nodes[1]],
      edges: [],
    };
    expect(() =>
      dagToWorkflow(orphan, {
        name: 'x',
        subProject: 'VOUCHERS',
        semver: '1.0.0',
      }),
    ).toThrow();
  });
});

describe('workflowToDag', () => {
  it('workflowToDag_of_example_03_bar_grouped_returns_4_nodes', () => {
    const def = example03 as unknown as WorkflowDefinition;
    const dag = workflowToDag(def);
    const kinds = new Set(dag.nodes.map((n) => n.kind));
    expect(kinds.has('source')).toBeTruthy();
    expect(kinds.has('filter')).toBeTruthy();
    expect(kinds.has('aggregate')).toBeTruthy();
    expect(kinds.has('computed')).toBeTruthy();
    expect(dag.nodes.length >= 4).toBeTruthy();
    expect(dag.edges.length >= 3).toBeTruthy();
  });

  it('roundtrip dag→workflow→dag preserves source type', () => {
    const def = dagToWorkflow(sampleDag, {
      name: 'rt',
      subProject: 'VOUCHERS',
      semver: '1.0.0',
    });
    const dag2 = workflowToDag(def);
    const src = dag2.nodes.find((n) => n.kind === 'source');
    expect(src).toBeTruthy();
    expect(src?.paramsJson['type']).toBe('yugabyte');
  });
});

// --- Direct-run smoke ---
const __isDirectRun = (() => {
  try {
    const argv = (
      globalThis as { process?: { argv?: string[] } }
    ).process?.argv;
    return Boolean(
      argv && argv.some((a) => a.endsWith('/dag-serializer.spec.ts')),
    );
  } catch {
    return false;
  }
})();

if (__isDirectRun) {
  let pass = 0;
  let fail = 0;
  for (const suite of __SUITES) {
    // eslint-disable-next-line no-console
    console.log(`\n${suite.name}`);
    for (const t of suite.tests) {
      try {
        t.fn();
        pass++;
        // eslint-disable-next-line no-console
        console.log(`  ok  ${t.name}`);
      } catch (e) {
        fail++;
        // eslint-disable-next-line no-console
        console.error(`  FAIL ${t.name}: ${(e as Error).message}`);
      }
    }
  }
  // eslint-disable-next-line no-console
  console.log(`\n${pass} passed, ${fail} failed`);
  if (fail > 0) process.exit(1);
}
