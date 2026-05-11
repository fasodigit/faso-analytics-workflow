import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { WorkflowDefinition } from '../widgets/workflow-definition.model';
import { CanvasComponent } from './canvas.component';
import { Dag, DagNode } from './dag-node.model';
import {
  DagSerializationError,
  dagToWorkflow,
  workflowToDag,
} from './dag-serializer';
import { PaletteComponent } from './palette.component';
import { ParamsSidebarComponent } from './params-sidebar.component';
import { ToolbarComponent, ToolbarMetadata } from './toolbar.component';

import example01 from '../../../../../schemas/examples/01-bar-vertical-pagsi-region.json';
import example02 from '../../../../../schemas/examples/02-bar-horizontal-top10-communes.json';
import example03 from '../../../../../schemas/examples/03-bar-grouped-region-x-cereale.json';
import example04 from '../../../../../schemas/examples/04-donut-distribution-statuts.json';
import example05 from '../../../../../schemas/examples/05-half-donut-resurep-especes.json';
import example06 from '../../../../../schemas/examples/06-combo-dual-axis-sonagess.json';
import example07 from '../../../../../schemas/examples/07-line-multi-evolution.json';
import example08 from '../../../../../schemas/examples/08-choropleth-bf-coverage.json';

const EXAMPLES: Record<string, WorkflowDefinition> = {
  '01-bar-vertical': example01 as unknown as WorkflowDefinition,
  '02-bar-horizontal': example02 as unknown as WorkflowDefinition,
  '03-bar-grouped': example03 as unknown as WorkflowDefinition,
  '04-donut': example04 as unknown as WorkflowDefinition,
  '05-half-donut': example05 as unknown as WorkflowDefinition,
  '06-combo-dual-axis': example06 as unknown as WorkflowDefinition,
  '07-line-multi': example07 as unknown as WorkflowDefinition,
  '08-choropleth': example08 as unknown as WorkflowDefinition,
};

@Component({
  selector: 'app-builder-page',
  standalone: true,
  imports: [
    CommonModule,
    ToolbarComponent,
    PaletteComponent,
    CanvasComponent,
    ParamsSidebarComponent,
  ],
  templateUrl: './builder-page.component.html',
  styleUrls: ['./builder.scss'],
})
export class BuilderPageComponent {
  readonly dag = signal<Dag>({ nodes: [], edges: [] });
  readonly selectedNodeId = signal<string | null>(null);
  readonly linkMode = signal<boolean>(false);
  readonly metadata = signal<ToolbarMetadata>({
    name: 'mon-workflow',
    subProject: 'VOUCHERS',
    semver: '1.0.0-draft.1',
  });

  get selectedNode(): DagNode | null {
    const id = this.selectedNodeId();
    if (!id) return null;
    return this.dag().nodes.find((n) => n.id === id) ?? null;
  }

  onDagChange(newDag: Dag): void {
    this.dag.set(newDag);
  }

  onSelect(id: string | null): void {
    this.selectedNodeId.set(id);
  }

  onLinkModeChange(v: boolean): void {
    this.linkMode.set(v);
  }

  onParamsChange(params: Record<string, unknown>): void {
    const id = this.selectedNodeId();
    if (!id) return;
    this.dag.update((d) => ({
      ...d,
      nodes: d.nodes.map((n) =>
        n.id === id ? { ...n, paramsJson: params } : n,
      ),
    }));
  }

  onLabelChange(label: string): void {
    const id = this.selectedNodeId();
    if (!id) return;
    this.dag.update((d) => ({
      ...d,
      nodes: d.nodes.map((n) => (n.id === id ? { ...n, label } : n)),
    }));
  }

  onMetadataChange(meta: ToolbarMetadata): void {
    this.metadata.set(meta);
  }

  onToolbarAction(evt: { type: string; payload?: string }): void {
    switch (evt.type) {
      case 'new':
        if (confirm('Effacer le DAG courant ?')) {
          this.dag.set({ nodes: [], edges: [] });
          this.selectedNodeId.set(null);
        }
        return;
      case 'load-example':
        if (!evt.payload) return;
        this.loadExample(evt.payload);
        return;
      case 'validate':
        this.validate();
        return;
      case 'export':
        this.exportJson();
        return;
      case 'simulate':
        this.simulate();
        return;
    }
  }

  private loadExample(key: string): void {
    const def = EXAMPLES[key];
    if (!def) return;
    this.dag.set(workflowToDag(def));
    this.metadata.set({
      name: def.metadata.name,
      subProject: def.metadata.subProject as ToolbarMetadata['subProject'],
      semver: def.metadata.semver,
    });
    this.selectedNodeId.set(null);
  }

  private validate(): void {
    try {
      dagToWorkflow(this.dag(), this.metadata());
      alert('DAG valide.');
    } catch (e) {
      const msg =
        e instanceof DagSerializationError
          ? e.message
          : `Erreur inconnue: ${String(e)}`;
      alert(`DAG invalide : ${msg}`);
    }
  }

  private exportJson(): void {
    try {
      const def = dagToWorkflow(this.dag(), this.metadata());
      const blob = new Blob([JSON.stringify(def, null, 2)], {
        type: 'application/json',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${this.metadata().name}-${this.metadata().semver}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      alert(`Export impossible : ${(e as Error).message}`);
    }
  }

  private simulate(): void {
    try {
      const def = dagToWorkflow(this.dag(), this.metadata());
      // Phase 3 wiring → POST /v1/workflows/.../simulate
      // Phase 2: just log
      // eslint-disable-next-line no-console
      console.log('[simulate] would POST workflow definition', def);
      alert('Simulation déclenchée (cf. console). Wiring Phase 3.');
    } catch (e) {
      alert(`Simulation impossible : ${(e as Error).message}`);
    }
  }
}
