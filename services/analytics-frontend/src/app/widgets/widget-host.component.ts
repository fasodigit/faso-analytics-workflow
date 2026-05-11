import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import {
  DemoDataset,
  EchartsRendererComponent,
} from './echarts-renderer.component';
import { PivotTableRendererComponent } from './pivot-table-renderer.component';
import { WorkflowDefinition } from './workflow-definition.model';

@Component({
  selector: 'app-widget-host',
  standalone: true,
  imports: [CommonModule, EchartsRendererComponent, PivotTableRendererComponent],
  template: `
    @if (isPivot) {
      <app-pivot-table-renderer
        [workflowDef]="workflowDef"
        [rows]="dataset.rows"
      ></app-pivot-table-renderer>
    } @else {
      <app-echarts-renderer
        [workflowDef]="workflowDef"
        [dataset]="dataset"
      ></app-echarts-renderer>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }
    `,
  ],
})
export class WidgetHostComponent {
  @Input({ required: true }) workflowDef!: WorkflowDefinition;
  @Input() dataset: DemoDataset = { rows: [] };

  get isPivot(): boolean {
    const t = this.workflowDef?.spec?.visualizations?.[0]?.type;
    return t === 'PIVOT_TABLE';
  }
}
