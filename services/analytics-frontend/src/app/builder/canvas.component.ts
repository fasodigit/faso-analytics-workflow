import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  HostListener,
  Input,
  Output,
} from '@angular/core';
import {
  CdkDragDrop,
  CdkDropList,
  DragDropModule,
} from '@angular/cdk/drag-drop';
import { NgxGraphModule, Edge, Node } from '@swimlane/ngx-graph';
import { Dag, DagEdge, DagNode, PaletteItem } from './dag-node.model';
import { dagToWorkflow } from './dag-serializer';

@Component({
  selector: 'fd-canvas',
  standalone: true,
  imports: [CommonModule, DragDropModule, NgxGraphModule],
  templateUrl: './canvas.component.html',
})
export class CanvasComponent {
  @Input() dag: Dag = { nodes: [], edges: [] };
  @Input() selectedNodeId: string | null = null;
  @Input() linkMode = false;

  @Output() dagChange = new EventEmitter<Dag>();
  @Output() nodeSelected = new EventEmitter<string | null>();
  @Output() linkModeChange = new EventEmitter<boolean>();

  private linkSource: string | null = null;

  get graphNodes(): Node[] {
    return this.dag.nodes.map((n) => ({
      id: n.id,
      label: n.label,
      data: { kind: n.kind, selected: n.id === this.selectedNodeId },
      position: n.x !== undefined && n.y !== undefined ? { x: n.x, y: n.y } : undefined,
    }));
  }

  get graphLinks(): Edge[] {
    return this.dag.edges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
    }));
  }

  onDropFromPalette(event: CdkDragDrop<DagNode[]>): void {
    const item = event.item.data as PaletteItem;
    if (!item) return;
    const baseX = 80 + this.dag.nodes.length * 60;
    const baseY = 80 + (this.dag.nodes.length % 4) * 100;
    const newNode: DagNode = {
      id: this.generateNodeId(item.kind),
      kind: item.kind,
      label: item.label,
      paramsJson: {},
      x: baseX,
      y: baseY,
    };
    this.dagChange.emit({
      nodes: [...this.dag.nodes, newNode],
      edges: this.dag.edges,
    });
    this.nodeSelected.emit(newNode.id);
  }

  private generateNodeId(kind: string): string {
    const existing = new Set(this.dag.nodes.map((n) => n.id));
    let i = 1;
    while (existing.has(`${kind}_${i}`)) i++;
    return `${kind}_${i}`;
  }

  onNodeClick(node: Node): void {
    if (this.linkMode) {
      if (this.linkSource === null) {
        this.linkSource = node.id;
      } else if (this.linkSource !== node.id) {
        const edgeId = `e_${this.linkSource}_${node.id}_${Date.now()}`;
        // Prevent duplicates
        const exists = this.dag.edges.some(
          (e) => e.source === this.linkSource && e.target === node.id,
        );
        if (!exists) {
          const newEdge: DagEdge = {
            id: edgeId,
            source: this.linkSource,
            target: node.id,
          };
          // Validate the resulting dag stays acyclic
          const candidate: Dag = {
            nodes: this.dag.nodes,
            edges: [...this.dag.edges, newEdge],
          };
          if (this.wouldRemainAcyclic(candidate)) {
            this.dagChange.emit(candidate);
          } else {
            // eslint-disable-next-line no-alert
            alert('Refusé : un cycle serait créé.');
          }
        }
        this.linkSource = null;
        this.linkMode = false;
        this.linkModeChange.emit(false);
      }
      return;
    }
    this.nodeSelected.emit(node.id);
  }

  onCanvasClick(): void {
    if (!this.linkMode) {
      this.nodeSelected.emit(null);
    }
  }

  toggleLinkMode(): void {
    this.linkMode = !this.linkMode;
    this.linkSource = null;
    this.linkModeChange.emit(this.linkMode);
  }

  onLinkContextMenu(event: MouseEvent, edge: Edge): void {
    event.preventDefault();
    // eslint-disable-next-line no-alert
    if (confirm(`Supprimer le lien ${edge.id} ?`)) {
      this.dagChange.emit({
        nodes: this.dag.nodes,
        edges: this.dag.edges.filter((e) => e.id !== edge.id),
      });
    }
  }

  @HostListener('window:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (event.key !== 'Delete' && event.key !== 'Backspace') return;
    if (!this.selectedNodeId) return;
    const target = event.target as HTMLElement | null;
    if (
      target &&
      (target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable)
    ) {
      return;
    }
    event.preventDefault();
    this.deleteSelectedNode();
  }

  private deleteSelectedNode(): void {
    const id = this.selectedNodeId;
    if (!id) return;
    this.dagChange.emit({
      nodes: this.dag.nodes.filter((n) => n.id !== id),
      edges: this.dag.edges.filter((e) => e.source !== id && e.target !== id),
    });
    this.nodeSelected.emit(null);
  }

  validateNow(): void {
    try {
      dagToWorkflow(this.dag, {
        name: 'preview',
        subProject: 'VOUCHERS',
        semver: '1.0.0-draft.1',
      });
      // eslint-disable-next-line no-alert
      alert('DAG valide.');
    } catch (e) {
      // eslint-disable-next-line no-alert
      alert(`DAG invalide : ${(e as Error).message}`);
    }
  }

  private wouldRemainAcyclic(dag: Dag): boolean {
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
    const queue: string[] = [];
    indeg.forEach((v, k) => {
      if (v === 0) queue.push(k);
    });
    let visited = 0;
    while (queue.length > 0) {
      const c = queue.shift() as string;
      visited++;
      for (const next of adj.get(c) ?? []) {
        const r = (indeg.get(next) ?? 0) - 1;
        indeg.set(next, r);
        if (r === 0) queue.push(next);
      }
    }
    return visited === dag.nodes.length;
  }
}
